package org.arcadia.arc_quest.client.hud.quest.journal.history;

public enum QuestChangeHistoryCategory {
    QUEST("QUEST"),
    PHASE("PHASE"),
    OBJECTIVE("OBJ"),
    COLLECTION("COLL"),
    REWARD("REWARD"),
    SYSTEM("SYS");

    private final String shortLabel;

    QuestChangeHistoryCategory(String shortLabel) {
        this.shortLabel = shortLabel;
    }

    public String shortLabel() {
        return shortLabel;
    }
}
