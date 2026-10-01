package org.arcadia.arc_quest.client.hud.shop;

import java.util.ArrayList;
import java.util.List;

/** Fits every displayed material into the reserved area; no trailing cost is silently dropped. */
final class TradeIngredientSlotLayout {
    static final int MIN_ICON_SIZE = 12;
    private TradeIngredientSlotLayout() {}
    static int pageCapacity(int width, int height) {
        int stride = MIN_ICON_SIZE + 2;
        return Math.max(1, (width / stride) * (height / stride));
    }
    static Paging paging(int width, int height) {
        boolean horizontal = width >= height;
        int extent = horizontal ? width : height;
        int arrow = Math.min(8, Math.max(0, (extent - MIN_ICON_SIZE - 2) / 2));
        if (horizontal) return new Paging(
                new Area(arrow, 0, width - 2 * arrow, height),
                new Area(0, 0, arrow, height), new Area(width - arrow, 0, arrow, height));
        return new Paging(
                new Area(0, arrow, width, height - 2 * arrow),
                new Area(0, 0, width, arrow), new Area(0, height - arrow, width, arrow));
    }
    static List<Cell> fit(int count, int width, int height, int preferredSize) {
        if (count <= 0 || width <= 0 || height <= 0) return List.of();
        int columns = 1;
        double bestSize = -1;
        for (int candidate = 1; candidate <= count; candidate++) {
            int rows = (count + candidate - 1) / candidate;
            double size = Math.min(preferredSize, Math.min(width / (double) candidate - 2, height / (double) rows - 2));
            if (size > bestSize || size == bestSize && candidate > columns) {
                bestSize = size;
                columns = candidate;
            }
        }
        int rows = (count + columns - 1) / columns;
        double cellWidth = width / (double) columns, cellHeight = height / (double) rows;
        double size = Math.min(preferredSize, Math.min(cellWidth, cellHeight) - Math.min(2, Math.min(cellWidth, cellHeight) / 4));
        List<Cell> cells = new ArrayList<>(count);
        for (int index = 0; index < count; index++)
            cells.add(new Cell(index % columns * cellWidth, index / columns * cellHeight, cellWidth, cellHeight, size));
        return List.copyOf(cells);
    }
    record Cell(double x, double y, double width, double height, double iconSize) {}
    record Area(int x, int y, int width, int height) {}
    record Paging(Area content, Area previous, Area next) {}
}
