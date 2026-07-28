package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JournalQuestOrderTest {

    @Test
    void activeQuestsAreOrderedByTheTimeTheyWereAccepted() {
        Map<String, QuestRuntimeData> runtimes = new LinkedHashMap<>();
        runtimes.put("test:third", runtime("test:third", 30L, 3_000L));
        runtimes.put("test:first", runtime("test:first", 10L, 1_000L));
        runtimes.put("test:second", runtime("test:second", 20L, 2_000L));

        List<JournalTypes.QuestListEntry> entries = new ArrayList<>(List.of(
                entry("test:third"), entry("test:first"), entry("test:second")));

        JournalQuestOrder.sortByAcquisition(entries, runtimes::get);

        assertEquals(List.of("test:first", "test:second", "test:third"),
                entries.stream().map(JournalTypes.QuestListEntry::questId).toList());
    }

    @Test
    void acceptanceTickStabilizesQuestsAcceptedInTheSameMillisecond() {
        Map<String, QuestRuntimeData> runtimes = Map.of(
                "test:later", runtime("test:later", 12L, 1_000L),
                "test:earlier", runtime("test:earlier", 11L, 1_000L));
        List<JournalTypes.QuestListEntry> entries = new ArrayList<>(List.of(
                entry("test:later"), entry("test:earlier")));

        JournalQuestOrder.sortByAcquisition(entries, runtimes::get);

        assertEquals(List.of("test:earlier", "test:later"),
                entries.stream().map(JournalTypes.QuestListEntry::questId).toList());
    }

    private static QuestRuntimeData runtime(String id, long tick, long realTime) {
        return new QuestRuntimeData(id, "start", 0, tick, realTime, 0L);
    }

    private static JournalTypes.QuestListEntry entry(String id) {
        return new JournalTypes.QuestListEntry(
                id, Component.literal(id), QuestState.ACTIVE, null);
    }
}
