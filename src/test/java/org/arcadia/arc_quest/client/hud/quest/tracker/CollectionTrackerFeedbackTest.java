package org.arcadia.arc_quest.client.hud.quest.tracker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectionTrackerFeedbackTest {
    @Test void completedFocusBrieflyStaysVisibleThenReturnsToOverview() {
        var feedback = new CollectionTrackerFeedback();
        assertTrue(feedback.update("quest/run/chapter", "zombie", false, false, 1000).keepFocus());
        var completed = feedback.update("quest/run/chapter", "zombie", false, true, 2000);
        assertTrue(completed.keepFocus());
        assertTrue(completed.focusComplete());
        assertTrue(feedback.update("quest/run/chapter", "zombie", false, true, 3199).keepFocus());
        assertFalse(feedback.update("quest/run/chapter", "zombie", false, true, 3200).keepFocus());
    }

    @Test void waitingForConfirmationHasABoundedLifetime() {
        var feedback = new CollectionTrackerFeedback();
        feedback.update("quest/run/chapter", "", false, false, 1000);
        assertTrue(feedback.update("quest/run/chapter", "", true, false, 2000).readyMessage());
        assertFalse(feedback.update("quest/run/chapter", "", true, false, 5499).hide());
        assertTrue(feedback.update("quest/run/chapter", "", true, false, 5500).hide());
    }

    @Test void reconnectNeverReplaysCompletionAndProgressNeverChangesFocus() {
        var feedback = new CollectionTrackerFeedback();
        var restored = feedback.update("quest/old-run/chapter", "zombie", true, true, 9000);
        assertFalse(restored.keepFocus());
        assertFalse(restored.readyMessage());
        assertTrue(restored.hide());
        assertTrue(feedback.update("quest/new-run/chapter", "skeleton", false, false, 10000).keepFocus());
        assertTrue(feedback.update("quest/new-run/chapter", "skeleton", false, false, 11000).keepFocus());
    }
}
