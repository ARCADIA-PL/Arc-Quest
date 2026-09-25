package org.arcadia.arc_quest.client.hud.shop;

/** 对已排序的未读条目位置作二分查询；包含被视口边缘截断的卡片。 */
final class TradeUpdateScrollIndex {
    private TradeUpdateScrollIndex() { }

    record Outside(int aboveCount, int belowCount, int nearestAbove, int nearestBelow) { }

    static Outside outside(int[] indices, double scroll, int viewportHeight, int stride, int cardHeight, int padding) {
        int above = lowerBound(indices, (scroll - padding) / stride);
        int belowStart = upperBound(indices, (scroll + viewportHeight - padding - cardHeight) / stride);
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
