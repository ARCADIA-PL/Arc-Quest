package org.arcadia.arc_quest.client.hud.guide;

/** Content-local vertical clipping; keep the existing layout and scroll extent unchanged. */
final class GuideContentVisibility {
    private GuideContentVisibility() {}

    static boolean intersects(double localY, double height, double scrollOffset, int viewportHeight) {
        if (!Double.isFinite(localY) || !Double.isFinite(height) || !Double.isFinite(scrollOffset)) return true;
        double top = 12d - scrollOffset + localY;
        // Conservative glyph/rounding margin, including scaled text near the scissor edge.
        return top + height >= -2d && top <= viewportHeight + 2d;
    }
}
