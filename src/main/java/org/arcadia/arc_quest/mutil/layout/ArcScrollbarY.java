package org.arcadia.arc_quest.mutil.layout;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.core.ArcGuiColor;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;

public class ArcScrollbarY extends ArcGuiElement {
    private int contentHeight;
    private int viewHeight;
    private double scrollOffset;
    private boolean dragging;
    private double dragYOffset;
    private int trackColor = 0x28000000;
    private int thumbColor = 0x88FFFFFF;
    private int thumbDragColor = 0xCCFFFFFF;
    private Runnable onChanged;

    public ArcScrollbarY(int x, int y, int height) {
        super(x, y, 4, height);
        this.viewHeight = height;
    }

    public ArcScrollbarY configure(int contentHeight, int viewHeight, double scrollOffset) {
        this.contentHeight = Math.max(0, contentHeight);
        this.viewHeight = Math.max(1, viewHeight);
        this.scrollOffset = clamp(scrollOffset);
        this.height = viewHeight;
        return this;
    }

    public ArcScrollbarY setColors(int trackColor, int thumbColor, int thumbDragColor) {
        this.trackColor = trackColor;
        this.thumbColor = thumbColor;
        this.thumbDragColor = thumbDragColor;
        return this;
    }

    public ArcScrollbarY setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged;
        return this;
    }

    public double getScrollOffset() {
        return scrollOffset;
    }

    public void setScrollOffset(double scrollOffset) {
        this.scrollOffset = clamp(scrollOffset);
    }

    public int getMaxScroll() {
        return Math.max(0, contentHeight - viewHeight);
    }

    public int getThumbHeight() {
        if (getMaxScroll() <= 0 || contentHeight <= 0) return height;
        return Math.max(16, Math.round((viewHeight / (float) contentHeight) * viewHeight));
    }

    public int getThumbY() {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) return 0;
        return Math.round((float) (scrollOffset / maxScroll) * (viewHeight - getThumbHeight()));
    }

    @Override
    public boolean onMouseClick(double mouseX, double mouseY, int button) {
        if (button != 0 || getMaxScroll() <= 0 || !focused) return false;
        dragging = true;
        int thumbY = getThumbY();
        int thumbH = getThumbHeight();
        double localY = mouseY - getAbsoluteY();
        if (localY >= thumbY && localY <= thumbY + thumbH) dragYOffset = localY - thumbY;
        else {
            dragYOffset = thumbH / 2.0;
            updateFromMouse(localY);
        }
        return true;
    }

    @Override
    public void onMouseRelease(double mouseX, double mouseY, int button) {
        if (button == 0) dragging = false;
    }

    @Override
    protected void update(ArcGuiContext context, int refX, int refY) {
        if (dragging) updateFromMouse(context.mouseY() - refY - y);
    }

    private void updateFromMouse(double localY) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) return;
        int thumbH = getThumbHeight();
        double ratio = Math.max(0.0, Math.min(1.0, (localY - dragYOffset) / Math.max(1, viewHeight - thumbH)));
        scrollOffset = ratio * maxScroll;
        if (onChanged != null) onChanged.run();
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(value, getMaxScroll()));
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!visible || getMaxScroll() <= 0) return;
        int alphaTrack = ArcGuiColor.withOpacity(trackColor, inheritedOpacity * opacity);
        int alphaThumb = ArcGuiColor.withOpacity(dragging ? thumbDragColor : thumbColor, inheritedOpacity * opacity);
        int drawX = refX + x;
        int drawY = refY + y;
        graphics.fill(drawX, drawY, drawX + width, drawY + height, alphaTrack);
        int thumbY = drawY + getThumbY();
        graphics.fill(drawX, thumbY, drawX + width, thumbY + getThumbHeight(), alphaThumb);
    }
}
