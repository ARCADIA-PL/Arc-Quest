package org.arcadia.arc_quest.client.hud.shop;

final class TradeScreenLayout {
    private static final int SCREEN_MARGIN = 8;
    private static final int FULL_HEADER_HEIGHT = 36;
    private static final int FULL_PANEL_GAP = 16;

    private TradeScreenLayout() {
    }

    static Metrics full(int screenWidth, int screenHeight) {
        int panelWidth = fitDimension(screenWidth, 380, 800, 0.85f);
        int panelHeight = fitDimension(screenHeight, 220, 600, 0.85f);
        int categoryWidth = Math.max(72, Math.min(130, Math.round(panelWidth * 0.20f)));
        int listWidth = Math.max(48, panelWidth - categoryWidth - FULL_PANEL_GAP);
        return new Metrics(panelWidth, panelHeight, categoryWidth, listWidth,
                FULL_HEADER_HEIGHT, FULL_PANEL_GAP);
    }

    static Metrics simple(int screenWidth, int screenHeight) {
        int panelWidth = fitDimension(screenWidth, 220, 800, 0.85f);
        int panelHeight = fitDimension(screenHeight, 140, 600, 0.70f);
        return new Metrics(panelWidth, panelHeight, 0, panelWidth, 0, 0);
    }

    private static int fitDimension(int screenSize, int preferredMinimum, int maximum, float ratio) {
        int available = Math.max(1, screenSize - SCREEN_MARGIN * 2);
        int desired = Math.round(screenSize * ratio);
        return Math.min(available, Math.min(maximum, Math.max(1, Math.max(preferredMinimum, desired))));
    }

    record Metrics(int panelWidth, int panelHeight, int categoryWidth, int listWidth,
                   int headerHeight, int panelGap) {
    }
}
