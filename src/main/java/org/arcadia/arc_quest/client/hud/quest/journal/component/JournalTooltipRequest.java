package org.arcadia.arc_quest.client.hud.quest.journal.component;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.ArrayList;

/** Semantic contents for the journal's own tooltip skin and animation. */
public record JournalTooltipRequest(String identity, ItemStack stack, List<Component> extraLines, Anchor anchor) {
    public JournalTooltipRequest(String identity, ItemStack stack, List<Component> extraLines) {
        this(identity, stack, extraLines, null);
    }
    public JournalTooltipRequest {
        stack = stack == null ? ItemStack.EMPTY : stack.copy();
        extraLines = List.copyOf(extraLines);
    }
    @Override public ItemStack stack() { return stack.copy(); }
    public boolean compact() { return anchor != null; }
    /** Icon inspection shows identity, not inventory lore, attributes or debug/NBT details. */
    public List<Component> compactLines() {
        List<Component> lines = new ArrayList<>();
        if (!stack.isEmpty()) lines.add(stack.getHoverName().copy().withStyle(stack.getRarity().color));
        for (int i = 0; i < extraLines.size(); i++) {
            Component line = extraLines.get(i).copy();
            if (lines.isEmpty()) line = line.copy().withStyle(net.minecraft.ChatFormatting.WHITE);
            lines.add(line);
        }
        return List.copyOf(lines);
    }
    public record Anchor(int x, int y, int width, int height) {}
}
