package org.arcadia.arc_quest.client.hud.quest.toast;

/** One coordinate system for the legacy left-centre notification slot. */
public final class QuestToastLayout {
    public static final int WIDTH = 200;
    public static final int HEIGHT = 32;
    public static final int LEFT = 20;
    public static final float SIZE_FACTOR = 0.70f;
    public static final int TEXT_X = 10, SUBTITLE_Y = 5, TITLE_Y = 16;
    public static final int TEXT_WIDTH = WIDTH - TEXT_X * 2;
    public static final long ENTER_MS = ToastScheduler.ENTER_MILLIS;
    public static final long HOLD_MS = ToastScheduler.HOLD_MILLIS;
    public static final long EXIT_MS = ToastScheduler.EXIT_MILLIS;

    private QuestToastLayout() {}

    public record Frame(float x, float y, float width, float height, float scale) {}

    public static Frame resolve(int screenWidth, int screenHeight, float requestedScale) {
        float width = Math.max(1, screenWidth);
        float height = Math.max(1, screenHeight);
        float scale = Float.isFinite(requestedScale) && requestedScale > 0 ? requestedScale : 1;
        scale *= SIZE_FACTOR;
        scale = Math.min(scale, Math.min(width / (LEFT + WIDTH + 8f), height / (HEIGHT + 8f)));
        return new Frame(LEFT * scale, (height - HEIGHT * scale) / 2f,
                WIDTH * scale, HEIGHT * scale, scale);
    }

    public static float opacity(double elapsedMillis, boolean persistent) {
        double age = Math.max(0, Double.isFinite(elapsedMillis) ? elapsedMillis : 0);
        if (age < ENTER_MS) {
            double remaining = 1 - age / ENTER_MS;
            return (float) (1 - remaining * remaining * remaining);
        }
        if (persistent || age < ENTER_MS + HOLD_MS) return 1;
        return (float) Math.max(0, 1 - (age - ENTER_MS - HOLD_MS) / EXIT_MS);
    }
}
