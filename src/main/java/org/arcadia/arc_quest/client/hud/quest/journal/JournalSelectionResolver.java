package org.arcadia.arc_quest.client.hud.quest.journal;

import org.jetbrains.annotations.Nullable;
import org.arcadia.arc_quest.quest.api.QuestDefinition;

import java.util.List;
import java.util.Objects;

final class JournalSelectionResolver {

    private JournalSelectionResolver() {
    }

    static Result resolve(List<JournalTypes.QuestListEntry> entries,
                          @Nullable JournalTypes.QuestListEntry previousEntry,
                          @Nullable String fallbackQuestId,
                          boolean preservePreviousSelection) {
        return resolve(entries, previousEntry, fallbackQuestId, preservePreviousSelection, false);
    }

    static Result resolve(List<JournalTypes.QuestListEntry> entries,
                          @Nullable JournalTypes.QuestListEntry previousEntry,
                          @Nullable String fallbackQuestId,
                          boolean preservePreviousSelection, boolean sameContentEpoch) {
        String targetQuestId = preservePreviousSelection && previousEntry != null
                ? previousEntry.questId()
                : fallbackQuestId;
        int selectedIndex = findQuestIndex(entries, targetQuestId);
        if (selectedIndex < 0 && !entries.isEmpty()) selectedIndex = 0;

        JournalTypes.QuestListEntry selectedEntry = selectedIndex >= 0
                ? entries.get(selectedIndex)
                : null;
        boolean contextChanged = !preservePreviousSelection
                || !sameContext(previousEntry, selectedEntry, sameContentEpoch);
        return new Result(selectedIndex, contextChanged);
    }

    private static int findQuestIndex(List<JournalTypes.QuestListEntry> entries,
                                      @Nullable String questId) {
        if (questId == null || questId.isEmpty()) return -1;
        for (int index = 0; index < entries.size(); index++) {
            if (questId.equals(entries.get(index).questId())) return index;
        }
        return -1;
    }

    private static boolean sameContext(@Nullable JournalTypes.QuestListEntry previousEntry,
                                       @Nullable JournalTypes.QuestListEntry selectedEntry, boolean sameContentEpoch) {
        if (previousEntry == null || selectedEntry == null) return previousEntry == selectedEntry;
        return Objects.equals(previousEntry.questId(), selectedEntry.questId())
                && previousEntry.state() == selectedEntry.state()
                && (previousEntry.def() == selectedEntry.def()
                    // An earned/claimed reward replaces the authorized presentation document,
                    // without changing the player's current archive browsing session.
                    || sameContentEpoch && modernCollection(previousEntry.def()) && modernCollection(selectedEntry.def()));
    }

    private static boolean modernCollection(@Nullable QuestDefinition definition) {
        return definition != null && definition.isCollectionQuest() && definition.hasCollectionSheets();
    }

    record Result(int selectedIndex, boolean contextChanged) {
    }
}
