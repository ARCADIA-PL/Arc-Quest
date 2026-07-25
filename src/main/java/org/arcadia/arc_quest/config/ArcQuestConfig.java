package org.arcadia.arc_quest.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ArcQuestConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLE_QUEST_HISTORY_TAB;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("quest_journal");
        ENABLE_QUEST_HISTORY_TAB = builder
                .comment("Whether to show the history tab in the quest journal.")
                .define("enable_history_tab", true);
        builder.pop();
        SPEC = builder.build();
    }

    private ArcQuestConfig() {
    }

    public static boolean isQuestHistoryTabEnabled() {
        return ENABLE_QUEST_HISTORY_TAB.get();
    }
}
