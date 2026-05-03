package org.arcadia.arc_quest.mutil.primitive;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;

public class ArcResultCardElement extends ArcGuiElement {
    private int themeColor = 0xFF4FC3F7;
    private ItemStack itemStack = ItemStack.EMPTY;
    private final ArcGuiItem item;
    private final ArcGuiText title;

    public ArcResultCardElement(int x, int y, int width, int height) {
        super(x, y, width, height);
        item = new ArcGuiItem(width / 2 - 8, height / 2 - 8);
        title = new ArcGuiText(8, height - 18, width - 16, "");
        addChild(item);
        addChild(title);
    }

    public ArcResultCardElement setThemeColor(int themeColor) {
        this.themeColor = themeColor;
        return this;
    }

    public ArcResultCardElement setItem(ItemStack stack) {
        this.itemStack = stack == null ? ItemStack.EMPTY : stack;
        item.setItem(this.itemStack);
        title.setText(this.itemStack.isEmpty() ? "" : this.itemStack.getHoverName().getString());
        return this;
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!visible) return;
        int drawX = refX + x;
        int drawY = refY + y;
        ArcDrawUtil.drawAccentPanel(graphics, drawX, drawY, width, height, 0xAA05050A, themeColor, 3);
        super.draw(graphics, context, refX, refY, inheritedOpacity);
    }
}
