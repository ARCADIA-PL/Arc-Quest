package org.arcadia.arc_quest.client.hud.quest.icon;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class IconFrameSelectionTest {
    @BeforeAll static void initialize() { MinecraftRegistryTestBootstrap.initialize(); }
    @Test void tooltipOrQueryCannotMutateTheRenderedCandidate() {
        ItemStack source = new ItemStack(Items.OAK_LOG);
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, source, tag -> tag.putString("name", "source"));
        var frame = new IconFrameSelection("objective", "oak", source, null, 0, 2, 9);
        source.setCount(12);
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, source, tag -> tag.putString("name", "mutated"));
        frame.stack().setCount(5);
        assertEquals(1, frame.stack().getCount());
        assertEquals("source", frame.stack().getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getString("name"));
        assertEquals(9, frame.generation());
        assertEquals(2, frame.candidateCount());
    }
    @Test void emptyCandidatesHaveNoPlaceholder() {
        assertFalse(ResolvedObjectiveIcon.items(List.of(ItemStack.EMPTY)).available());
        assertFalse(ResolvedObjectiveIcon.none().available());
        var source = new ItemStack(Items.BIRCH_LOG);
        var resolved = ResolvedObjectiveIcon.items(List.of(source));
        source.setCount(0);
        resolved.items().get(0).setCount(0);
        assertTrue(resolved.available());
        assertEquals(1, resolved.items().get(0).getCount());
    }
}
