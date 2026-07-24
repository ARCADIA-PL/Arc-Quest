package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.api.QuestCategory;
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
                           Function<JournalTypes.QuestListEntry, GroupDefinition> groupResolver) {
        Map<GroupDefinition, List<IndexedQuest>> grouped = new LinkedHashMap<>();
        List<IndexedQuest> ungrouped = new ArrayList<>();

        for (int questIndex = 0; questIndex < entries.size(); questIndex++) {
            JournalTypes.QuestListEntry entry = entries.get(questIndex);
            IndexedQuest indexed = new IndexedQuest(entry, questIndex);
            GroupDefinition group = groupResolver.apply(entry);
            if (group == null) {
                ungrouped.add(indexed);
            } else {
                grouped.computeIfAbsent(group, ignored -> new ArrayList<>()).add(indexed);
            }
        }

        List<Map.Entry<GroupDefinition, List<IndexedQuest>>> orderedGroups = new ArrayList<>(grouped.entrySet());
        orderedGroups.sort(Map.Entry.comparingByKey(
                Comparator.comparingInt(GroupDefinition::sortOrder)
                        .thenComparing(group -> group.id().toString())));

        List<Row> rows = new ArrayList<>(entries.size() + orderedGroups.size());
        for (Map.Entry<GroupDefinition, List<IndexedQuest>> groupedEntry : orderedGroups) {
            GroupDefinition group = groupedEntry.getKey();
            List<IndexedQuest> quests = List.copyOf(groupedEntry.getValue());
            rows.add(new GroupRow(group, quests.stream().map(IndexedQuest::entry).toList()));
            for (IndexedQuest quest : quests) {
                rows.add(new QuestRow(quest.entry(), quest.questIndex(), group.id()));
            }
        }
        for (IndexedQuest quest : ungrouped) {
            rows.add(new QuestRow(quest.entry(), quest.questIndex(), null));
        }
        return List.copyOf(rows);
    }

    sealed interface Row permits GroupRow, QuestRow {
    }

    record GroupRow(GroupDefinition group, List<JournalTypes.QuestListEntry> quests) implements Row {
    }

    record QuestRow(JournalTypes.QuestListEntry entry, int questIndex, ResourceLocation groupId) implements Row {

        boolean grouped() {
            return groupId != null;
        }
    }

    private record IndexedQuest(JournalTypes.QuestListEntry entry, int questIndex) {
    }

    record GroupDefinition(ResourceLocation id, Component displayName, int sortOrder, int themeColor) {

        static GroupDefinition explicit(QuestGroupDefinition group) {
            return new GroupDefinition(
                    group.getId(),
                    group.getDisplayName(),
                    group.getSortOrder(),
                    group.getThemeColor()
            );
        }

        static GroupDefinition category(QuestCategory category) {
            ResourceLocation categoryId = category.getId();
            ResourceLocation journalGroupId = ResourceLocation.fromNamespaceAndPath(
                    Arc_Quest.MOD_ID,
                    "journal_category/" + categoryId.getNamespace() + "/" + categoryId.getPath()
            );
            return new GroupDefinition(
                    journalGroupId,
                    category.getDisplayName(),
                    category.getSortOrder(),
                    category.getThemeColor()
            );
        }
    }
}
