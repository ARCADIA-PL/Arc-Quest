package org.arcadia.arc_quest.client.hud.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TradeScreenLayoutTest {
    @Test
    void guiThreeContentGrowsGentlyFromGuiTwo() {
        var two = TradeScreenLayout.rows(1, TradeScreenLayout.density(2));
        float density = TradeScreenLayout.density(3);
        var three = TradeScreenLayout.rows(density, density);
        assertEquals(1f, TradeScreenLayout.density(1));
        assertEquals(1f, TradeScreenLayout.density(2));
        assertEquals(.75f, density);
        assertEquals(48, two.cardHeight());
        assertTrue(three.cardHeight() * 3 >= two.cardHeight() * 2 * 1.1);
        assertTrue(three.cardHeight() * 3 <= two.cardHeight() * 2 * 1.2);
        assertTrue(three.buttonHeight() * 3 <= two.buttonHeight() * 2 * 1.2);
        assertTrue(three.unit(20) * 3 <= two.unit(20) * 2 * 1.2);
    }

    @Test
    void densityKeepsFontReflowAndCostsInsideCards() {
        for (int gui : new int[] {2, 3, 4, 5}) {
            float density = TradeScreenLayout.density(gui);
            var normal = TradeScreenLayout.rows(density, density);
            var large = TradeScreenLayout.rows(2 * density, density);
            assertTrue(large.cardHeight() > normal.cardHeight());
            assertEquals(normal.unit(22), large.unit(22));
            for (float setting : new float[] {.5f, .75f, 1, 1.5f, 2}) {
                var rows = TradeScreenLayout.rows(setting * density, density);
                assertTrue(rows.costTop() + rows.lineHeight() < rows.cardHeight());
                assertTrue(rows.buttonHeight() <= rows.cardHeight());
                assertTrue(rows.unit(22) <= rows.cardHeight());
            }
        }
    }

    @Test
    void densityPanelsPreserveGuiTwoAndProgressWithoutLargeSizeJumps() {
        var two = TradeScreenLayout.full(960, 540, 1, TradeScreenLayout.density(2));
        var three = TradeScreenLayout.full(640, 360, .75f, TradeScreenLayout.density(3));
        var four = TradeScreenLayout.full(480, 270, .625f, TradeScreenLayout.density(4));
        assertEquals(TradeScreenLayout.full(960, 540), two);
        assertTrue(three.panelWidth() * 3 > two.panelWidth() * 2);
        assertTrue(four.panelWidth() * 4 > three.panelWidth() * 3);
        assertTrue(three.panelHeight() * 3 > two.panelHeight() * 2);
        assertTrue(four.panelHeight() * 4 > three.panelHeight() * 3);
        assertTrue(three.panelWidth() * 3 < two.panelWidth() * 2 * 1.1);
    }

    @Test
    void fontChangesReflowRowsWithoutScalingTheItemIcons() {
        var small = TradeScreenLayout.rows(.75f);
        var normal = TradeScreenLayout.rows(1f);
        var large = TradeScreenLayout.rows(2f);
        assertTrue(small.cardHeight() < normal.cardHeight());
        assertTrue(large.cardHeight() > normal.cardHeight());
        assertTrue(large.buttonHeight() > normal.buttonHeight());
        assertTrue(large.costTop() + large.lineHeight() < large.cardHeight());
        assertTrue(large.categoryStride() > normal.categoryStride());
    }

    @Test
    void guiThreeAndFourKeepReadableMinimumsInsteadOfIdenticalPixelBounds() {
        var two = TradeScreenLayout.full(960, 540);
        var three = TradeScreenLayout.full(640, 360);
        var four = TradeScreenLayout.full(480, 270);
        assertEquals(800, two.panelWidth());
        assertEquals(459, two.panelHeight());
        assertTrue(four.panelWidth() * 4 > three.panelWidth() * 3);
        assertTrue(four.panelHeight() * 4 > three.panelHeight() * 3);
    }

    @Test
    void largeTypeExpandsTheLayoutWithinTheWindow() {
        var normal = TradeScreenLayout.full(640, 360, 1);
        var large = TradeScreenLayout.full(640, 360, 2);
        assertTrue(large.panelWidth() > normal.panelWidth());
        assertTrue(large.panelHeight() > normal.panelHeight());
        for (int[] window : new int[][] {{320, 240}, {480, 270}, {640, 360}, {1280, 720}})
            for (float setting : new float[] {.5f, 1, 1.5f, 2}) {
                var layout = TradeScreenLayout.full(window[0], window[1], setting);
                assertTrue(layout.panelWidth() <= window[0] - 16);
                assertTrue(layout.panelHeight() <= window[1] - 16);
                assertTrue(layout.listWidth() > 0);
            }
    }

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
