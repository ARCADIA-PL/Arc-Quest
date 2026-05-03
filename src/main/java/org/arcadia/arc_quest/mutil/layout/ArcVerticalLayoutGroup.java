package org.arcadia.arc_quest.mutil.layout;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.perf.ArcGuiProfiler;

public class ArcVerticalLayoutGroup extends ArcGuiElement {
    protected boolean layoutDirty = false;
    protected final int spacing;

    public ArcVerticalLayoutGroup(int x, int y, int width, int spacing) {
        super(x, y, width, 0);
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
        invalidateLayout();
    }

    @Override
    public void onChildLayoutInvalidated() {
        markLayoutDirty();
    }

    public void layoutNow() {
        int offset = 0;
        for (ArcGuiElement child : children) {
            child.setY(offset);
            offset += child.getHeight() + spacing;
        }
        setHeight(Math.max(0, offset - (children.isEmpty() ? 0 : spacing)));
        layoutDirty = false;
        ArcGuiProfiler.layoutRebuilt();
    }

    @Override
    protected void drawChildren(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (layoutDirty) layoutNow();
        super.drawChildren(graphics, context, refX, refY, inheritedOpacity);
    }
}
