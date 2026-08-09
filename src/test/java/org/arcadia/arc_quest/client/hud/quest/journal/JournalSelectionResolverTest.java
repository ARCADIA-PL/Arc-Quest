package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JournalSelectionResolverTest {

    @Test
    void tabSwitchDoesNotReusePreviousEntryIndex() {
        JournalTypes.QuestListEntry active = entry("test:active", QuestState.ACTIVE);
        List<JournalTypes.QuestListEntry> completed = List.of(
                entry("test:completed", QuestState.COMPLETED));

        JournalSelectionResolver.Result result = JournalSelectionResolver.resolve(
                completed, active, null, false);

        assertEquals(0, result.selectedIndex());
        assertTrue(result.contextChanged());
    }

    @Test
    void survivingSelectionRefreshesRowsWithoutResettingDetail() {
        JournalTypes.QuestListEntry previous = entry("test:active", QuestState.ACTIVE);
        List<JournalTypes.QuestListEntry> refreshed = List.of(
                entry("test:other", QuestState.ACTIVE), previous);

        JournalSelectionResolver.Result result = JournalSelectionResolver.resolve(
                refreshed, previous, null, true);

        assertEquals(1, result.selectedIndex());
        assertFalse(result.contextChanged());
    }

    @Test
    void stateChangeInvalidatesDetailContext() {
        JournalTypes.QuestListEntry previous = entry("test:quest", QuestState.ACTIVE);
        List<JournalTypes.QuestListEntry> completed = List.of(
                entry("test:quest", QuestState.COMPLETED));

        JournalSelectionResolver.Result result = JournalSelectionResolver.resolve(
                completed, previous, null, true);

        assertEquals(0, result.selectedIndex());
        assertTrue(result.contextChanged());
    }

    @Test
    void removedSelectionFallsBackToFirstCurrentEntry() {
        JournalTypes.QuestListEntry previous = entry("test:removed", QuestState.ACTIVE);
        List<JournalTypes.QuestListEntry> active = List.of(
                entry("test:first", QuestState.ACTIVE),
                entry("test:second", QuestState.ACTIVE));

        JournalSelectionResolver.Result result = JournalSelectionResolver.resolve(
                active, previous, null, true);

        assertEquals(0, result.selectedIndex());
        assertTrue(result.contextChanged());
    }

    private static JournalTypes.QuestListEntry entry(String questId, QuestState state) {
        return new JournalTypes.QuestListEntry(
                questId, Component.literal(questId), state, null);
    }
}
