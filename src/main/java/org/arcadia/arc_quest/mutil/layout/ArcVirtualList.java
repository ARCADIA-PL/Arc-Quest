package org.arcadia.arc_quest.mutil.layout;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.screen.ArcScissorStack;

public class ArcVirtualList extends ArcGuiElement {
    private final int rowHeight;
    private int itemCount;
    private double scrollOffset;
    private RowRenderer rowRenderer;
    private final ArcScissorStack scissorStack = new ArcScissorStack();

    public ArcVirtualList(int x, int y, int width, int height, int rowHeight) {
        super(x, y, width, height);
        this.rowHeight = Math.max(1, rowHeight);
    }

    public ArcVirtualList setItemCount(int itemCount) {
        this.itemCount = Math.max(0, itemCount);
        scrollOffset = clamp(scrollOffset);
        return this;
    }

    public ArcVirtualList setRowRenderer(RowRenderer rowRenderer) {
        this.rowRenderer = rowRenderer;
        return this;
    }

    public double getScrollOffset() {
        return scrollOffset;
    }

    public void setScrollOffset(double scrollOffset) {
        this.scrollOffset = clamp(scrollOffset);
    }

    public int getMaxScroll() {
        return Math.max(0, itemCount * rowHeight - height);
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double distance) {
        if (focused) {
            setScrollOffset(scrollOffset - distance * rowHeight);
            return true;
        }
        return super.onMouseScroll(mouseX, mouseY, distance);
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!visible || rowRenderer == null || itemCount <= 0) return;
        int drawX = refX + x;
        int drawY = refY + y;
        int first = Math.max(0, (int) (scrollOffset / rowHeight));
        int last = Math.min(itemCount - 1, (int) ((scrollOffset + height) / rowHeight) + 1);
        scissorStack.push(graphics, drawX, drawY, width, height);
        for (int i = first; i <= last; i++) {
            int rowY = drawY + i * rowHeight - (int) scrollOffset;
            rowRenderer.render(graphics, context, i, drawX, rowY, width, rowHeight, inheritedOpacity * opacity);
        }
        scissorStack.pop(graphics);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(value, getMaxScroll()));
    }

    @FunctionalInterface
    public interface RowRenderer {
        void render(GuiGraphics graphics, ArcGuiContext context, int index, int x, int y, int width, int height, float opacity);
    }
}
