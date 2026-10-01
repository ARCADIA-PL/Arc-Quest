package org.arcadia.arc_quest.client.hud.shop;

/** Shared timing and conservative viewport bounds for the existing staggered card animation. */
final class TradeGridAnimation {
    private static final float CARD_DURATION = .45f;
    private static final float CARD_DELAY = .025f;
    private static final float HALO_MARGIN = 8;

    private TradeGridAnimation() {}

    static float duration(int count) {
        return CARD_DURATION + Math.max(0, count - 1) * CARD_DELAY;
    }

    static float openingProgress(float normalizedProgress, int index, int count) {
        if (normalizedProgress >= 1) return 1;
        float elapsedSeconds = Math.max(0, normalizedProgress) * duration(count);
        return Math.max(0, Math.min(1, (elapsedSeconds - index * CARD_DELAY) / CARD_DURATION));
    }

    static boolean intersectsViewport(float drawX, float drawY, int width, int height, float maximumScale,
                                      int screenWidth, int screenHeight) {
        float centerX = drawX + width / 2f, centerY = drawY + height / 2f;
        float halfWidth = width * Math.max(0, maximumScale) / 2 + HALO_MARGIN;
        float halfHeight = height * Math.max(0, maximumScale) / 2 + HALO_MARGIN;
        return centerX + halfWidth >= 0 && centerX - halfWidth <= screenWidth
                && centerY + halfHeight >= 0 && centerY - halfHeight <= screenHeight;
    }
}
