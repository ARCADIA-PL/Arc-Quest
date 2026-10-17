package org.arcadia.arc_quest.trade.api;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** The same matching rules and dynamic count as a real payment, without performing one. */
public record ItemCostPreview(List<ItemStack> alternatives, Component label, int required, int owned) {
    public ItemCostPreview { alternatives = alternatives.stream().map(ItemStack::copy).toList(); }
    public int missing() { return Math.max(0, required - owned); }
}
