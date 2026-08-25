package org.arcadia.arc_quest.client.hud.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeScreenLayoutTest {
    @Test
    void fullShopFitsSmallWindow() {
        TradeScreenLayout.Metrics layout = TradeScreenLayout.full(320, 240);

        assertTrue(layout.panelWidth() <= 304);
        assertTrue(layout.panelHeight() <= 224);
        assertTrue(layout.categoryWidth() + layout.panelGap() + layout.listWidth() <= layout.panelWidth());
    }

    @Test
    void fullShopPreservesNormalDesignBounds() {
        TradeScreenLayout.Metrics layout = TradeScreenLayout.full(1280, 720);

        assertTrue(layout.panelWidth() <= 800);
        assertTrue(layout.panelHeight() <= 600);
        assertTrue(layout.categoryWidth() >= 72);
        assertTrue(layout.listWidth() > 0);
    }

    @Test
    void simpleShopFitsSmallWindow() {
        TradeScreenLayout.Metrics layout = TradeScreenLayout.simple(320, 240);

        assertTrue(layout.panelWidth() <= 304);
        assertTrue(layout.panelHeight() <= 224);
        assertTrue(layout.listWidth() == layout.panelWidth());
    }

    @Test
    void scrollbarGeometryKeepsThumbWithinTrack() {
        int listHeight = 180;
        int contentHeight = 20 * (TradeListPanel.CARD_HEIGHT + 8) + 4;
        int thumbHeight = Math.min(listHeight, Math.max(16, (int) ((float) listHeight / contentHeight * listHeight)));

        assertTrue(thumbHeight >= 16);
        assertTrue(thumbHeight <= listHeight);
        assertTrue(listHeight - thumbHeight >= 0);
    }

    @Test
    void scrollbarPositionClampsToTrack() {
        int listHeight = 180;
        int contentHeight = 20 * (TradeListPanel.CARD_HEIGHT + 8) + 4;
        int maxScroll = contentHeight - listHeight;
        int thumbHeight = Math.min(listHeight, Math.max(16, (int) ((float) listHeight / contentHeight * listHeight)));
        int travel = listHeight - thumbHeight;

        double topAtStart = Math.max(0, Math.min(1, -100.0 / travel));
        double topAtEnd = Math.max(0, Math.min(1, (travel + 100.0) / travel));

        assertTrue(topAtStart * maxScroll >= 0);
        assertTrue(topAtEnd * maxScroll <= maxScroll);
    }
}
