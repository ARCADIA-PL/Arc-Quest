package org.arcadia.arc_quest.quest.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestTrackingPriorityTest {

    @Test
    void groupPriorityWinsBeforeQuestPriority() {
        QuestTrackingPriority preferredGroup = priority(0, "group:main", 100, 20, "test:main");
        QuestTrackingPriority preferredQuest = priority(1, "group:side", 0, 10, "test:side");

        assertEquals("test:main", first(preferredQuest, preferredGroup));
    }

    @Test
    void groupIdentityWinsBeforeQuestPriorityWhenGroupOrdersMatch() {
        QuestTrackingPriority earlierGroup = priority(0, "group:a", 100, 20, "test:a");
        QuestTrackingPriority laterGroup = priority(0, "group:b", 0, 10, "test:b");

        assertEquals("test:a", first(laterGroup, earlierGroup));
    }

    @Test
    void questPriorityWinsWithinTheSameGroup() {
        QuestTrackingPriority earlierQuest = priority(0, "group:main", 1, 20, "test:earlier");
        QuestTrackingPriority laterQuest = priority(0, "group:main", 2, 10, "test:later");

        assertEquals("test:earlier", first(laterQuest, earlierQuest));
    }

    @Test
    void acceptanceTimeAndIdStabilizeEqualPriorities() {
        QuestTrackingPriority oldQuest = priority(0, "group:main", 1, 10, "test:z");
        QuestTrackingPriority newQuest = priority(0, "group:main", 1, 20, "test:a");
        QuestTrackingPriority sameTickEarlierId = priority(0, "group:main", 1, 10, "test:a");

        assertEquals("test:z", first(newQuest, oldQuest));
        assertEquals("test:a", first(oldQuest, sameTickEarlierId));
    }

    private static QuestTrackingPriority priority(int groupOrder, String groupKey,
                                                  int questOrder, long acceptedAtTick,
                                                  String questId) {
        return new QuestTrackingPriority(groupOrder, groupKey, questOrder, acceptedAtTick, questId);
    }

    private static String first(QuestTrackingPriority... priorities) {
        return List.of(priorities).stream()
                .min(QuestTrackingPriority::compareTo)
                .orElseThrow()
                .questId();
    }
}
