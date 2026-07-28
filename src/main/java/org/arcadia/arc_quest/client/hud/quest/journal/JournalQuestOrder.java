package org.arcadia.arc_quest.client.hud.quest.journal;

import java.util.Comparator;
import java.util.List;

final class JournalQuestOrder {

    private JournalQuestOrder() {
    }

    static void sortByDefinition(List<JournalTypes.QuestListEntry> entries) {
        entries.sort(Comparator
                .comparingInt(JournalQuestOrder::definitionOrder)
                .thenComparing(JournalTypes.QuestListEntry::questId));
    }

    private static int definitionOrder(JournalTypes.QuestListEntry entry) {
        return entry.def() == null ? 0 : entry.def().getSortOrder();
    }
}
