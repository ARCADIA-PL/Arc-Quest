package org.arcadia.arc_quest.quest.registry;

public record QuestSourceInfo(
        QuestSourceType sourceType,
        String sourceId,
        int loadOrder,
        String ignoredReason
) {
}
