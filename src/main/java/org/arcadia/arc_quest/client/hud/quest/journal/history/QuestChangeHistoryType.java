package org.arcadia.arc_quest.client.hud.quest.journal.history;

public enum QuestChangeHistoryType {
    QUEST_ACCEPTED(QuestChangeHistoryCategory.QUEST, 0x4FC3F7, "QUEST ACCEPTED"),
    QUEST_COMPLETED(QuestChangeHistoryCategory.QUEST, 0x66FF66, "QUEST COMPLETED"),
    QUEST_FAILED(QuestChangeHistoryCategory.QUEST, 0xFF6666, "QUEST FAILED"),
    QUEST_ABANDONED(QuestChangeHistoryCategory.QUEST, 0xFFAA66, "QUEST ABANDONED"),

    PHASE_ADDED(QuestChangeHistoryCategory.PHASE, 0x8CD8FF, "PHASE ADDED"),
    PHASE_SWITCHED(QuestChangeHistoryCategory.PHASE, 0xA98BFF, "PHASE SWITCHED"),
    PHASE_ADVANCED(QuestChangeHistoryCategory.PHASE, 0xFFD166, "PHASE ADVANCED"),
    PHASE_COMPLETED(QuestChangeHistoryCategory.PHASE, 0x66FF66, "PHASE COMPLETED"),

    OBJECTIVE_PROGRESS(QuestChangeHistoryCategory.OBJECTIVE, 0x88DDFF, "OBJECTIVE PROGRESS"),
    OBJECTIVE_COMPLETED(QuestChangeHistoryCategory.OBJECTIVE, 0x66FF66, "OBJECTIVE COMPLETED"),

    COLLECTION_ENTRY_DISCOVERED(QuestChangeHistoryCategory.COLLECTION, 0xA98BFF, "ENTRY DISCOVERED"),
    COLLECTION_ENTRY_COMPLETED(QuestChangeHistoryCategory.COLLECTION, 0x7CFFB2, "ENTRY COMPLETED"),
    COLLECTION_CATEGORY_COMPLETED(QuestChangeHistoryCategory.COLLECTION, 0x66FF88, "CATEGORY COMPLETED"),
    COLLECTION_QUEST_COMPLETED(QuestChangeHistoryCategory.COLLECTION, 0x66FF66, "COLLECTION COMPLETED"),

    COLLECTION_REWARD_UNLOCKED(QuestChangeHistoryCategory.REWARD, 0xFFD166, "REWARD UNLOCKED"),
    COLLECTION_REWARD_CLAIMED(QuestChangeHistoryCategory.REWARD, 0xFFE6A3, "REWARD CLAIMED"),

    SYSTEM_SYNC(QuestChangeHistoryCategory.SYSTEM, 0x90A4AE, "SYSTEM SYNC");

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
