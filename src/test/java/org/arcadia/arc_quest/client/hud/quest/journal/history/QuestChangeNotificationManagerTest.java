package org.arcadia.arc_quest.client.hud.quest.journal.history;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestChangeNotificationManagerTest {

    private final QuestChangeNotificationManager manager = QuestChangeNotificationManager.INSTANCE;

    @BeforeEach
    void setUp() {
        manager.markAllRead();
    }

    @AfterEach
    void tearDown() {
        manager.markAllRead();
    }

    @Test
    void newQuestUnreadIsTrackedSeparatelyFromOrdinaryUpdates() {
        manager.markUnread("arc_quest:updated");

        assertTrue(manager.hasAnyUnread());
        assertFalse(manager.hasAnyUnreadNewQuest());
        assertFalse(manager.hasUnreadOtherThan("arc_quest:updated"));
        assertTrue(manager.hasUnreadOtherThan("arc_quest:tracked"));

        manager.markNewQuestUnread("arc_quest:new");

        assertTrue(manager.hasUnread("arc_quest:new"));
        assertTrue(manager.hasAnyUnreadNewQuest());
        assertTrue(manager.hasUnreadOtherThan("arc_quest:new"));
        assertTrue(manager.hasUnreadOtherThan("arc_quest:tracked"));
        assertFalse(manager.hasUnreadNewQuestOtherThan("arc_quest:new"));
        assertTrue(manager.hasUnreadNewQuestOtherThan("arc_quest:tracked"));
    }

    @Test
    void readingQuestClearsBothUnreadCategories() {
        manager.markNewQuestUnread("arc_quest:new");

        manager.markRead("arc_quest:new");

        assertFalse(manager.hasUnread("arc_quest:new"));
        assertFalse(manager.hasAnyUnread());
        assertFalse(manager.hasAnyUnreadNewQuest());
    }

    @Test
    void trackingTransitionConsumesHiddenNotificationsOnBothQuests() {
        manager.markNewQuestUnread("arc_quest:main");
        manager.markUnread("arc_quest:side");
        manager.markUnread("arc_quest:unrelated");

        manager.acknowledgeTrackedQuestTransition("arc_quest:main", "arc_quest:side");

        assertFalse(manager.hasUnread("arc_quest:main"));
        assertFalse(manager.hasUnread("arc_quest:side"));
        assertFalse(manager.hasAnyUnreadNewQuest());
        assertTrue(manager.hasUnread("arc_quest:unrelated"));
    }
}
