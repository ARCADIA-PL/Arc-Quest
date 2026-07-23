package org.arcadia.arc_quest.client.hud.quest.journal.history;

import java.util.LinkedHashSet;
import java.util.Set;

public final class QuestChangeNotificationManager {

    public static final QuestChangeNotificationManager INSTANCE = new QuestChangeNotificationManager();

    private final Set<String> unreadQuestIds = new LinkedHashSet<>();
    private final Set<String> unreadNewQuestIds = new LinkedHashSet<>();

    private QuestChangeNotificationManager() {
    }

    public void markUnread(String questId) {
        if (questId != null && !questId.isEmpty()) {
            unreadQuestIds.add(questId);
        }
    }

    public void markNewQuestUnread(String questId) {
        if (questId != null && !questId.isEmpty()) {
            unreadQuestIds.add(questId);
            unreadNewQuestIds.add(questId);
        }
    }

    public void markRead(String questId) {
        unreadQuestIds.remove(questId);
        unreadNewQuestIds.remove(questId);
    }

    public void markAllRead() {
        unreadQuestIds.clear();
        unreadNewQuestIds.clear();
    }

    public boolean hasUnread(String questId) {
        return questId != null && unreadQuestIds.contains(questId);
    }

    public boolean hasAnyUnread() {
        return !unreadQuestIds.isEmpty();
    }

    public boolean hasUnreadOtherThan(String questId) {
        if (questId == null || questId.isEmpty()) return hasAnyUnread();
        for (String unreadQuestId : unreadQuestIds) {
            if (!questId.equals(unreadQuestId)) return true;
        }
        return false;
    }

    public boolean hasAnyUnreadNewQuest() {
        return !unreadNewQuestIds.isEmpty();
    }

    public boolean hasUnreadNewQuestOtherThan(String questId) {
        if (questId == null || questId.isEmpty()) return hasAnyUnreadNewQuest();
        for (String unreadQuestId : unreadNewQuestIds) {
            if (!questId.equals(unreadQuestId)) return true;
        }
        return false;
    }

    public int unreadCount() {
        return unreadQuestIds.size();
    }
}
