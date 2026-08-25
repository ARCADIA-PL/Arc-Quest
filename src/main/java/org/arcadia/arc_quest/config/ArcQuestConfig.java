package org.arcadia.arc_quest.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ArcQuestConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLE_QUEST_HISTORY_TAB;
    public static final ForgeConfigSpec.BooleanValue SHOW_QUEST_HISTORY_UNREAD_DOTS;
    public static final ForgeConfigSpec.BooleanValue SHOW_QUEST_JOURNAL_MARK_ALL_READ_BUTTON;
    public static final ForgeConfigSpec.BooleanValue SHOW_GUIDE_MARK_ALL_READ_BUTTON;
    public static final ForgeConfigSpec.BooleanValue SYNC_QUEST_MARKERS_TO_XAERO_MINIMAP;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("quest_journal");
        ENABLE_QUEST_HISTORY_TAB = builder
                .comment("Whether to show the history tab in the quest journal.")
                .define("enable_history_tab", true);
        SHOW_QUEST_HISTORY_UNREAD_DOTS = builder
                .comment("Whether to show unread red-dot indicators for quest history.")
                .define("show_history_unread_dots", true);
        SHOW_QUEST_JOURNAL_MARK_ALL_READ_BUTTON = builder
                .comment("Whether to show the mark-all-read button in the quest journal.")
                .define("show_journal_mark_all_read_button", false);
        SHOW_GUIDE_MARK_ALL_READ_BUTTON = builder
                .comment("Whether to show the clear-unread button in the guide list.")
                .define("show_guide_mark_all_read_button", false);
        builder.pop();
        builder.push("quest_markers");
        SYNC_QUEST_MARKERS_TO_XAERO_MINIMAP = builder
                .comment("Whether Arc Quest markers are mirrored to Xaero's Minimap as third-party waypoints when installed.")
                .define("sync_to_xaero_minimap", true);
        builder.pop();
        SPEC = builder.build();
    }

    private ArcQuestConfig() {
    }

    public static boolean isQuestHistoryTabEnabled() {
        return ENABLE_QUEST_HISTORY_TAB.get();
    }

    public static boolean shouldShowQuestHistoryUnreadDots() {
        return SHOW_QUEST_HISTORY_UNREAD_DOTS.get();
    }

    public static boolean shouldShowQuestJournalMarkAllReadButton() {
        return SHOW_QUEST_JOURNAL_MARK_ALL_READ_BUTTON.get();
    }

    public static boolean shouldShowGuideMarkAllReadButton() {
        return SHOW_GUIDE_MARK_ALL_READ_BUTTON.get();
    }

    public static boolean shouldSyncQuestMarkersToXaeroMinimap() {
        return SYNC_QUEST_MARKERS_TO_XAERO_MINIMAP.get();
    }
}
