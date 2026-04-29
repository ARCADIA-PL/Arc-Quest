package org.com.arc_quest.client.hud.quest.tracker;

public class TrackerConstants {
    // 布局常量
    public static final int PANEL_WIDTH = 175;
    public static final int MARGIN_RIGHT = 6;
    public static final int MARGIN_TOP = 30;
    public static final int ACCENT_WIDTH = 3;
    public static final int TITLE_HEIGHT = 14;
    public static final int OBJ_ROW_HEIGHT = 11;
    public static final int PROGRESS_BAR_H = 3;
    public static final int PADDING = 5;
    public static final int GAP_AFTER_TITLE = 2;
    public static final int COLOR_ACCENT_DEFAULT = 0xFF4FC3F7;
    public static final float DISMISS_DELAY = 2000f;
    public static final float DISMISS_SLIDE_TIME = 500f;
    public static final float TIME_WIPE_OUT = 250f;
    public static final float TIME_WIPE_IN = 350f;

    // 并行摘要布局
    public static final int LANE_SUMMARY_MAX_ROWS = 4;
    public static final int LANE_HEADER_H = 12;
    public static final int LANE_ROW_H = 11;
    public static final int LANE_ROW_GAP = 2;

    // 通用动画缓动数学引擎
    public static float lerp(float current, float target, float speed, float dt) {
        return current + (target - current) * Math.min(1f, speed * dt * 60f);
    }

    public static float easeOutCubic(float t) {
        float u = 1f - Math.min(1f, Math.max(0f, t));
        return 1f - u * u * u;
    }

    public static float easeInCubic(float t) {
        float v = Math.min(1f, Math.max(0f, t));
        return v * v * v;
    }
}