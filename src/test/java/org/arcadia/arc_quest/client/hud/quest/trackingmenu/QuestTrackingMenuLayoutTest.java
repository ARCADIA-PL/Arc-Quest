package org.arcadia.arc_quest.client.hud.quest.trackingmenu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestTrackingMenuLayoutTest {
    @Test
    void keepsCardInsideSmallWindowViewport() {
        QuestTrackingMenuLayout.Metrics layout = QuestTrackingMenuLayout.resolve(320, 240);

        assertTrue(layout.railLeft() >= 0);
        assertTrue(layout.railRight() <= 320);
        assertTrue(layout.cardWidth() <= layout.railWidth() - 12 || layout.cardHeight() < 48);
        assertTrue(layout.centerY() - layout.cardHeight() / 2 >= layout.viewportTop());
        assertTrue(layout.centerY() + layout.cardHeight() / 2 <= 240);
    }

    @Test
    void scalesContentForGuiScaleFourLikePhysicalLayout() {
        QuestTrackingMenuLayout.Metrics layout = QuestTrackingMenuLayout.resolve(480, 270);

        assertTrue(QuestTrackingMenuLayout.contentScale(layout.cardWidth(), layout.cardHeight()) < 1f);
        assertTrue(layout.cardHeight() <= Math.round((270 - layout.viewportTop() - 4) * 0.46f));
    }

    @Test
    void keepsNormalWindowAtReferenceContentScale() {
        QuestTrackingMenuLayout.Metrics layout = QuestTrackingMenuLayout.resolve(1280, 720);

        assertTrue(QuestTrackingMenuLayout.contentScale(layout.cardWidth(), layout.cardHeight()) > 0.9f);
    }
}
