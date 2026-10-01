package org.arcadia.arc_quest.client.hud.shop;

/** 只统计完全离开视口的未读卡片；部分可见的卡片不重复出现在方向提示中。 */
final class TradeUpdateScrollIndex {
    private TradeUpdateScrollIndex() { }

    record Outside(int aboveCount, int belowCount, int nearestAbove, int nearestBelow) { }

    static Outside outside(int[] indices, double scroll, int viewportHeight, int stride, int cardHeight, int padding) {
        int above = upperBound(indices, (scroll - padding - cardHeight) / stride);
        int belowStart = lowerBound(indices, (scroll + viewportHeight - padding) / stride);
        return new Outside(above, indices.length - belowStart,
                above == 0 ? -1 : indices[above - 1], belowStart == indices.length ? -1 : indices[belowStart]);
    }

    static double centeredScroll(int index, int viewportHeight, int maxScroll, int stride, int cardHeight, int padding) {
        return Math.max(0, Math.min(maxScroll, padding + index * (double) stride + cardHeight / 2.0 - viewportHeight / 2.0));
    }

    private static int lowerBound(int[] indices, double value) {
        int low = 0, high = indices.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (indices[mid] < value) low = mid + 1;
            else high = mid;
        }
        return low;
    }

    private static int upperBound(int[] indices, double value) {
        int low = 0, high = indices.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (indices[mid] <= value) low = mid + 1;
            else high = mid;
        }
        return low;
    }
}
