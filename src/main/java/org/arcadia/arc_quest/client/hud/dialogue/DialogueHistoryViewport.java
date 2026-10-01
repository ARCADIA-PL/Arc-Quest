package org.arcadia.arc_quest.client.hud.dialogue;

/** Cached block offsets avoid scanning hidden transcript entries on every rendered frame. */
final class DialogueHistoryViewport {
    private DialogueHistoryViewport() {}

    static int firstBlock(int[] cumulativeBottoms, float scrollOffset) {
        int low = 0, high = cumulativeBottoms.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (cumulativeBottoms[middle] < scrollOffset) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    static int firstLine(float viewportTop, int textTop, int stride, int count) {
        return Math.min(count, Math.max(0, (int) Math.floor((viewportTop - textTop) / stride) - 1));
    }

    static int lastLineExclusive(float viewportBottom, int textTop, int stride, int count) {
        return Math.min(count, Math.max(0, (int) Math.ceil((viewportBottom - textTop) / stride) + 1));
    }
}
