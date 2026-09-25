package org.arcadia.arc_quest.client.hud.shop;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TradeUpdateScrollIndexTest {
    private static final int[] UPDATES = {0, 2, 5, 9};

    @Test
    void firstPagePointsToNearestUpdateBelow() {
        var outside = outside(0, 168);
        assertEquals(0, outside.aboveCount());
        assertEquals(2, outside.belowCount());
        assertEquals(-1, outside.nearestAbove());
        assertEquals(5, outside.nearestBelow());
    }

    @Test
    void middlePageGuidesInBothDirectionsIncludingClippedCards() {
        var outside = outside(125, 168);
        assertEquals(2, outside.aboveCount());
        assertEquals(2, outside.belowCount());
        assertEquals(2, outside.nearestAbove());
        assertEquals(5, outside.nearestBelow());
    }

    @Test
    void fullyVisibleBoundaryIsNotReportedButPartialClippingIs() {
        assertEquals(0, outside(8, 160).aboveCount());
        assertEquals(1, outside(8.01, 160).aboveCount());
        assertEquals(2, outside(0, 168).belowCount());
        assertEquals(3, outside(0, 167).belowCount());
    }

    @Test
    void finalPageHasNoDownHintAndJumpCentersTarget() {
        var outside = outside(396, 168);
        assertEquals(3, outside.aboveCount());
        assertEquals(0, outside.belowCount());
        assertEquals(5, outside.nearestAbove());
        assertEquals(-1, outside.nearestBelow());
        assertEquals(228, TradeUpdateScrollIndex.centeredScroll(5, 168, 396, 56, 48, 8));
        assertEquals(0, TradeUpdateScrollIndex.centeredScroll(0, 168, 396, 56, 48, 8));
        assertEquals(396, TradeUpdateScrollIndex.centeredScroll(9, 168, 396, 56, 48, 8));
    }

    @Test
    void clearedUpdatesRemoveBothHints() {
        var empty = TradeUpdateScrollIndex.outside(new int[0], 200, 168, 56, 48, 8);
        assertEquals(0, empty.aboveCount());
        assertEquals(0, empty.belowCount());
        assertEquals(-1, empty.nearestAbove());
        assertEquals(-1, empty.nearestBelow());
    }

    private static TradeUpdateScrollIndex.Outside outside(double scroll, int height) {
        return TradeUpdateScrollIndex.outside(UPDATES, scroll, height, 56, 48, 8);
    }
}
