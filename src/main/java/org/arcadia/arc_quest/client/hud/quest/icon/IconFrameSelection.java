package org.arcadia.arc_quest.client.hud.quest.icon;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** One immutable choice is shared by drawing, the custom tooltip and JEI input. */
public record IconFrameSelection(String objectiveKey, String candidateKey, ItemStack stack,
                                 ObjectiveIconVisual visual, int index, int candidateCount, long generation) {
    public IconFrameSelection { stack = stack == null ? ItemStack.EMPTY : stack.copy(); }
    @Override public ItemStack stack() { return stack.copy(); }
    public boolean isItem() { return !stack.isEmpty(); }
    public boolean available() { return isItem() || visual != null && visual.available(); }
    public void render(GuiGraphics graphics, int x, int y, int size) {
        render(graphics, x, y, size, 1);
    }
    public void render(GuiGraphics graphics, int x, int y, int size, float alpha) {
        if (isItem()) {
            ObjectiveIconAlpha.renderItem(graphics, stack, x, y, size, alpha);
        } else if (visual != null && visual.available()) visual.render(graphics, x, y, size, alpha);
    }
}
