package org.arcadia.arc_quest.client.hud.quest.journal.history;

public enum QuestChangeHistoryType {
    QUEST_ACCEPTED(QuestChangeHistoryCategory.QUEST, 0x4FC3F7, "arc_quest.hud.history.type.quest_accepted"),
    QUEST_COMPLETED(QuestChangeHistoryCategory.QUEST, 0x66FF66, "arc_quest.hud.history.type.quest_completed"),
    QUEST_FAILED(QuestChangeHistoryCategory.QUEST, 0xFF6666, "arc_quest.hud.history.type.quest_failed"),
    QUEST_ABANDONED(QuestChangeHistoryCategory.QUEST, 0xFFAA66, "arc_quest.hud.history.type.quest_abandoned"),

    PHASE_ADDED(QuestChangeHistoryCategory.PHASE, 0x8CD8FF, "arc_quest.hud.history.type.phase_added"),
    PHASE_SWITCHED(QuestChangeHistoryCategory.PHASE, 0xA98BFF, "arc_quest.hud.history.type.phase_switched"),
    PHASE_ADVANCED(QuestChangeHistoryCategory.PHASE, 0xFFD166, "arc_quest.hud.history.type.phase_advanced"),
    PHASE_COMPLETED(QuestChangeHistoryCategory.PHASE, 0x66FF66, "arc_quest.hud.history.type.phase_completed"),

    OBJECTIVE_PROGRESS(QuestChangeHistoryCategory.OBJECTIVE, 0x88DDFF, "arc_quest.hud.history.type.objective_progress"),
    OBJECTIVE_COMPLETED(QuestChangeHistoryCategory.OBJECTIVE, 0x66FF66, "arc_quest.hud.history.type.objective_completed"),

    COLLECTION_ENTRY_DISCOVERED(QuestChangeHistoryCategory.COLLECTION, 0xA98BFF, "arc_quest.hud.history.type.collection_entry_discovered"),
    COLLECTION_ENTRY_COMPLETED(QuestChangeHistoryCategory.COLLECTION, 0x7CFFB2, "arc_quest.hud.history.type.collection_entry_completed"),
    COLLECTION_CATEGORY_COMPLETED(QuestChangeHistoryCategory.COLLECTION, 0x66FF88, "arc_quest.hud.history.type.collection_category_completed"),
    COLLECTION_QUEST_COMPLETED(QuestChangeHistoryCategory.COLLECTION, 0x66FF66, "arc_quest.hud.history.type.collection_quest_completed"),

    COLLECTION_REWARD_UNLOCKED(QuestChangeHistoryCategory.REWARD, 0xFFD166, "arc_quest.hud.history.type.collection_reward_unlocked"),
    COLLECTION_REWARD_CLAIMED(QuestChangeHistoryCategory.REWARD, 0xFFE6A3, "arc_quest.hud.history.type.collection_reward_claimed"),

    SYSTEM_SYNC(QuestChangeHistoryCategory.SYSTEM, 0x90A4AE, "arc_quest.hud.history.type.system_sync");

    private final QuestChangeHistoryCategory category;
    private final int accentColor;
    private final String displayName;

    QuestChangeHistoryType(QuestChangeHistoryCategory category, int accentColor, String displayName) {
        this.category = category;
        this.accentColor = accentColor;
        this.displayName = displayName;
    }

    public QuestChangeHistoryCategory category() {
        return category;
    }

    public int accentColor() {
        return accentColor;
    }

    public String displayName() {
        return displayName;
    }
}
