package org.arcadia.arc_quest.client.hud.quest.journal.component;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Semantic contents for the journal's own tooltip skin and animation. */
public record JournalTooltipRequest(String identity, ItemStack stack, List<Component> extraLines) {
    public JournalTooltipRequest {
        stack = stack == null ? ItemStack.EMPTY : stack.copy();
        extraLines = List.copyOf(extraLines);
    }
    @Override public ItemStack stack() { return stack.copy(); }
}
