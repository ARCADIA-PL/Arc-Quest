package org.arcadia.arc_quest.dialogue.runtime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DialogueCloseRevisionTest {
    @Test void closingAcceptsAnOlderPageOfTheSameValidatedSession() {
        assertTrue(DialogueSessionManager.acceptsRevision(4, 3, true));
        assertTrue(DialogueSessionManager.acceptsRevision(4, 4, true));
    }
    @Test void closingNeverAcceptsFutureOrNegativeRevisions() {
        assertFalse(DialogueSessionManager.acceptsRevision(4, 5, true));
        assertFalse(DialogueSessionManager.acceptsRevision(4, -1, true));
    }
    @Test void ChoicesAndAutomaticAdvancesStillRequireTheExactPage() {
        assertFalse(DialogueSessionManager.acceptsRevision(4, 3, false));
        assertFalse(DialogueSessionManager.acceptsRevision(4, 5, false));
        assertTrue(DialogueSessionManager.acceptsRevision(4, 4, false));
    }
}
