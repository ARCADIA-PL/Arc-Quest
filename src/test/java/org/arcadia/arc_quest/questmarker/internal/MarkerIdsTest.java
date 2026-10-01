package org.arcadia.arc_quest.questmarker.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkerIdsTest {

    @Test
    void trackingPhaseIdsAreDerived() {
        String markerId = MarkerIds.trackingPhase("arc_quest:test", "travel", "destination");

        assertEquals("aq:tracking:arc_quest:test:travel:destination", markerId);
        assertTrue(MarkerIds.isTracking(markerId));
        assertTrue(MarkerIds.isDerived(markerId));
    }
}
