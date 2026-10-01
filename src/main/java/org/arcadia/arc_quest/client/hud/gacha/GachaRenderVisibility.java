package org.arcadia.arc_quest.client.hud.gacha;

/** Conservative screen bounds, evaluated after rolling and exit animation transforms. */
final class GachaRenderVisibility {
    private GachaRenderVisibility() {}

    static boolean cardIntersectsScreen(double left, int cardWidth, float scale, int screenWidth) {
        double center = left + cardWidth / 2.0;
        double halfWidth = cardWidth * Math.max(0, scale) / 2.0 + 8;
        return center + halfWidth >= 0 && center - halfWidth <= screenWidth;
    }

    static float previewProgress(float openingProgress, int itemIndex) {
        // Preserve the first rows' stagger, but complete every later row within the same opening.
        return Math.max(0, Math.min(1, openingProgress * 1.5f - Math.min(itemIndex * .05f, .5f)));
    }
}
