package org.arcadia.arc_quest.mutil.core;

public final class ArcGuiColor {
    private ArcGuiColor() {
    }

    public static int withAlpha(int rgb, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    public static int withOpacity(int color, float opacity) {
        int alpha = Math.round(Math.max(0f, Math.min(1f, opacity)) * 255f);
        int baseAlpha = (color >>> 24) & 0xFF;
        if (baseAlpha == 0) baseAlpha = 255;
        alpha = alpha * baseAlpha / 255;
        return withAlpha(color, alpha);
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aA = (a >>> 24) & 0xFF;
        int aR = (a >>> 16) & 0xFF;
        int aG = (a >>> 8) & 0xFF;
        int aB = a & 0xFF;
        int bA = (b >>> 24) & 0xFF;
        int bR = (b >>> 16) & 0xFF;
        int bG = (b >>> 8) & 0xFF;
        int bB = b & 0xFF;
        int rA = aA + Math.round((bA - aA) * t);
        int rR = aR + Math.round((bR - aR) * t);
        int rG = aG + Math.round((bG - aG) * t);
        int rB = aB + Math.round((bB - aB) * t);
        return (rA << 24) | (rR << 16) | (rG << 8) | rB;
    }
}
