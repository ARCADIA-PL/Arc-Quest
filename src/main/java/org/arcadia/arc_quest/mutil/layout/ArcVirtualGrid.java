package org.arcadia.arc_quest.mutil.layout;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.screen.ArcScissorStack;

public class ArcVirtualGrid extends ArcGuiElement {
    private int itemCount;
    private int columns;
    private int cellWidth;
    private int cellHeight;
    private int gapX;
    private int gapY;
    private double scrollOffset;
    private CellRenderer cellRenderer;
    private final ArcScissorStack scissorStack = new ArcScissorStack();

    public ArcVirtualGrid(int x, int y, int width, int height, int columns, int cellWidth, int cellHeight, int gapX, int gapY) {
        super(x, y, width, height);
        this.columns = Math.max(1, columns);
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.gapX = gapX;
        this.gapY = gapY;
    }

    public ArcVirtualGrid setItemCount(int itemCount) {
        this.itemCount = Math.max(0, itemCount);
        scrollOffset = clamp(scrollOffset);
        return this;
    }

    public ArcVirtualGrid setCellRenderer(CellRenderer cellRenderer) {
        this.cellRenderer = cellRenderer;
        return this;
    }

    public void setScrollOffset(double scrollOffset) {
        this.scrollOffset = clamp(scrollOffset);
    }

    public int getMaxScroll() {
        int rows = itemCount == 0 ? 0 : ((itemCount - 1) / columns) + 1;
        int contentHeight = rows * cellHeight + Math.max(0, rows - 1) * gapY;
        return Math.max(0, contentHeight - height);
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double distance) {
        if (focused) {
            setScrollOffset(scrollOffset - distance * (cellHeight + gapY));
            return true;
        }
        return super.onMouseScroll(mouseX, mouseY, distance);
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!visible || cellRenderer == null || itemCount <= 0) return;
        int drawX = refX + x;
        int drawY = refY + y;
        int rowStride = Math.max(1, cellHeight + gapY);
        int firstRow = Math.max(0, (int) (scrollOffset / rowStride));
        int lastRow = (int) ((scrollOffset + height) / rowStride) + 1;
        int firstIndex = firstRow * columns;
        int lastIndex = Math.min(itemCount - 1, (lastRow + 1) * columns - 1);
        scissorStack.push(graphics, drawX, drawY, width, height);
        for (int i = firstIndex; i <= lastIndex; i++) {
            int col = i % columns;
            int row = i / columns;
            int cellX = drawX + col * (cellWidth + gapX);
            int cellY = drawY + row * (cellHeight + gapY) - (int) scrollOffset;
            cellRenderer.render(graphics, context, i, cellX, cellY, cellWidth, cellHeight, inheritedOpacity * opacity);
        }
        scissorStack.pop(graphics);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(value, getMaxScroll()));
    }

    @FunctionalInterface
    public interface CellRenderer {
        void render(GuiGraphics graphics, ArcGuiContext context, int index, int x, int y, int width, int height, float opacity);
    }
}
