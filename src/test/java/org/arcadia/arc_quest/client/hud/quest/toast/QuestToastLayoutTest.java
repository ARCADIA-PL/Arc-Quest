package org.arcadia.arc_quest.client.hud.quest.toast;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuestToastLayoutTest {
    @Test
    void shrinksTheNotificationWhileKeepingTheLeftCentreAnchor() {
        var frame = QuestToastLayout.resolve(640, 360, 1);
        assertEquals(14, frame.x(), 0.001);
        assertEquals(168.8, frame.y(), 0.001);
        assertEquals(140, frame.width(), 0.001);
        assertEquals(22.4, frame.height(), 0.001);
    }

    @Test
    void compactNotificationOccupiesLessThanOneThirdOfTheNativeAcceptanceWindow() {
        var frame = QuestToastLayout.resolve(1280, 960, 1280f / 480);
        assertTrue(frame.width() < 1280f / 3);
        assertTrue(frame.height() < 960f / 10);
    }

    @Test
    void layoutScalesTogetherAndAlwaysFits() {
        for (int[] viewport : new int[][]{{1, 1}, {120, 80}, {320, 240}, {640, 360}, {1920, 1080}}) {
            for (float scale : new float[]{0.5f, 1, 1.5f, 3, Float.NaN, Float.POSITIVE_INFINITY}) {
                var frame = QuestToastLayout.resolve(viewport[0], viewport[1], scale);
                assertTrue(Float.isFinite(frame.scale()) && frame.scale() > 0);
                assertTrue(frame.x() >= 0 && frame.y() >= 0);
                assertTrue(frame.x() + frame.width() <= viewport[0] + 0.001);
                assertTrue(frame.y() + frame.height() <= viewport[1] + 0.001);
                assertEquals(viewport[1] / 2f, frame.y() + frame.height() / 2f, 0.001);
            }
        }
    }

    @Test
    void pendingActionsStayVisibleUntilTheirStateIsResolved() {
        assertEquals(0, QuestToastLayout.opacity(0, true));
        assertEquals(1, QuestToastLayout.opacity(250, true));
        assertEquals(1, QuestToastLayout.opacity(60_000, true));
    }

    @Test
    void transientAnimationIsBoundedAndHasNoHoldingDrift() {
        assertEquals(0, QuestToastLayout.opacity(-10, false));
        assertTrue(QuestToastLayout.opacity(125, false) > 0);
        assertEquals(1, QuestToastLayout.opacity(250, false));
        assertEquals(1, QuestToastLayout.opacity(3250, false));
        assertEquals(0.5f, QuestToastLayout.opacity(3350, false));
        assertEquals(0, QuestToastLayout.opacity(3450, false));
        assertEquals(0, QuestToastLayout.opacity(6000, false));
    }
}
