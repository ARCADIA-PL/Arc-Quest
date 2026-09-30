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
        if (isItem()) {
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(x, y, 0);
                graphics.pose().scale(size / 16f, size / 16f, 1);
                graphics.renderFakeItem(stack, 0, 0);
            } finally { graphics.pose().popPose(); }
        } else if (visual != null && visual.available()) visual.render(graphics, x, y, size);
    }
}
