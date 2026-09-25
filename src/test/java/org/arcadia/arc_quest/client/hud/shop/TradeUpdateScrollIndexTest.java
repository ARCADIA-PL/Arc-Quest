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
    void middlePageExcludesPartiallyVisibleCards() {
        var outside = outside(125, 168);
        assertEquals(1, outside.aboveCount());
        assertEquals(1, outside.belowCount());
        assertEquals(0, outside.nearestAbove());
        assertEquals(9, outside.nearestBelow());
    }

    @Test
    void onlyEntirelyHiddenCardsAreReported() {
        assertEquals(0, outside(55.99, 160).aboveCount());
        assertEquals(1, outside(56, 160).aboveCount());
        assertEquals(2, outside(0, 121).belowCount());
        assertEquals(3, outside(0, 120).belowCount());
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
    void cardSpanningShortViewportIsNotCountedInEitherDirection() {
        var outside = TradeUpdateScrollIndex.outside(new int[]{0}, 20, 10, 56, 48, 8);
        assertEquals(0, outside.aboveCount());
        assertEquals(0, outside.belowCount());
    }

    @Test
    void countsMatchIndependentRectangleIntersectionAcrossScrollPositions() {
        for (int height : new int[]{1, 10, 48, 49, 100, 168}) {
            for (double scroll = 0; scroll <= 564; scroll += 0.5) {
                int above = 0, below = 0;
                for (int index : UPDATES) {
                    double top = 8 + index * 56 - scroll;
                    if (top + 48 <= 0) above++;
                    if (top >= height) below++;
                }
                var actual = outside(scroll, height);
                assertEquals(above, actual.aboveCount());
                assertEquals(below, actual.belowCount());
                assertTrue(actual.aboveCount() + actual.belowCount() <= UPDATES.length);
            }
        }
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
