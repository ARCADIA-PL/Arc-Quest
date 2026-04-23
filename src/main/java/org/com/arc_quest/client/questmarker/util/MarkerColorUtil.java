package org.com.arc_quest.client.questmarker.util;

public final class MarkerColorUtil {

    private MarkerColorUtil() {
    }

    public static float[] argbToFloats(int argb) {
        float a = ((argb >> 24) & 0xFF) / 255.0f;
        float r = ((argb >> 16) & 0xFF) / 255.0f;
        float g = ((argb >> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        return new float[]{r, g, b, a};
    }

    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}