package org.arcadia.arc_quest.client.hud.quest.arcmutil;

public final class QuestHudMigrationFlags {
    public static boolean ARC_TRACKER_ENABLED = false;
    public static boolean ARC_TOAST_ENABLED = false;
    public static boolean ARC_JOURNAL_ENABLED = false;
    public static boolean ARC_HISTORY_ENABLED = false;
    public static boolean ARC_MARKER_ENABLED = false;
    public static boolean ARC_DEBUG_OVERLAY = false;

    private QuestHudMigrationFlags() {
    }

    public static void setArcTrackerEnabled(boolean enabled) {
        ARC_TRACKER_ENABLED = enabled;
    }

    public static void setArcDebugOverlay(boolean enabled) {
        ARC_DEBUG_OVERLAY = enabled;
    }

    public static void toggleArcTracker() {
        ARC_TRACKER_ENABLED = !ARC_TRACKER_ENABLED;
    }

    public static void toggleDebugOverlay() {
        ARC_DEBUG_OVERLAY = !ARC_DEBUG_OVERLAY;
    }
}
