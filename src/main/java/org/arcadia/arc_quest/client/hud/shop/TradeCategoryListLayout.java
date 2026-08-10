package org.arcadia.arc_quest.client.hud.shop;

final class TradeCategoryListLayout {

    static final int TOP_PADDING = 10;
    static final int BOTTOM_PADDING = 10;
    static final int ROW_HEIGHT = 28;
    static final int ROW_STRIDE = 36;

    private TradeCategoryListLayout() {
    }

    static int contentHeight(int rowCount) {
        if (rowCount <= 0) return 0;
        return TOP_PADDING + (rowCount - 1) * ROW_STRIDE + ROW_HEIGHT + BOTTOM_PADDING;
    }

    static int maxScroll(int rowCount, int viewportHeight) {
        return Math.max(0, contentHeight(rowCount) - Math.max(0, viewportHeight));
    }

    static int rowAt(double mouseY, int viewportY, int viewportHeight,
                     double scrollOffset, int rowCount) {
        if (rowCount <= 0 || mouseY < viewportY || mouseY >= viewportY + viewportHeight) return -1;
        double contentY = mouseY - viewportY + scrollOffset - TOP_PADDING;
        if (contentY < 0) return -1;
        int row = (int) Math.floor(contentY / ROW_STRIDE);
        double withinRow = contentY - row * ROW_STRIDE;
        return row >= 0 && row < rowCount && withinRow < ROW_HEIGHT ? row : -1;
    }
}
