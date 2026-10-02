package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalScrollbar;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectionJournalLayoutTest {
    @Test void secondaryDetailsPreserveTheCatalogWidthAndColumnCount() {
        var expanded = CollectionJournalLayout.measure(600, 10, 240, true);
        var collapsed = CollectionJournalLayout.measure(600, 10, 240, false);
        assertTrue(expanded.secondLevel());
        assertEquals(600 - CollectionJournalLayout.SCROLLBAR_GUTTER, collapsed.catalog().width());
        assertEquals(0, collapsed.detail().width());
        assertEquals(collapsed.columns(), expanded.columns());
        assertTrue(expanded.cardWidth() >= CollectionJournalLayout.MIN_CARD_WIDTH);
        assertEquals(0, expanded.detail().x());
        assertEquals(600, expanded.detail().right());
    }

    @Test void narrowDetailsUseASecondLevelInsteadOfShrinkingNames() {
        for (int width : new int[]{120, 220, 320, 400, 640, 900}) {
            var layout = CollectionJournalLayout.measure(width, 20, 150, true);
            assertTrue(layout.secondLevel());
            assertEquals(width, layout.detail().width());
            assertEquals(width, layout.detail().right());
            assertTrue(layout.cardWidth() >= 92);
        }
    }

    @Test void detailsUseTheWindowHeightAndKeepTheScrollbarOutsideText() {
        for (int[] dimensions : new int[][]{{640, 360}, {320, 180}, {900, 540}}) {
            var modal = CollectionJournalLayout.modal(dimensions[0], dimensions[1]);
            var body = CollectionJournalLayout.detailBody(modal);
            var track = CollectionJournalLayout.scrollbarTrack(body);
            assertTrue(modal.x() >= 0 && modal.right() <= dimensions[0]);
            assertTrue(modal.y() >= 0 && modal.bottom() <= dimensions[1]);
            assertTrue(modal.height() >= dimensions[1] - 40);
            assertTrue(body.height() >= modal.height() - 46);
            assertTrue(track.x() > body.right());
            assertTrue(track.right() < modal.right());
            assertEquals(body.height(), track.height());
        }
    }

    @Test void scrollbarTrackClickAndThumbDraggingActuallyChangeTheScroll() {
        var scrollbar = new JournalScrollbar(3, 18);
        var track = new HudRect(100, 20, 3, 100);
        assertFalse(scrollbar.mouseClicked(50, 70, track, 8, 400, 0).consumed());
        var clicked = scrollbar.mouseClicked(105, 70, track, 8, 400, 0);
        assertTrue(clicked.consumed());
        assertEquals(150, clicked.scrollOffset(), .001);
        assertEquals(300, scrollbar.mouseDragged(1000, track, 400, clicked.scrollOffset()).scrollOffset(), .001);
        assertEquals(0, scrollbar.mouseDragged(-1000, track, 400, clicked.scrollOffset()).scrollOffset(), .001);
        assertTrue(scrollbar.mouseReleased(0));
        assertFalse(scrollbar.mouseDragged(70, track, 400, 150).consumed());
        assertFalse(scrollbar.mouseClicked(102, 70, track, 8, 80, 0).consumed());
    }

    @Test void defaultSelectionDoesNotOpenDetailsUntilThePlayerSelectsACard() {
        var state = new CollectionJournalState();
        state.validate(java.util.List.of("zombie", "iron"));
        assertEquals("zombie", state.selection);
        assertFalse(state.expanded);
        assertFalse(state.selectionMade);
        state.select("iron");
        assertTrue(state.expanded);
        state.validate(java.util.List.of("zombie"));
        assertFalse(state.expanded);
        assertFalse(state.selectionMade);
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
