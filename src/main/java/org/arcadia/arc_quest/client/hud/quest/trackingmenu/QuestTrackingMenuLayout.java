package org.arcadia.arc_quest.client.hud.quest.trackingmenu;

final class QuestTrackingMenuLayout {
    static final int REFERENCE_CARD_WIDTH = 240;
    static final int REFERENCE_CARD_HEIGHT = 135;

    private static final int VIEWPORT_TOP = 38;
    private static final int MIN_RAIL_WIDTH = 96;
    private static final int MAX_RAIL_WIDTH = 286;
    private static final int MIN_CARD_HEIGHT = 36;

    private QuestTrackingMenuLayout() {
    }

    static Metrics resolve(int screenWidth, int screenHeight) {
        int rightMargin = Math.max(8, Math.min(40, Math.round(screenWidth * 0.07f)));
        int maximumRailWidth = Math.max(72, screenWidth - rightMargin - 8);
        int desiredRailWidth = Math.max(MIN_RAIL_WIDTH,
                Math.min(MAX_RAIL_WIDTH, Math.round(screenWidth * 0.26f)));
        int railWidth = Math.min(desiredRailWidth, maximumRailWidth);
        int railRight = screenWidth - rightMargin;
        int railLeft = railRight - railWidth;

        int cardWidth = Math.max(48, railWidth - 12);
        int cardHeight = Math.max(MIN_CARD_HEIGHT, Math.round(cardWidth * 9f / 16f));
        int availableHeight = Math.max(MIN_CARD_HEIGHT, screenHeight - VIEWPORT_TOP - 4);
        int maximumCardHeight = Math.max(MIN_CARD_HEIGHT, Math.round(availableHeight * 0.46f));
        if (cardHeight > maximumCardHeight) {
            cardHeight = maximumCardHeight;
            cardWidth = Math.max(48, Math.round(cardHeight * 16f / 9f));
        }

        int cardSpacing = cardHeight + Math.max(6, Math.min(12, Math.round(cardHeight * 0.08f)));
        int minimumCenterY = VIEWPORT_TOP + cardHeight / 2;
        int maximumCenterY = Math.max(minimumCenterY, screenHeight - cardHeight / 2 - 4);
        int centerY = Math.max(minimumCenterY, Math.min(maximumCenterY, screenHeight / 2 + 8));
        return new Metrics(railLeft, railRight, railWidth, railLeft + railWidth / 2,
                VIEWPORT_TOP, cardWidth, cardHeight, cardSpacing, centerY);
    }

    static float contentScale(int cardWidth, int cardHeight) {
        float widthScale = cardWidth / (float) REFERENCE_CARD_WIDTH;
        float heightScale = cardHeight / (float) REFERENCE_CARD_HEIGHT;
        return Math.max(0.1f, Math.min(1f, Math.min(widthScale, heightScale)));
    }

    record Metrics(int railLeft, int railRight, int railWidth, int centerX,
                   int viewportTop, int cardWidth, int cardHeight,
                   int cardSpacing, int centerY) {
    }
}
