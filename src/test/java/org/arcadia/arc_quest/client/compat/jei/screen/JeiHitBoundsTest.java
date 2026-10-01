package org.arcadia.arc_quest.client.compat.jei.screen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JeiHitBoundsTest {
    @Test void scaledAndTranslatedIconUsesRenderedPosition() {
        var hit = JeiHitBounds.transformed(4, 2, 16, 16, 1.5, 0, 0, 1.5, 120, 40);
        assertEquals(new JeiHitBounds(126, 43, 150, 67), hit);
        assertTrue(hit.contains(126, 43));
        assertFalse(hit.contains(150, 43));
        assertFalse(hit.contains(4, 2));
    }
    @Test void scrollingClipsTheInvisiblePartOfAnIcon() {
        var icon = JeiHitBounds.transformed(10, 20, 16, 16, 2, 0, 0, 2, 0, -35);
        var clipped = icon.intersect(new JeiHitBounds(0, 15, 100, 60));
        assertEquals(new JeiHitBounds(20, 15, 52, 37), clipped);
        assertFalse(clipped.contains(25, 10));
        assertTrue(clipped.contains(25, 16));
    }
    @Test void nestedScissorsKeepTheSmallestIntersection() {
        var icon = new JeiHitBounds(20, 20, 60, 60);
        var result = icon.intersect(new JeiHitBounds(0, 30, 100, 100))
                .intersect(new JeiHitBounds(35, 0, 45, 50));
        assertEquals(new JeiHitBounds(35, 30, 45, 50), result);
        assertFalse(result.contains(25, 40));
        assertFalse(result.contains(40, 55));
        assertTrue(result.contains(40, 40));
    }
    @Test void fullyClippedAndZeroScaleRegionsNeverHit() {
        var clipped = new JeiHitBounds(5, 5, 15, 15).intersect(new JeiHitBounds(20, 20, 50, 50));
        assertTrue(clipped.empty());
        assertFalse(clipped.contains(10, 10));
        var collapsed = JeiHitBounds.transformed(0, 0, 16, 16, 0, 0, 0, 0, 100, 100);
        assertTrue(collapsed.empty());
        assertFalse(collapsed.contains(100, 100));
    }
    @Test void negativeScaleUsesBothCorners() {
        var reflected = JeiHitBounds.transformed(0, 0, 16, 16, -2, 0, 0, 2, 100, 10);
        assertEquals(new JeiHitBounds(68, 10, 100, 42), reflected);
    }
    @Test void animatedFractionalEdgeDoesNotExpandIntoNeighbor() {
        var left = new JeiHitBounds(10.25, 10, 26.25, 26);
        var right = new JeiHitBounds(26.25, 10, 42.25, 26);
        assertFalse(left.contains(26.25, 15));
        assertTrue(right.contains(26.25, 15));
    }
}
