package org.arcadia.arc_quest.client.hud.quest.arcmutil.tracker;

public final class ArcQuestTrackerConstants {
    public static final int PANEL_WIDTH = 175;
    public static final int MARGIN_RIGHT = 6;
    public static final int MARGIN_TOP = 30;
    public static final int ACCENT_WIDTH = 3;
    public static final int TITLE_HEIGHT = 14;
    public static final int PADDING = 5;
    public static final int COLOR_ACCENT_DEFAULT = 0xFF4FC3F7;
    public static final float DISMISS_DELAY = 2000f;
    public static final float DISMISS_SLIDE_TIME = 500f;
    public static final float TIME_WIPE_OUT = 250f;
    public static final float TIME_WIPE_IN = 350f;
    public static final int OBJECTIVE_ROW_HEIGHT = 18;
    public static final int OBJECTIVE_ROW_GAP = 4;
    public static final int COLLECTION_SUMMARY_HEIGHT = 50;
    public static final int COLLECTION_LINE_HEIGHT = 13;

    private ArcQuestTrackerConstants() {
    }

    public static float lerp(float current, float target, float speed, float dt) {
        return current + (target - current) * Math.min(1f, speed * dt * 60f);
    }

    public static float easeInCubic(float t) {
        float v = Math.min(1f, Math.max(0f, t));
        return v * v * v;
    }
}
