package org.arcadia.arc_quest.client.hud.quest.tracker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestTrackerPanelLayoutTest {

    @Test
    void scaleFitsNarrowScreenWidth() {
        float scale = QuestTrackerPanel.fitUiScale(1.5f, 220, 400, 181f, 220f);

        assertTrue(181f * scale <= 218f);
    }

    @Test
    void scaleFitsTallTrackerWithinShortScreen() {
        float scale = QuestTrackerPanel.fitUiScale(1.5f, 800, 240, 181f, 310f);

        assertTrue(310f * scale <= 238f);
    }
}
