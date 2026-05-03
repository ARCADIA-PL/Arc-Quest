package org.arcadia.arc_quest.mutil.layout;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.perf.ArcGuiProfiler;

public class ArcGridLayoutGroup extends ArcGuiElement {
    private int columns;
    private int cellWidth;
    private int cellHeight;
    private int gapX;
    private int gapY;
    private boolean layoutDirty = true;

    public ArcGridLayoutGroup(int x, int y, int columns, int cellWidth, int cellHeight, int gapX, int gapY) {
        super(x, y, 0, 0);
        this.columns = Math.max(1, columns);
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.gapX = gapX;
        this.gapY = gapY;
    }

    public ArcGridLayoutGroup setGrid(int columns, int cellWidth, int cellHeight, int gapX, int gapY) {
        this.columns = Math.max(1, columns);
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.gapX = gapX;
        this.gapY = gapY;
        markLayoutDirty();
        return this;
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
        for (int i = 0; i < children.size(); i++) {
            ArcGuiElement child = children.get(i);
            int col = i % columns;
            int row = i / columns;
            child.setPosition(col * (cellWidth + gapX), row * (cellHeight + gapY));
            child.setSize(cellWidth, cellHeight);
        }
        int rows = children.isEmpty() ? 0 : ((children.size() - 1) / columns) + 1;
        setMeasuredSize(
                columns * cellWidth + Math.max(0, columns - 1) * gapX,
                rows * cellHeight + Math.max(0, rows - 1) * gapY
        );
        layoutDirty = false;
        ArcGuiProfiler.layoutRebuilt();
    }

    @Override
    protected void drawChildren(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (layoutDirty) layoutNow();
        super.drawChildren(graphics, context, refX, refY, inheritedOpacity);
    }
}
