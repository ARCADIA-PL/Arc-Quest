package org.arcadia.arc_quest.mutil.layout;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;

public class ArcScrollableY extends ArcGuiElement {
    protected boolean boundsDirty = false;
    protected double scrollOffset = 0;
    protected double scrollVelocity = 0;
    protected boolean globalScroll = false;
    protected int minOffset;
    protected int maxOffset;
    protected long lastDrawTime = System.currentTimeMillis();

    public ArcScrollableY(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public ArcScrollableY setGlobalScroll(boolean globalScroll) {
        this.globalScroll = globalScroll;
        return this;
    }

    public double getScrollOffset() {
        return scrollOffset;
    }

    public void setScrollOffset(double scrollOffset) {
        this.scrollOffset = Mth.clamp(scrollOffset, minOffset, maxOffset);
    }

    public int getMaxOffset() {
        return maxOffset;
    }

    public void markBoundsDirty() {
        boundsDirty = true;
    }

    public void refreshBounds() {
        int tempMax = 0;
        minOffset = 0;
        for (ArcGuiElement child : children) {
            int childY = getYOffset(this, child.getAttachmentAnchor()) - getYOffset(child, child.getAttachmentPoint());
            minOffset = Math.min(childY, minOffset);
            tempMax = Math.max(childY + child.getHeight(), tempMax);
        }
        maxOffset = Math.max(tempMax - height, 0);
        scrollOffset = Mth.clamp(scrollOffset, minOffset, maxOffset);
        boundsDirty = false;
    }

    @Override
    public void addChild(ArcGuiElement child) {
        super.addChild(child);
        markBoundsDirty();
    }

    @Override
    public void clearChildren() {
        super.clearChildren();
        markBoundsDirty();
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double distance) {
        if (super.onMouseScroll(mouseX, mouseY, distance)) return true;
        if (globalScroll || focused) {
            if (Math.signum(scrollVelocity) != Math.signum(-distance)) scrollVelocity = 0;
            scrollVelocity -= distance * 12;
            scrollOffset = Mth.clamp(scrollOffset - distance * 6, minOffset, maxOffset);
            return true;
        }
        return false;
    }

    @Override
    public void updateFocusState(int refX, int refY, int mouseX, int mouseY) {
        for (ArcGuiElement child : children) {
            if (!child.isVisible()) continue;
            child.updateFocusState(
                    refX + x + getXOffset(this, child.getAttachmentAnchor()) - getXOffset(child, child.getAttachmentPoint()),
                    refY + y + getYOffset(this, child.getAttachmentAnchor()) - getYOffset(child, child.getAttachmentPoint()) - (int) scrollOffset,
                    mouseX, mouseY);
        }
        boolean nextFocused = contains(refX, refY, mouseX, mouseY);
        if (nextFocused != focused) {
            focused = nextFocused;
            if (focused) onFocus();
            else onBlur();
        }
    }

    @Override
    protected void drawChildren(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        long now = System.currentTimeMillis();
        if (scrollVelocity != 0) {
            double delta = (scrollVelocity * 0.2 + Math.signum(scrollVelocity)) * (now - lastDrawTime) / 1000d * 50d;
            if (Math.signum(scrollVelocity) != Math.signum(scrollVelocity - delta)) {
                delta = scrollVelocity;
                scrollVelocity = 0;
            } else {
                scrollVelocity -= delta;
            }
            scrollOffset = Mth.clamp(scrollOffset + delta, minOffset, maxOffset);
        }
        lastDrawTime = now;
        super.drawChildren(graphics, context, refX, refY - (int) scrollOffset, inheritedOpacity);
        if (boundsDirty) refreshBounds();
    }
}
