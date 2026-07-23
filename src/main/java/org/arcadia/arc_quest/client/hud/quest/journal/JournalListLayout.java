package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.QuestGroupDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class JournalListLayout {

    private JournalListLayout() {
    }

    static List<Row> build(List<JournalTypes.QuestListEntry> entries,
                           Function<String, QuestGroupDefinition> groupResolver) {
        Map<QuestGroupDefinition, List<IndexedQuest>> grouped = new LinkedHashMap<>();
        List<IndexedQuest> ungrouped = new ArrayList<>();

        for (int questIndex = 0; questIndex < entries.size(); questIndex++) {
            JournalTypes.QuestListEntry entry = entries.get(questIndex);
            IndexedQuest indexed = new IndexedQuest(entry, questIndex);
            QuestGroupDefinition group = groupResolver.apply(entry.questId());
            if (group == null) {
                ungrouped.add(indexed);
            } else {
                grouped.computeIfAbsent(group, ignored -> new ArrayList<>()).add(indexed);
            }
        }

        List<Map.Entry<QuestGroupDefinition, List<IndexedQuest>>> orderedGroups = new ArrayList<>(grouped.entrySet());
        orderedGroups.sort(Map.Entry.comparingByKey(
                Comparator.comparingInt(QuestGroupDefinition::getSortOrder)
                        .thenComparing(group -> group.getId().toString())));

        List<Row> rows = new ArrayList<>(entries.size() + orderedGroups.size());
        for (Map.Entry<QuestGroupDefinition, List<IndexedQuest>> groupedEntry : orderedGroups) {
            QuestGroupDefinition group = groupedEntry.getKey();
            List<IndexedQuest> quests = List.copyOf(groupedEntry.getValue());
            rows.add(new GroupRow(group, quests.stream().map(IndexedQuest::entry).toList()));
            for (IndexedQuest quest : quests) {
                rows.add(new QuestRow(quest.entry(), quest.questIndex(), group.getId()));
            }
        }
        for (IndexedQuest quest : ungrouped) {
            rows.add(new QuestRow(quest.entry(), quest.questIndex(), null));
        }
        return List.copyOf(rows);
    }

    sealed interface Row permits GroupRow, QuestRow {
    }

    record GroupRow(QuestGroupDefinition group, List<JournalTypes.QuestListEntry> quests) implements Row {
    }

    record QuestRow(JournalTypes.QuestListEntry entry, int questIndex, ResourceLocation groupId) implements Row {

        boolean grouped() {
            return groupId != null;
        }
    }

    private record IndexedQuest(JournalTypes.QuestListEntry entry, int questIndex) {
    }
}
