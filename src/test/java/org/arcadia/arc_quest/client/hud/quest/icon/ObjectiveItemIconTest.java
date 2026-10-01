package org.arcadia.arc_quest.client.hud.quest.icon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.quest.api.ObjectiveItemResolver;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ObjectiveItemIconTest {
    @BeforeAll static void initialize() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void explicitInventoryItemOverridesVisualWithoutChangingTargetCandidates() {
        var context = context(ObjectiveBuilder.collect(Items.DIAMOND, 8).iconTexture(Items.EMERALD));
        var session = new ObjectiveIconSession();
        assertTrue(session.resolve(context).available());
        assertEquals(1, session.targetCount(context));
        assertTrue(session.singleTarget(context).is(Items.DIAMOND));
        var frame = session.select(context, false, true);
        assertTrue(frame.isItem());
        assertTrue(frame.stack().is(Items.EMERALD));
        assertEquals(1, frame.candidateCount());
        assertFalse(ObjectiveItemResolver.matches(context.objective(), frame.stack()));
    }

    @Test void explicitItemWorksForNonItemObjectivesAndReturnsIsolatedStacks() {
        var context = context(ObjectiveBuilder.custom(ResourceLocation.parse("example:inspect"), 1).iconItem(Items.CHEST));
        var resolved = ObjectiveIconRegistry.resolve(context);
        assertTrue(resolved.available());
        assertTrue(resolved.items().get(0).is(Items.CHEST));
        resolved.items().get(0).setCount(30);
        assertEquals(1, resolved.items().get(0).getCount());
        assertTrue(ObjectiveItemResolver.candidates(context.objective()).isEmpty());
    }

    @Test void missingEmptyAndHiddenItemIconsDoNotFallBackToTheTarget() {
        assertFalse(ObjectiveIconRegistry.resolve(context(ObjectiveBuilder.collect(Items.DIAMOND, 1)
                .iconItem("missing_addon:no_item"))).available());
        assertFalse(ObjectiveIconRegistry.resolve(context(ObjectiveBuilder.collect(Items.DIAMOND, 1)
                .iconItem(Items.AIR))).available());
        assertFalse(ObjectiveIconRegistry.resolve(context(ObjectiveBuilder.collect(Items.DIAMOND, 1)
                .iconItem(Items.EMERALD).hidden())).available());
    }

    private static ObjectiveIconContext context(ObjectiveBuilder builder) {
        var objective = builder.build();
        return new ObjectiveIconContext("example:quest", "phase", 0, objective, 0, objective.getRequiredCount(), 0);
    }
}
