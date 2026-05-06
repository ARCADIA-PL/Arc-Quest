package org.arcadia.arc_quest.client.hud.quest.journal.history;

public final class QuestChangeHistoryFilters {
    public String questId = "";
    public QuestChangeHistoryCategory category = null;
    public QuestChangeHistoryType type = null;
    public int limit = 200;

    public boolean matches(QuestChangeHistoryEntry entry) {
        if (entry == null) return false;
        if (questId != null && !questId.isEmpty() && !questId.equals(entry.questId)) return false;
        if (category != null && category != entry.category) return false;
        return type == null || type == entry.type;
    }
}
