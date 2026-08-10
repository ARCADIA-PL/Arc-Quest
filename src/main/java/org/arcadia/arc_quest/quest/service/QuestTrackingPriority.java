package org.arcadia.arc_quest.quest.service;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.QuestCategory;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestGroupDefinition;
import org.arcadia.arc_quest.quest.registry.QuestGroupRegistry;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

import java.util.Comparator;

public record QuestTrackingPriority(int groupSortOrder,
                                    String groupKey,
                                    int questSortOrder,
                                    long acceptedAtTick,
                                    String questId) implements Comparable<QuestTrackingPriority> {

    private static final Comparator<QuestTrackingPriority> ORDER = Comparator
            .comparingInt(QuestTrackingPriority::groupSortOrder)
            .thenComparing(QuestTrackingPriority::groupKey)
            .thenComparingInt(QuestTrackingPriority::questSortOrder)
            .thenComparingLong(QuestTrackingPriority::acceptedAtTick)
            .thenComparing(QuestTrackingPriority::questId);

    public static QuestTrackingPriority resolve(String questId, long acceptedAtTick) {
        ResourceLocation questKey = ResourceLocation.tryParse(questId);
        QuestDefinition definition = questKey != null ? QuestRegistry.get(questKey) : null;
        QuestGroupDefinition explicitGroup = questKey != null
                ? QuestGroupRegistry.getGroupForQuest(questKey)
                : null;

        if (explicitGroup != null) {
            return new QuestTrackingPriority(
                    explicitGroup.getSortOrder(),
                    "group:" + explicitGroup.getId(),
                    definition != null ? definition.getSortOrder() : Integer.MAX_VALUE,
                    acceptedAtTick,
                    questId
            );
        }

        QuestCategory category = definition != null ? definition.getCategory() : null;
        if (category != null) {
            return new QuestTrackingPriority(
                    category.getSortOrder(),
                    "category:" + category.getId(),
                    definition.getSortOrder(),
                    acceptedAtTick,
                    questId
            );
        }

        return new QuestTrackingPriority(
                Integer.MAX_VALUE,
                "ungrouped",
                definition != null ? definition.getSortOrder() : Integer.MAX_VALUE,
                acceptedAtTick,
                questId
        );
    }

    @Override
    public int compareTo(QuestTrackingPriority other) {
        return ORDER.compare(this, other);
    }
}
