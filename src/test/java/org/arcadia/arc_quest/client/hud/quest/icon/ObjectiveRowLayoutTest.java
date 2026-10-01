package org.arcadia.arc_quest.client.hud.quest.icon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ObjectiveRowLayoutTest {
    @Test void absentIconOccupiesNoSlotOrGap() {
        var plain = ObjectiveRowLayout.measure(180, 9, 1, 35, false, true);
        assertEquals(0, plain.iconSize());
        assertEquals(0, plain.textX());
        assertEquals(180, plain.textWidth());
    }
    @Test void compactStillHasASeparateProgressLine() {
        var row = ObjectiveRowLayout.measure(160, 9, 1, 35, true, true);
        assertEquals(20, row.iconSize());
        assertTrue(row.progressY() > 9);
        assertTrue(row.height() > row.iconSize());
        assertTrue(row.textX() + row.barWidth() < row.countX());
    }
    @Test void wrappedTextExpandsActualHeight() {
        var one = ObjectiveRowLayout.measure(200, 9, 1, 35, true, false);
        var three = ObjectiveRowLayout.measure(200, 9, 3, 35, true, false);
        assertTrue(three.height() > one.height());
        assertTrue(three.progressY() >= 30);
    }
    @Test void narrowRowsRetainPositiveBarAndBoundedCountPosition() {
        var row = ObjectiveRowLayout.measure(65, 9, 1, 120, true, true);
        assertTrue(row.barWidth() > 0);
        assertTrue(row.countX() >= row.textX());
        assertTrue(row.countX() < 65);
    }
}
