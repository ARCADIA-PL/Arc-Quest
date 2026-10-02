package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectionJournalLayoutTest {
    @Test void collapsingGivesTheCatalogAllAvailableWidth() {
        var expanded = CollectionJournalLayout.measure(600, 10, 240, true);
        var collapsed = CollectionJournalLayout.measure(600, 10, 240, false);
        assertFalse(expanded.secondLevel());
        assertEquals(600, collapsed.catalog().width());
        assertEquals(0, collapsed.detail().width());
        assertTrue(collapsed.columns() > expanded.columns());
        assertTrue(expanded.cardWidth() >= CollectionJournalLayout.MIN_CARD_WIDTH);
        assertTrue(expanded.detail().x() >= expanded.catalog().right());
        assertEquals(600, expanded.detail().right());
    }

    @Test void narrowDetailsUseASecondLevelInsteadOfShrinkingNames() {
        for (int width : new int[]{120, 220, 320, 400}) {
            var layout = CollectionJournalLayout.measure(width, 20, 150, true);
            assertTrue(layout.secondLevel());
            assertEquals(width, layout.detail().width());
            assertEquals(width, layout.detail().right());
            assertTrue(layout.cardWidth() >= 92);
        }
    }

    @Test void thousandEntriesOnlyExposeRowsIntersectingTheViewport() {
        int rowHeight = CollectionJournalLayout.CARD_HEIGHT + CollectionJournalLayout.GAP;
        assertArrayEquals(new int[]{0, 9}, CollectionJournalLayout.visibleRange(1000, 3, 0, rowHeight * 3));
        assertArrayEquals(new int[]{30, 39}, CollectionJournalLayout.visibleRange(1000, 3, rowHeight * 10, rowHeight * 3));
        assertArrayEquals(new int[]{999, 1000}, CollectionJournalLayout.visibleRange(1000, 3, rowHeight * 333, rowHeight * 3));
        assertEquals(rowHeight * 334 - CollectionJournalLayout.GAP, CollectionJournalLayout.contentHeight(1000, 3));
    }

    @Test void browserSelectionAndFilteringHaveNoTrackingSideEffects() {
        var state = new CollectionJournalState();
        state.select("skeleton");
        state.category = "materials";
        state.query = "iron";
        state.expanded = false;
        assertEquals("skeleton", state.selection);
        assertEquals("materials", state.category);
        state.select("skeleton");
        assertTrue(state.expanded);
        state.detailScroll = 80;
        state.select("iron");
        assertEquals(0, state.detailScroll);
    }

    @Test void browserMemoryIsScopedToConnectionRunAndRealChapter() {
        Object connection = new Object();
        var first = CollectionJournalState.get(connection, "quest", 1, "forest");
        first.select("zombie");
        first.query = "night";
        assertSame(first, CollectionJournalState.get(connection, "quest", 1, "forest"));
        assertNotSame(first, CollectionJournalState.get(connection, "quest", 1, "mine"));
        assertNotSame(first, CollectionJournalState.get(connection, "quest", 2, "forest"));
        assertEquals("", CollectionJournalState.get(new Object(), "quest", 1, "forest").query);
    }
}
