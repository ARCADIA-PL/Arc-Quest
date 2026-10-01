package org.arcadia.arc_quest.client.hud.quest.icon;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ObjectiveIconItemIdentityTest {
    @BeforeAll
    static void initialize() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test
    void distinctDisplayComponentsAreSeparateCycleCandidates() {
        var plain = new ItemStack(Items.IRON_SWORD);
        var named = plain.copy();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Quest sword"));
        var unbreakable = plain.copy();
        unbreakable.set(DataComponents.UNBREAKABLE, new Unbreakable(true));

        assertNotEquals(ObjectiveIconSession.itemKey(plain), ObjectiveIconSession.itemKey(named));
        assertNotEquals(ObjectiveIconSession.itemKey(plain), ObjectiveIconSession.itemKey(unbreakable));
        assertNotEquals(ObjectiveIconSession.itemKey(named), ObjectiveIconSession.itemKey(unbreakable));
    }

    @Test
    void changingQuantityDoesNotRestartTheSameCandidate() {
        var single = new ItemStack(Items.APPLE);
        var many = single.copyWithCount(32);
        assertEquals(ObjectiveIconSession.itemKey(single), ObjectiveIconSession.itemKey(many));
    }
}
