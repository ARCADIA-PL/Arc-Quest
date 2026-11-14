package org.arcadia.arc_quest.client.hud.questmarker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarkerProjectionTest {
    @Test
    void frameProjectionKeepsScreenCenterAndEdgeSafety() {
        var forward = MarkerProjection.projectCamera(0, 0, 5, 400, 240, 1, 1, 30);
        assertTrue(forward.onScreen);
        assertEquals(200, forward.x);
        assertEquals(120, forward.y);
        var behind = MarkerProjection.projectCamera(0, 0, -5, 400, 240, 1, 1, 30);
        assertTrue(behind.behind);
        assertFalse(behind.onScreen);
        assertTrue(behind.y > 120);
        assertTrue(Math.abs(behind.x - 200) >= 100, "Edge markers still avoid the hotbar");
        var right = MarkerProjection.projectCamera(20, 0, 1, 400, 240, 1, 1, 30);
        assertFalse(right.onScreen);
        assertEquals(370, right.x, .01);
    }
}
