package org.arcadia.arc_quest.client.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientEventHandlerTest {

    @Test
    void activeDialogueUsesPopupEvenWhenScreenIsBrieflyMissing() {
        assertFalse(ClientEventHandler.shouldOpenStandaloneGuide(false, true));
    }

    @Test
    void standaloneGuideRequiresNoScreenAndNoDialogue() {
        assertTrue(ClientEventHandler.shouldOpenStandaloneGuide(false, false));
        assertFalse(ClientEventHandler.shouldOpenStandaloneGuide(true, false));
    }
}
