package org.arcadia.arc_quest.client.hud.shop;

final class TradeScreenLayout {
    private static final int SCREEN_MARGIN = 8;
    private static final int FULL_PANEL_GAP = 16;

    private TradeScreenLayout() {
    }

    static Metrics full(int screenWidth, int screenHeight) {
        return full(screenWidth, screenHeight, 1f);
    }

    static Metrics full(int screenWidth, int screenHeight, float textScale) {
        return full(screenWidth, screenHeight, textScale, 1f);
    }

    // Above GUI 2, grow content gently in physical pixels instead of jumping 50% at GUI 3.
    // This only changes measurements: rendering and input retain native screen coordinates.
    static float density(double guiScale) {
        return guiScale <= 2 ? 1f : (float) ((2 + .25 * (guiScale - 2)) / guiScale);
    }

    static Metrics full(int screenWidth, int screenHeight, float textScale, float density) {
        Rows rows = rows(textScale, density);
        int headerHeight = rows.unit(40) + rows.searchHeight();
        float ratio = .85f + (1 - density) * .04f;
        int panelWidth = fitDimension(screenWidth, Math.round(420 * density + 140 * textScale), rows.unit(800), ratio);
        int panelHeight = fitDimension(screenHeight, headerHeight + 3 * rows.stride() + rows.unit(44), rows.unit(600), ratio);
        int categoryWidth = Math.max(rows.unit(72), Math.min(Math.round(130 * Math.max(density, textScale)), Math.round(panelWidth * 0.24f)));
        int gap = rows.unit(FULL_PANEL_GAP);
        int listWidth = Math.max(rows.unit(48), panelWidth - categoryWidth - gap);
        return new Metrics(panelWidth, panelHeight, categoryWidth, listWidth,
                headerHeight, gap);
    }

    static Rows rows(float textScale) {
        return rows(textScale, 1f);
    }

    static Rows rows(float textScale, float density) {
        int lineHeight = Math.max(Math.round(5 * density), (int) Math.ceil(9 * textScale));
        return new Rows(Math.max(Math.round(40 * density), Math.round(30 * density) + lineHeight * 2),
                Math.max(Math.round(56 * density), Math.round(80 * textScale)),
                Math.max(Math.round(24 * density), lineHeight + Math.round(14 * density)), lineHeight,
                Math.max(Math.round(20 * density), lineHeight + Math.round(10 * density)), density);
    }

    record Rows(int cardHeight, int buttonWidth, int buttonHeight, int lineHeight, int searchHeight, float density) {
        int unit(int size) { return Math.max(1, Math.round(size * density)); }
        int stride() { return cardHeight + unit(8); }
        int costTop() { return unit(16) + lineHeight; }
        int categoryHeight() { return Math.max(unit(28), lineHeight + unit(18)); }
        int categoryStride() { return categoryHeight() + unit(8); }
        int navigationHeight() { return Math.max(unit(18), lineHeight + unit(8)); }
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
