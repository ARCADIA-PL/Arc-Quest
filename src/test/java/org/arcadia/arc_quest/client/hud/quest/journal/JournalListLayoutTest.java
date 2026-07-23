package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.QuestGroupDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class JournalListLayoutTest {

    @Test
    void groupsAreExpandedByDefaultAndUngroupedQuestsRemainVisible() {
        QuestGroupDefinition later = group("later", 20);
        QuestGroupDefinition earlier = group("earlier", 10);
        Map<String, QuestGroupDefinition> assignments = Map.of(
                "arc_quest:quest_a", later,
                "arc_quest:quest_b", earlier,
                "arc_quest:quest_c", later);

        List<JournalListLayout.Row> rows = JournalListLayout.build(
                entries(), assignments::get);

        assertEquals(6, rows.size());
        assertEquals(earlier, assertInstanceOf(JournalListLayout.GroupRow.class, rows.get(0)).group());
        assertEquals("arc_quest:quest_b",
                assertInstanceOf(JournalListLayout.QuestRow.class, rows.get(1)).entry().questId());
        assertEquals(later, assertInstanceOf(JournalListLayout.GroupRow.class, rows.get(2)).group());
        assertEquals("arc_quest:ungrouped",
                assertInstanceOf(JournalListLayout.QuestRow.class, rows.get(5)).entry().questId());
    }

    @Test
    void questRowsRetainTheirGroupIdentityForAnimatedCollapse() {
        QuestGroupDefinition group = group("collapsed", 0);
        Map<String, QuestGroupDefinition> assignments = Map.of(
                "arc_quest:quest_a", group,
                "arc_quest:quest_c", group);

        List<JournalListLayout.Row> rows = JournalListLayout.build(
                entries(), assignments::get);

        assertEquals(5, rows.size());
        assertInstanceOf(JournalListLayout.GroupRow.class, rows.get(0));
        JournalListLayout.QuestRow firstGrouped = assertInstanceOf(JournalListLayout.QuestRow.class, rows.get(1));
        assertEquals(group.getId(), firstGrouped.groupId());
        assertEquals("arc_quest:quest_a", firstGrouped.entry().questId());
        assertEquals("arc_quest:quest_b",
                assertInstanceOf(JournalListLayout.QuestRow.class, rows.get(3)).entry().questId());
        assertEquals("arc_quest:ungrouped",
                assertInstanceOf(JournalListLayout.QuestRow.class, rows.get(4)).entry().questId());
    }

    private static List<JournalTypes.QuestListEntry> entries() {
        return List.of(
                entry("arc_quest:quest_a"),
                entry("arc_quest:quest_b"),
                entry("arc_quest:quest_c"),
                entry("arc_quest:ungrouped"));
    }

    private static JournalTypes.QuestListEntry entry(String questId) {
        return new JournalTypes.QuestListEntry(questId, questId, QuestState.ACTIVE, null);
    }

    private static QuestGroupDefinition group(String path, int sortOrder) {
        return new QuestGroupDefinition(
                ResourceLocation.fromNamespaceAndPath("arc_quest", path),
                Component.literal(path), sortOrder, 0xFFFFFFFF);
    }
}
