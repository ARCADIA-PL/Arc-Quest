package org.arcadia.arc_quest.mutil.theme;

public final class ArcMarkerColorPolicy {
    public static final int DEFAULT_MAIN = 0xFFFFCC00;
    public static final int DEFAULT_SIDE = 0xFF00E5FF;
    public static final int DEFAULT_COMPLETED = 0xFF88FF88;
    public static final int DEFAULT_FAILED = 0xFFFF5555;

    private ArcMarkerColorPolicy() {
    }

    public static int withDistanceFade(int color, float distance, float near, float far) {
        if (far <= near) return color;
        float t = Math.max(0f, Math.min(1f, (distance - near) / (far - near)));
        int alpha = Math.max(50, Math.round(255f * (1f - t * 0.55f)));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public static int choose(boolean mainQuest, boolean completed, boolean failed, int themeColor) {
        if (failed) return DEFAULT_FAILED;
        if (completed) return DEFAULT_COMPLETED;
        if ((themeColor & 0x00FFFFFF) != 0x00FFFFFF) return 0xFF000000 | (themeColor & 0x00FFFFFF);
        return mainQuest ? DEFAULT_MAIN : DEFAULT_SIDE;
    }
}
