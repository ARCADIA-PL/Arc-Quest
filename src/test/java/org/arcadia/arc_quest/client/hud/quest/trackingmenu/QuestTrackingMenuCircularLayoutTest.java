package org.arcadia.arc_quest.client.hud.quest.trackingmenu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestTrackingMenuCircularLayoutTest {

    @Test
    void keepsSecondCardBelowAtPositiveHalfCycleBoundary() {
        assertEquals(1.0, QuestTrackingMenuScreen.circularDistance(1, 0.0, 2));
        assertTrue(QuestTrackingMenuScreen.circularDistance(1, 0.0001, 2) > 0.0);
    }

    @Test
    void keepsPreviousCardAboveAtNegativeHalfCycleBoundary() {
        assertEquals(-1.0, QuestTrackingMenuScreen.circularDistance(0, 1.0, 2));
        assertTrue(QuestTrackingMenuScreen.circularDistance(0, 0.9999, 2) < 0.0);
    }
}
