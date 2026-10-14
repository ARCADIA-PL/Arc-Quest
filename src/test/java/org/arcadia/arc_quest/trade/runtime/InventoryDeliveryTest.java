package org.arcadia.arc_quest.trade.runtime;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventoryDeliveryTest {
    @BeforeAll static void bootstrap() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void partialStackCapacityMustNotCausePartialDelivery() {
        var inventory = full();
        inventory.setItem(0, new ItemStack(Items.DIAMOND, 63));
        var requested = new ItemStack(Items.DIAMOND, 2);
        var before = inventory.save(new net.minecraft.nbt.ListTag());
        assertThrows(IllegalStateException.class, () -> InventoryDelivery.deliver(inventory, requested));
        assertEquals(before, inventory.save(new net.minecraft.nbt.ListTag()));
        assertEquals(2, requested.getCount());
    }

    @Test void oversizedDeliverySplitsStacksAndRetainsNbtAndCallerStack() {
        var inventory = full();
        inventory.setItem(0, ItemStack.EMPTY);
        inventory.setItem(1, ItemStack.EMPTY);
        var requested = new ItemStack(Items.DIAMOND, 70);
        requested.getOrCreateTag().putString("origin", "custom_reward");
        InventoryDelivery.deliver(inventory, requested);
        assertEquals(64, inventory.getItem(0).getCount());
        assertEquals(6, inventory.getItem(1).getCount());
        assertEquals("custom_reward", inventory.getItem(1).getTag().getString("origin"));
        assertEquals(70, requested.getCount());
        requested.getTag().putString("origin", "changed");
        assertEquals("custom_reward", inventory.getItem(0).getTag().getString("origin"));
    }

    @Test void differentTagsCannotMergeOrUseArmorSlots() {
        var inventory = full();
        inventory.setItem(0, new ItemStack(Items.DIAMOND, 1));
        var reward = new ItemStack(Items.DIAMOND, 1);
        reward.getOrCreateTag().putBoolean("special", true);
        assertThrows(IllegalStateException.class, () -> InventoryDelivery.deliver(inventory, reward));
        assertEquals(1, inventory.getItem(0).getCount());
    }

    private static Inventory full() {
        var inventory = new Inventory(null);
        for (int slot = 0; slot < inventory.items.size(); slot++)
            inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        return inventory;
    }
}
