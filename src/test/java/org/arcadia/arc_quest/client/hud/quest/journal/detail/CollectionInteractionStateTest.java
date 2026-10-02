package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectionInteractionStateTest {
    @Test void terminalTransparentFramesCannotBecomeOpaqueThroughMinecraftFontColorFallback() {
        for (int alpha = 0; alpha < 4; alpha++) assertFalse(CollectionDetailTransition.shouldDraw(alpha));
        assertTrue(CollectionDetailTransition.shouldDraw(4));
        assertTrue(CollectionDetailTransition.shouldDraw(255));
    }

    @Test void trackingLabelCrossfadesAfterTheDwellAndFadesBackWithoutJumpingOnExit() {
        var dwell = new CollectionTrackDwell();
        dwell.update("coal", 0);
        dwell.update("coal", .19f);
        assertEquals(0, dwell.appearance("coal"));
        dwell.update("coal", .02f);
        assertTrue(dwell.ready("coal"));
        assertTrue(dwell.appearance("coal") > 0 && dwell.appearance("coal") < 1);
        dwell.update("coal", .12f);
        assertEquals(1, dwell.appearance("coal"));
        dwell.update(null, .03f);
        assertFalse(dwell.ready("coal"));
        assertTrue(dwell.appearance("coal") > 0 && dwell.appearance("coal") < 1);
        dwell.update(null, .12f);
        assertEquals(0, dwell.appearance("coal"));
    }

    @Test void enteringAndLeavingModalKeepBackgroundBlockedWithoutAllowingJei() {
        var transition = new CollectionDetailTransition();
        assertTrue(transition.visible(true));
        assertFalse(transition.interactive(true));
        transition.advance(true, .1f);
        assertTrue(transition.alpha() > 0 && transition.alpha() < 1);
        assertFalse(transition.interactive(true));
        transition.advance(true, .1f);
        assertTrue(transition.interactive(true));
        assertEquals(0, transition.offsetY());
        transition.advance(false, .08f);
        assertTrue(transition.visible(false));
        assertFalse(transition.interactive(false));
        transition.advance(false, .08f);
        assertFalse(transition.visible(false));
        assertEquals(0, transition.alpha());
    }

    @Test void reversingExitContinuesFromCurrentOpacityAndResetReleasesTheBarrier() {
        var transition = new CollectionDetailTransition();
        transition.advance(true, 1);
        transition.advance(false, .04f);
        float exitAlpha = transition.alpha();
        transition.advance(true, .025f);
        assertTrue(transition.alpha() > exitAlpha);
        assertFalse(transition.interactive(true));
        transition.advance(true, 1);
        transition.reset();
        assertFalse(transition.visible(false));
        assertFalse(transition.interactive(true));
    }

    @Test void movingBetweenTrackButtonsOrLeavingThemRestartsTheDeliberateDwell() {
        var dwell = new CollectionTrackDwell();
        dwell.update("iron", 0);
        dwell.update("iron", .19f);
        assertFalse(dwell.ready("iron"));
        dwell.update("iron", .02f);
        assertTrue(dwell.ready("iron"));
        dwell.update("logs", .5f);
        assertFalse(dwell.ready("logs"));
        assertFalse(dwell.ready("iron"));
        dwell.update("logs", .21f);
        assertTrue(dwell.ready("logs"));
        dwell.update(null, .5f);
        dwell.update("logs", .5f);
        assertFalse(dwell.ready("logs"));
    }

    @Test void freshJournalClearsEveryVisitedChapterSearchWithoutChangingSelectedEntryOrPhase() {
        Object connection = new Object();
        var first = CollectionJournalState.get(connection, "quest", 1, "forest");
        var second = CollectionJournalState.get(connection, "quest", 1, "mine");
        first.query = "logs"; second.query = "iron";
        first.select("oak"); first.catalogScroll = 80;
        CollectionJournalState.rememberPhase("quest", 1, "mine");
        assertEquals("logs", CollectionJournalState.get(connection, "quest", 1, "forest").query);
        CollectionJournalState.resetBrowserOnOpen(connection);
        assertEquals("", first.query); assertEquals("", second.query);
        assertEquals("oak", first.selection);
        assertEquals("mine", CollectionJournalState.phase(connection, "quest", 1, "forest"));
        assertEquals(0, first.catalogScroll);
        assertFalse(first.expanded); assertFalse(first.selectionMade);
    }

    @Test void freshOpenBeforeRendererHasAnyStateAlsoClearsStaleConnectionMemory() {
        Object firstConnection = new Object(), nextConnection = new Object();
        CollectionJournalState.get(firstConnection, "quest", 1, "forest").query = "old";
        CollectionJournalState.resetBrowserOnOpen(nextConnection);
        assertEquals("", CollectionJournalState.get(nextConnection, "quest", 1, "forest").query);
    }
}
