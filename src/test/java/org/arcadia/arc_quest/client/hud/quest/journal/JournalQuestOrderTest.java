package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.QuestBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JournalQuestOrderTest {

    @Test
    void questsAreOrderedByTheirDefinitionSortOrder() {
        List<JournalTypes.QuestListEntry> entries = new ArrayList<>(List.of(
                entry("test:third", 30), entry("test:first", 10), entry("test:second", 20)));

        JournalQuestOrder.sortByDefinition(entries);

        assertEquals(List.of("test:first", "test:second", "test:third"),
                entries.stream().map(JournalTypes.QuestListEntry::questId).toList());
    }

    @Test
    void questIdStabilizesEqualDefinitionOrders() {
        List<JournalTypes.QuestListEntry> entries = new ArrayList<>(List.of(
                entry("test:later", 10), entry("test:earlier", 10)));

        JournalQuestOrder.sortByDefinition(entries);

        assertEquals(List.of("test:earlier", "test:later"),
                entries.stream().map(JournalTypes.QuestListEntry::questId).toList());
    }

    private static JournalTypes.QuestListEntry entry(String id, int sortOrder) {
        QuestDefinition definition = QuestBuilder.create(id)
                .sortOrder(sortOrder)
                .phase(PhaseBuilder.create("start")
                        .objective(ObjectiveBuilder.nullObjective()))
                .build();
        return new JournalTypes.QuestListEntry(
                id, Component.literal(id), QuestState.ACTIVE, definition);
    }
}
