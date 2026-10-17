package org.arcadia.arc_quest.trade.runtime;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;

/** Atomic main-inventory delivery. Full inventories reject rather than drop or consume rewards. */
public final class InventoryDelivery {
    private InventoryDelivery() {}

    public static void deliver(ServerPlayer player, ItemStack requested) {
        deliver(player.getInventory(), requested);
    }

    public static void deliver(Inventory inventory, ItemStack requested) {
        if (requested.isEmpty()) throw new IllegalArgumentException("Reward must not be empty");
        var remainder = requested.copy();
        var plan = new ArrayList<ItemStack>(inventory.items.size());
        for (var stack : inventory.items) plan.add(stack.copy());
        for (var stack : plan) {
            if (remainder.isEmpty()) break;
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, remainder)) continue;
            int amount = Math.min(remainder.getCount(), Math.max(0,
                    Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize()) - stack.getCount()));
            stack.grow(amount);
            remainder.shrink(amount);
        }
        for (int slot = 0; slot < plan.size() && !remainder.isEmpty(); slot++) {
            if (!plan.get(slot).isEmpty()) continue;
            var inserted = remainder.copy();
            int amount = Math.min(remainder.getCount(), Math.min(remainder.getMaxStackSize(), inventory.getMaxStackSize()));
            inserted.setCount(amount);
            plan.set(slot, inserted);
            remainder.shrink(amount);
        }
        if (!remainder.isEmpty()) throw new IllegalStateException("Not enough inventory space for the reward");
        for (int slot = 0; slot < plan.size(); slot++) inventory.setItem(slot, plan.get(slot));
        inventory.setChanged();
    }
}
