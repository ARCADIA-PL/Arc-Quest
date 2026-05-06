package org.arcadia.arc_quest.client.hud.quest.journal.history;

public final class QuestChangeHistoryEntry {
    public String id = "";
    public long timeMs = 0L;
    public String worldKey = "default";
    public String playerKey = "local";

    public String questId = "";
    public String questName = "";
    public String phaseId = "";
    public String phaseName = "";
    public String objectiveId = "";
    public String rewardId = "";
    public String categoryId = "";

    public QuestChangeHistoryType type = QuestChangeHistoryType.SYSTEM_SYNC;
    public QuestChangeHistoryCategory category = QuestChangeHistoryCategory.SYSTEM;

    public String title = "";
    public String detail = "";
    public String beforeValue = "";
    public String afterValue = "";

    public int themeColor = 0x90A4AE;
    public int sortPriority = 0;

    public String dedupeKey() {
        return type.name() + "|" + questId + "|" + phaseId + "|" + objectiveId + "|" + rewardId + "|" + categoryId + "|" + afterValue;
    }
}
