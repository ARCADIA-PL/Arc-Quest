package org.arcadia.arc_quest.client.hud.quest.trackingmenu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestTrackingMenuCardRendererTest {
    @Test
    void usesTwoColumnsForThreeObjectivesWhenCompactCardHasRoom() {
        assertEquals(2, QuestTrackingMenuCardRenderer.resolveObjectiveColumns(80, 140));
    }

    @Test
    void fallsBackToOneColumnForVerySmallCard() {
        assertEquals(1, QuestTrackingMenuCardRenderer.resolveObjectiveColumns(64, 140));
        assertEquals(1, QuestTrackingMenuCardRenderer.resolveObjectiveColumns(80, 80));
    }

    @Test
    void keepsCompactTextReadableAboveSmallestFallback() {
        assertTrue(QuestTrackingMenuCardRenderer.resolveCompactTextScale(80, 140) >= 0.82f);
        assertTrue(QuestTrackingMenuCardRenderer.resolveCompactTextScale(60, 140) < 0.82f);
    }
}
