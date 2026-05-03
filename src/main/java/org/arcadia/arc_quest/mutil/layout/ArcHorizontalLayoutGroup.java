package org.arcadia.arc_quest.mutil.layout;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;

public class ArcHorizontalLayoutGroup extends ArcGuiElement {
    protected boolean layoutDirty = false;
    protected final int spacing;

    public ArcHorizontalLayoutGroup(int x, int y, int height, int spacing) {
        super(x, y, 0, height);
        this.spacing = spacing;
    }

    @Override
    public void addChild(ArcGuiElement child) {
        super.addChild(child);
        markLayoutDirty();
    }

    @Override
    public void clearChildren() {
        super.clearChildren();
        markLayoutDirty();
    }

    public void markLayoutDirty() {
        layoutDirty = true;
    }

    public void layoutNow() {
        int offset = 0;
        for (ArcGuiElement child : children) {
            child.setX(offset);
            offset += child.getWidth() + spacing;
        }
        setWidth(Math.max(0, offset - (children.isEmpty() ? 0 : spacing)));
        layoutDirty = false;
    }

    @Override
    protected void drawChildren(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (layoutDirty) layoutNow();
        super.drawChildren(graphics, context, refX, refY, inheritedOpacity);
    }
}
