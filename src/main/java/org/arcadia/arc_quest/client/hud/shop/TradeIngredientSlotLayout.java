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
        return paging(width, height, MIN_ICON_SIZE + 2);
    }
    static Paging paging(int width, int height, int minimumContentExtent) {
        boolean horizontal = width >= height;
        int extent = horizontal ? width : height;
        int arrow = Math.min(8, Math.max(0, (extent - minimumContentExtent) / 2));
        if (horizontal) return new Paging(
                new Area(arrow, 0, width - 2 * arrow, height),
                new Area(0, 0, arrow, height), new Area(width - arrow, 0, arrow, height));
        return new Paging(
                new Area(0, arrow, width, height - 2 * arrow),
                new Area(0, 0, width, arrow), new Area(0, height - arrow, width, arrow));
    }
    /** Preserve each inline cost's natural width; unused space stays at the right. */
    static List<InlinePage> inlinePages(List<Integer> widths, int availableWidth, int gap) {
        if (widths.isEmpty() || availableWidth <= 0) return List.of();
        List<InlinePage> pages = new ArrayList<>();
        List<InlineCell> cells = new ArrayList<>();
        int right = 0;
        for (int index = 0; index < widths.size(); index++) {
            int width = Math.min(availableWidth, Math.max(1, widths.get(index)));
            int x = cells.isEmpty() ? 0 : right + gap;
            if (!cells.isEmpty() && x + width > availableWidth) {
                pages.add(new InlinePage(List.copyOf(cells)));
                cells.clear();
                x = 0;
            }
            cells.add(new InlineCell(index, x, width));
            right = x + width;
        }
        if (!cells.isEmpty()) pages.add(new InlinePage(List.copyOf(cells)));
        return List.copyOf(pages);
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
    record InlineCell(int index, int x, int width) {}
    record InlinePage(List<InlineCell> cells) {}
}
