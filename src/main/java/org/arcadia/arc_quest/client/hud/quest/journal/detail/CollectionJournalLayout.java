package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import org.arcadia.arc_quest.client.hud.component.HudRect;

/** Layout in journal logical pixels; a narrow page uses a readable second-level detail view. */
public record CollectionJournalLayout(HudRect catalog, HudRect detail, int columns,
                                      int cardWidth, int cardHeight, boolean secondLevel) {
    public static final int GAP = 8;
    public static final int MIN_CARD_WIDTH = 92;
    public static final int CARD_HEIGHT = 88;
    public static final int MIN_DETAIL_WIDTH = 206;
    public static final int MAX_DETAIL_WIDTH = 260;

    public static CollectionJournalLayout measure(int width, int y, int height, boolean expanded) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        boolean sideBySide = expanded && width >= MIN_CARD_WIDTH * 2 + GAP * 2 + MIN_DETAIL_WIDTH;
        int detailWidth = sideBySide
                ? Math.max(MIN_DETAIL_WIDTH, Math.min(MAX_DETAIL_WIDTH, Math.round(width * .34f))) : width;
        int catalogWidth = sideBySide ? width - detailWidth - GAP : width;
        int detailX = sideBySide ? catalogWidth + GAP : 0;
        int columns = Math.max(1, (catalogWidth + GAP) / (MIN_CARD_WIDTH + GAP));
        int cardWidth = Math.max(1, (catalogWidth - GAP * (columns - 1)) / columns);
        return new CollectionJournalLayout(new HudRect(0, y, catalogWidth, height),
                expanded ? new HudRect(detailX, y, detailWidth, height) : new HudRect(0, y, 0, 0),
                columns, cardWidth, CARD_HEIGHT, expanded && !sideBySide);
    }

    public static int contentHeight(int entries, int columns) {
        if (entries <= 0) return 0;
        return ((entries + columns - 1) / columns) * (CARD_HEIGHT + GAP) - GAP;
    }

    /** Exclusive end avoids drawing or registering hit regions for offscreen specimens. */
    public static int[] visibleRange(int count, int columns, double scroll, int height) {
        int rowHeight = CARD_HEIGHT + GAP;
        int firstRow = Math.max(0, (int) Math.floor(Math.max(0, scroll) / rowHeight));
        int endRow = Math.max(firstRow, (int) Math.ceil((Math.max(0, scroll) + Math.max(0, height)) / rowHeight));
        return new int[]{Math.min(count, firstRow * columns), Math.min(count, endRow * columns)};
    }
}
