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
}
