package org.arcadia.arc_quest.client.hud.quest.history;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.quest.api.VisualAsset;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestHistoryCardVisibilityTest {
    @BeforeAll static void bootstrap() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void transformedItemCoversKeepTheOriginalUnboundedRenderer() {
        var item = new ItemStack(Items.APPLE);
        assertTrue(QuestHistoryCardVisibility.hasBoundedCover(null));
        assertTrue(QuestHistoryCardVisibility.hasBoundedCover(VisualAsset.of(item)));
        assertTrue(QuestHistoryCardVisibility.hasBoundedCover(VisualAsset.of(ResourceLocation.parse("test:cover"), 5f)));
        assertFalse(QuestHistoryCardVisibility.hasBoundedCover(VisualAsset.of(item, 5f)));
        assertFalse(QuestHistoryCardVisibility.hasBoundedCover(
                new VisualAsset(null, 1f, 100f, 0f, 0xFFFFFFFF, true, item)));
        assertFalse(QuestHistoryCardVisibility.hasBoundedCover(
                new VisualAsset(null, 1f, 0f, -100f, 0xFFFFFFFF, true, item)));
    }

    @Test void edgeLabelsShadowsAndMaximumHoverScaleAreNotClipped() {
        assertTrue(QuestHistoryCardVisibility.intersects(-64, 40, 0, 0, 1, 200, 100));
        assertTrue(QuestHistoryCardVisibility.intersects(264, 40, 0, 0, 1, 200, 100));
        assertTrue(QuestHistoryCardVisibility.intersects(100, -40, 0, 0, 1, 200, 100));
        assertTrue(QuestHistoryCardVisibility.intersects(100, 140, 0, 0, 1, 200, 100));
        assertFalse(QuestHistoryCardVisibility.intersects(-80, 40, 0, 0, 1, 200, 100));
        assertFalse(QuestHistoryCardVisibility.intersects(100, -60, 0, 0, 1, 200, 100));
    }

    @Test void viewportPanningRemainsOutsideGraphZoomTransform() {
        assertTrue(QuestHistoryCardVisibility.intersects(300, 100, -500, -170, 2, 200, 100));
        assertFalse(QuestHistoryCardVisibility.intersects(700, 100, -500, -170, 2, 200, 100));
        assertTrue(QuestHistoryCardVisibility.intersects(400, 200, 0, 0, 0.25f, 200, 100));
    }

    @Test void largeGraphsOnlySubmitCardsNearTheViewport() {
        int submitted = 0;
        for (int row = 0; row < 20; row++) {
            for (int col = 0; col < 20; col++) {
                if (QuestHistoryCardVisibility.intersects(col * 150, row * 100, -800, -600, 1, 300, 180)) submitted++;
            }
        }
        assertTrue(submitted > 0 && submitted <= 12, "The offscreen majority must not submit cover draws");
    }

    @Test void invalidZoomUsesTheExistingRendererInsteadOfHidingCards() {
        assertTrue(QuestHistoryCardVisibility.intersects(1000, 1000, 0, 0, 0, 200, 100));
        assertTrue(QuestHistoryCardVisibility.intersects(1000, 1000, 0, 0, Float.NaN, 200, 100));
    }
}
