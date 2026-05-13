package org.arcadia.arc_quest.client.hud.quest.journal.history;

import java.util.LinkedHashSet;
import java.util.Set;

public final class QuestChangeNotificationManager {

    public static final QuestChangeNotificationManager INSTANCE = new QuestChangeNotificationManager();

    private final Set<String> unreadQuestIds = new LinkedHashSet<>();

    private QuestChangeNotificationManager() {
    }

    public void markUnread(String questId) {
        if (questId != null && !questId.isEmpty()) {
            unreadQuestIds.add(questId);
        }
    }

    public void markRead(String questId) {
        unreadQuestIds.remove(questId);
    }

    public void markAllRead() {
        unreadQuestIds.clear();
    }

    public boolean hasUnread(String questId) {
        return questId != null && unreadQuestIds.contains(questId);
    }

    public boolean hasAnyUnread() {
        return !unreadQuestIds.isEmpty();
    }

    public int unreadCount() {
        return unreadQuestIds.size();
    }
}