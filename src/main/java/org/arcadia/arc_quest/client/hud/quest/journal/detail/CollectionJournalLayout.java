package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import org.arcadia.arc_quest.client.hud.component.HudRect;

/** Layout in journal logical pixels. Details are always a second-level view within the journal. */
public record CollectionJournalLayout(HudRect catalog, HudRect detail, int columns,
                                      int cardWidth, int cardHeight, boolean secondLevel) {
    public static final int GAP = 8;
    public static final int MIN_CARD_WIDTH = 92;
    public static final int CARD_HEIGHT = 80;
    public static final int MIN_DETAIL_WIDTH = 206;
    public static final int MAX_DETAIL_WIDTH = 260;
    public static final int SCROLLBAR_GUTTER = 12;

    public static CollectionJournalLayout measure(int width, int y, int height, boolean expanded) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        int catalogWidth = Math.max(1, width - SCROLLBAR_GUTTER);
        int columns = Math.max(1, (catalogWidth + GAP) / (MIN_CARD_WIDTH + GAP));
        int cardWidth = Math.max(1, (catalogWidth - GAP * (columns - 1)) / columns);
        return new CollectionJournalLayout(new HudRect(0, y, catalogWidth, height),
                expanded ? new HudRect(0, y, width, height) : new HudRect(0, y, 0, 0),
                columns, cardWidth, CARD_HEIGHT, expanded);
    }

    public static HudRect modal(int screenWidth, int screenHeight) {
        int margin = Math.max(4, Math.min(20, Math.min(screenWidth, screenHeight) / 12));
        int width = Math.max(1, Math.min(600, screenWidth - margin * 2));
        int height = Math.max(1, screenHeight - margin * 2);
        return new HudRect((screenWidth - width) / 2, margin, width, height);
    }

    public static HudRect detailBody(HudRect panel) {
        int inset = Math.min(14, Math.max(1, panel.width() / 8));
        return new HudRect(panel.x() + inset, panel.y() + 34,
                Math.max(1, panel.width() - inset * 2 - SCROLLBAR_GUTTER),
                Math.max(1, panel.height() - 46));
    }

    /** The scrollbar is outside both text and icon hit regions. */
    public static HudRect scrollbarTrack(HudRect viewport) {
        return new HudRect(viewport.right() + 4, viewport.y(), 3, viewport.height());
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
