package org.arcadia.arc_quest.client.hud.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeCategoryListLayoutTest {

    @Test
    void contentHeightAndScrollAreBoundedByViewport() {
        assertEquals(48, TradeCategoryListLayout.contentHeight(1));
        assertEquals(0, TradeCategoryListLayout.maxScroll(1, 120));
        assertTrue(TradeCategoryListLayout.maxScroll(10, 120) > 0);
    }

    @Test
    void rowHitTestRejectsPanelOverflowAndInterRowGap() {
        int panelY = 100;
        int panelHeight = 100;

        assertEquals(0, TradeCategoryListLayout.rowAt(110, panelY, panelHeight, 0, 6));
        assertEquals(-1, TradeCategoryListLayout.rowAt(140, panelY, panelHeight, 0, 6));
        assertEquals(1, TradeCategoryListLayout.rowAt(146, panelY, panelHeight, 0, 6));
        assertEquals(-1, TradeCategoryListLayout.rowAt(200, panelY, panelHeight, 0, 6));
    }

    @Test
    void rowHitTestAccountsForScrollOffset() {
        assertEquals(1, TradeCategoryListLayout.rowAt(110, 100, 100, 36, 6));
        assertEquals(3, TradeCategoryListLayout.rowAt(110, 100, 100, 108, 6));
    }
}
