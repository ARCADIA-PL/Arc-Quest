package org.arcadia.arc_quest.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ArcQuestToastConfig {
    public static final String FILE_NAME = "arc_quest-toasts.toml";
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue QUEST_ACCEPTED;
    public static final ModConfigSpec.BooleanValue QUEST_COMPLETED;
    public static final ModConfigSpec.BooleanValue QUEST_FAILED;
    public static final ModConfigSpec.BooleanValue PHASE_ADVANCED;
    public static final ModConfigSpec.BooleanValue OBJECTIVE_COMPLETE;

    public static final ModConfigSpec.BooleanValue COLLECTION_ENTRY_DISCOVERED;
    public static final ModConfigSpec.BooleanValue COLLECTION_ENTRY_COMPLETED;
    public static final ModConfigSpec.BooleanValue COLLECTION_REWARD_UNLOCKED;
    public static final ModConfigSpec.BooleanValue COLLECTION_REWARD_CLAIMED;

    public static final ModConfigSpec.BooleanValue PHASE_ADDED;
    public static final ModConfigSpec.BooleanValue PHASE_SWITCHED;
    public static final ModConfigSpec.BooleanValue PHASE_COMPLETED;
    public static final ModConfigSpec.BooleanValue PHASE_PENDING_CONFIRM;
    public static final ModConfigSpec.BooleanValue BRANCH_CHOICE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("quest_notifications");
        QUEST_ACCEPTED = define(builder, "quest_accepted", "Show the quest accepted notification.");
        QUEST_COMPLETED = define(builder, "quest_completed", "Show the quest completed notification.");
        QUEST_FAILED = define(builder, "quest_failed", "Show the quest failed notification.");
        PHASE_ADVANCED = define(builder, "phase_advanced", "Show the phase advanced notification.");
        OBJECTIVE_COMPLETE = define(builder, "objective_complete", "Show the objective completed notification.");
        builder.pop();

        builder.push("collection_notifications");
        COLLECTION_ENTRY_DISCOVERED = define(builder, "entry_discovered", "Show collection entry discovery notifications.");
        COLLECTION_ENTRY_COMPLETED = define(builder, "entry_completed", "Show collection entry completion notifications.");
        COLLECTION_REWARD_UNLOCKED = define(builder, "reward_unlocked", "Show collection reward unlock notifications.");
        COLLECTION_REWARD_CLAIMED = define(builder, "reward_claimed", "Show collection reward claimed notifications.");
        builder.pop();

        builder.push("tracked_quest_notifications");
        PHASE_ADDED = define(builder, "phase_added", "Show the tracked quest phase added toast.");
        PHASE_SWITCHED = define(builder, "phase_switched", "Show the tracked quest phase switched toast.");
        PHASE_COMPLETED = define(builder, "phase_completed", "Show the tracked quest phase completed toast.");
        PHASE_PENDING_CONFIRM = define(builder, "phase_pending_confirm", "Show the manual phase confirmation toast.");
        BRANCH_CHOICE = define(builder, "branch_choice", "Show the tracked quest branch choice toast.");
        builder.pop();

        SPEC = builder.build();
    }

    private ArcQuestToastConfig() {
    }

    private static ModConfigSpec.BooleanValue define(ModConfigSpec.Builder builder, String key, String comment) {
        return builder.comment(comment).define(key, true);
    }
}
