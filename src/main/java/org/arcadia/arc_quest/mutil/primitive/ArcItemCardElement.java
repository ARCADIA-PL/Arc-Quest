package org.arcadia.arc_quest.mutil.primitive;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.mutil.core.ArcGuiColor;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;

public class ArcItemCardElement extends ArcGuiElement {
    private ItemStack stack = ItemStack.EMPTY;
    private int themeColor = 0xFF4FC3F7;
    private float hoverScale = 1f;
    private boolean selected;

    public ArcItemCardElement(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public ArcItemCardElement setStack(ItemStack stack) {
        this.stack = stack == null ? ItemStack.EMPTY : stack;
        return this;
    }

    public ArcItemCardElement setThemeColor(int themeColor) {
        this.themeColor = themeColor;
        return this;
    }

    public ArcItemCardElement setSelected(boolean selected) {
        this.selected = selected;
        return this;
    }

    @Override
    protected void update(ArcGuiContext context, int refX, int refY) {
        float target = focused || selected ? 1.07f : 1f;
        hoverScale += (target - hoverScale) * Math.min(1f, context.deltaTime() * 12f);
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!visible) return;
        int drawX = refX + x;
        int drawY = refY + y;
        graphics.pose().pushPose();
        graphics.pose().translate(drawX + width / 2f, drawY + height / 2f, 0);
        graphics.pose().scale(hoverScale, hoverScale, 1f);
        graphics.pose().translate(-(drawX + width / 2f), -(drawY + height / 2f), 0);
        ArcDrawUtil.drawAccentPanel(graphics, drawX, drawY, width, height, ArcGuiColor.withOpacity(0xAA111111, inheritedOpacity * opacity), ArcGuiColor.withOpacity(themeColor, inheritedOpacity * opacity), 2);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, drawX + (width - 16) / 2, drawY + (height - 16) / 2);
        }
        graphics.pose().popPose();
    }
}
