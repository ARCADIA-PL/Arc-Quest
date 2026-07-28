package org.arcadia.arc_quest.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ArcQuestConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLE_QUEST_HISTORY_TAB;
    public static final ForgeConfigSpec.BooleanValue SHOW_QUEST_HISTORY_UNREAD_DOTS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("quest_journal");
        ENABLE_QUEST_HISTORY_TAB = builder
                .comment("Whether to show the history tab in the quest journal.")
                .define("enable_history_tab", true);
        SHOW_QUEST_HISTORY_UNREAD_DOTS = builder
                .comment("Whether to show unread red-dot indicators for quest history.")
                .define("show_history_unread_dots", true);
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
}
