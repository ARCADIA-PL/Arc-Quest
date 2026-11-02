package org.arcadia.arc_quest.client.hud.shop;

final class TradeCategoryListLayout {

    static final int TOP_PADDING = 10;
    static final int BOTTOM_PADDING = 10;
    static final int ROW_HEIGHT = 28;
    static final int ROW_STRIDE = 36;

    private TradeCategoryListLayout() {
    }

    static int contentHeight(int rowCount) { return contentHeight(rowCount, ROW_HEIGHT, ROW_STRIDE); }

    static int contentHeight(int rowCount, int rowHeight, int rowStride) {
        return contentHeight(rowCount, rowHeight, rowStride, TOP_PADDING);
    }

    static int contentHeight(int rowCount, int rowHeight, int rowStride, int padding) {
        if (rowCount <= 0) return 0;
        return padding + (rowCount - 1) * rowStride + rowHeight + padding;
    }

    static int maxScroll(int rowCount, int viewportHeight) {
        return Math.max(0, contentHeight(rowCount) - Math.max(0, viewportHeight));
    }

    static int rowAt(double mouseY, int viewportY, int viewportHeight,
                     double scrollOffset, int rowCount) {
        return rowAt(mouseY, viewportY, viewportHeight, scrollOffset, rowCount, ROW_HEIGHT, ROW_STRIDE);
    }

    static int rowAt(double mouseY, int viewportY, int viewportHeight,
                     double scrollOffset, int rowCount, int rowHeight, int rowStride) {
        return rowAt(mouseY, viewportY, viewportHeight, scrollOffset, rowCount, rowHeight, rowStride, TOP_PADDING);
    }

    static int rowAt(double mouseY, int viewportY, int viewportHeight, double scrollOffset,
                     int rowCount, int rowHeight, int rowStride, int padding) {
        if (rowCount <= 0 || mouseY < viewportY || mouseY >= viewportY + viewportHeight) return -1;
        double contentY = mouseY - viewportY + scrollOffset - padding;
        if (contentY < 0) return -1;
        int row = (int) Math.floor(contentY / rowStride);
        double withinRow = contentY - row * rowStride;
        return row >= 0 && row < rowCount && withinRow < rowHeight ? row : -1;
    }
}
