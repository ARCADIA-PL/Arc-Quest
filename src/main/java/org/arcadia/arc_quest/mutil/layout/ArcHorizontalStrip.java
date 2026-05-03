package org.arcadia.arc_quest.mutil.layout;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;

public class ArcHorizontalStrip extends ArcGuiElement {
    private double scrollOffset;
    private double targetScroll;
    private float smoothing = 0.22f;
    private boolean layoutDirty = true;
    private int spacing;
    private int contentWidth;

    public ArcHorizontalStrip(int x, int y, int width, int height, int spacing) {
        super(x, y, width, height);
        this.spacing = spacing;
    }

    public ArcHorizontalStrip setSpacing(int spacing) {
        this.spacing = spacing;
        layoutDirty = true;
        return this;
    }

    public ArcHorizontalStrip setTargetScroll(double targetScroll) {
        this.targetScroll = clamp(targetScroll);
        return this;
    }

    public double getScrollOffset() {
        return scrollOffset;
    }

    public int getContentWidth() {
        return contentWidth;
    }

    public int getMaxScroll() {
        return Math.max(0, contentWidth - width);
    }

    @Override
    public void addChild(ArcGuiElement child) {
        super.addChild(child);
        layoutDirty = true;
    }

    @Override
    public void clearChildren() {
        super.clearChildren();
        layoutDirty = true;
    }

    private void layoutNow() {
        int cursor = 0;
        for (ArcGuiElement child : children) {
            child.setX(cursor);
            cursor += child.getWidth() + spacing;
        }
        contentWidth = Math.max(0, cursor - (children.isEmpty() ? 0 : spacing));
        targetScroll = clamp(targetScroll);
        scrollOffset = clamp(scrollOffset);
        layoutDirty = false;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(value, getMaxScroll()));
    }

    @Override
    protected void update(ArcGuiContext context, int refX, int refY) {
        if (layoutDirty) layoutNow();
        scrollOffset += (targetScroll - scrollOffset) * Math.min(1.0, context.deltaTime() * smoothing * 60.0);
    }

    @Override
    protected void drawChildren(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        graphics.enableScissor(refX, refY, refX + width, refY + height);
        super.drawChildren(graphics, context, refX - (int) scrollOffset, refY, inheritedOpacity);
        graphics.disableScissor();
    }
}
