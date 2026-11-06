package org.arcadia.arc_quest.client.hud.component;

/** Pixel-aware layout shared by journal labels. Minecraft's CJK bitmaps use two samples per unit. */
public final class HudTextMetrics {
    private HudTextMetrics() {}

    public static float readableScale(float requested, double pixelsPerUnit) {
        if (!(requested > 0) || !Float.isFinite(requested) || !(pixelsPerUnit > 0) || !Double.isFinite(pixelsPerUnit))
            throw new IllegalArgumentException("Invalid text scale");
        // Never reduce a 16px CJK glyph below its original sample count.
        return Math.max(requested, (float) (2.0 / pixelsPerUnit));
    }

    public record Position(float x, float y) {}
    public static Position centered(float x, float y, float width, float height,
            float textWidth, float inkHeight, float scale) {
        return new Position(x + (width - textWidth * scale) / 2f,
                y + (height - inkHeight * scale) / 2f);
    }

    public static float snap(float coordinate, double pixelsPerUnit, double translatedPixels) {
        return (float) ((Math.round(coordinate * pixelsPerUnit + translatedPixels) - translatedPixels) / pixelsPerUnit);
    }
}
