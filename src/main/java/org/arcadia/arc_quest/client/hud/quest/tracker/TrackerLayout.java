package org.arcadia.arc_quest.client.hud.quest.tracker;

/** Shared, GUI-coordinate layout for the registered HUD and its visual editor. */
public final class TrackerLayout {
    public static final double MIN_SCALE = 0.5;
    public static final double MAX_SCALE = 2.0;
    public static final Settings DEFAULT = new Settings(1.0, 0.0, 1.0);

    private TrackerLayout() {}

    /** Position is a fraction of the space left after fitting the panel on screen. */
    public record Settings(double x, double y, double scale) {
        public Settings {
            x = clamp(finite(x, 1.0), 0.0, 1.0);
            y = clamp(finite(y, 0.0), 0.0, 1.0);
            scale = clamp(finite(scale, 1.0), MIN_SCALE, MAX_SCALE);
        }
    }

    public record Frame(double x, double y, double width, double height,
                        double uiScale, int contentWidth) {
        public int contentHeight() { return Math.max(1, (int) Math.round(height / uiScale)); }
        public double right() { return x + width; }
        public double bottom() { return y + height; }
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
        }
    }

    public static Frame resolve(int screenWidth, int screenHeight, double guiScale,
                                int contentHeight, Settings settings, float topPush) {
        int sw = Math.max(1, screenWidth), sh = Math.max(1, screenHeight);
        double pixelScale = Math.max(1.0, finite(guiScale, 1.0));
        Settings chosen = settings == null ? DEFAULT : settings;
        int panelWidth = contentWidth(sw, pixelScale);
        int panelHeight = Math.max(1, contentHeight);
        double left = leftInset(sw), right = rightInset(sw);
        double top = Math.min(sh - 1.0, topInset(sh) + Math.max(0.0, finite(topPush, 0.0)));
        double bottom = bottomInset(sh);
        double availableWidth = Math.max(1.0, sw - left - right);
        double availableHeight = Math.max(1.0, sh - top - bottom);
        double scale = Math.min(preferredScale(sw, panelWidth, pixelScale) * chosen.scale(),
                Math.min(availableWidth / panelWidth, availableHeight / panelHeight));
        double width = panelWidth * scale, height = panelHeight * scale;
        return new Frame(left + Math.max(0.0, sw - right - width - left) * chosen.x(),
                top + Math.max(0.0, sh - bottom - height - top) * chosen.y(),
                width, height, scale, panelWidth);
    }

    /** Convert a dragged top-left back to resolution-independent settings. */
    public static Settings moveTo(Settings settings, int screenWidth, int screenHeight,
                                  double guiScale, int contentHeight, double pixelX, double pixelY) {
        Settings chosen = settings == null ? DEFAULT : settings;
        Frame frame = resolve(screenWidth, screenHeight, guiScale, contentHeight, chosen, 0);
        int sw = Math.max(1, screenWidth), sh = Math.max(1, screenHeight);
        double left = leftInset(sw), top = topInset(sh);
        double freeWidth = Math.max(0.0, sw - rightInset(sw) - left - frame.width());
        double freeHeight = Math.max(0.0, sh - bottomInset(sh) - top - frame.height());
        return new Settings(freeWidth > 0.0001 ? (pixelX - left) / freeWidth : chosen.x(),
                freeHeight > 0.0001 ? (pixelY - top) / freeHeight : chosen.y(), chosen.scale());
    }

    public static int contentWidth(int screenWidth, double guiScale) {
        double referenceWidth = screenWidth * Math.max(1.0, finite(guiScale, 1.0)) / 3.0;
        return (int) clamp(Math.round(referenceWidth * 0.27),
                TrackerConstants.MIN_PANEL_WIDTH, TrackerConstants.MAX_PANEL_WIDTH);
    }

    static double preferredScale(int screenWidth, int panelWidth, double guiScale) {
        double targetPhysicalWidth = clamp(screenWidth * guiScale * TrackerConstants.TARGET_SCREEN_WIDTH_RATIO,
                TrackerConstants.MIN_PHYSICAL_PANEL_WIDTH, TrackerConstants.MAX_PHYSICAL_PANEL_WIDTH);
        return targetPhysicalWidth / Math.max(1.0, panelWidth * guiScale);
    }

    private static double leftInset(int width) { return Math.min(2.0, Math.max(0, width - 1) / 2.0); }
    private static double rightInset(int width) {
        return Math.min(Math.max(4, Math.min(12, Math.round(width * 0.0125f))), Math.max(0, width - 1) / 2.0);
    }
    private static double topInset(int height) {
        return Math.min(Math.max(12, Math.min(26, Math.round(height * 0.055f))), Math.max(0, height - 1) / 2.0);
    }
    private static double bottomInset(int height) { return Math.min(4.0, Math.max(0, height - 1) / 2.0); }
    private static double finite(double value, double fallback) { return Double.isFinite(value) ? value : fallback; }
    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
