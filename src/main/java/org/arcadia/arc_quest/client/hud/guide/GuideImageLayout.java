package org.arcadia.arc_quest.client.hud.guide;

/** Shared image sizing preserves aspect ratio for collection details and enlarged guide images. */
public record GuideImageLayout(int x, int y, int width, int height) {
    public enum Fit { CONTAIN, COVER }

    public static GuideImageLayout measure(int x, int y, int width, int height,
                                          int textureWidth, int textureHeight, Fit fit) {
        if (width <= 0 || height <= 0) return new GuideImageLayout(x, y, 0, 0);
        textureWidth = Math.max(1, textureWidth);
        textureHeight = Math.max(1, textureHeight);
        double scale = fit == Fit.COVER ? Math.max((double) width / textureWidth, (double) height / textureHeight)
                : Math.min((double) width / textureWidth, (double) height / textureHeight);
        int drawWidth = Math.max(1, (int) Math.round(textureWidth * scale));
        int drawHeight = Math.max(1, (int) Math.round(textureHeight * scale));
        return new GuideImageLayout(x + (width - drawWidth) / 2, y + (height - drawHeight) / 2,
                drawWidth, drawHeight);
    }
}
