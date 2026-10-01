package org.arcadia.arc_quest.integration.jei.quest;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JeiQuestKnowledgeTest {
    @Test
    void keepsOnlyRecordedBranchesAcrossNbtRoundTripAndIsolatesPlayers() {
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        var state = new JeiQuestKnowledge();
        assertTrue(state.remember(player, "test:quest", Set.of("left"), Set.of("reward"), Set.of(), true));
        assertFalse(state.remember(player, "test:quest", Set.of("left"), Set.of("reward"), Set.of(), true));
        assertTrue(state.remember(player, "test:quest", Set.of("end"), Set.of(), Set.of("reward"), true));
        var loaded = JeiQuestKnowledge.load(state.save());
        assertEquals(Set.of("left", "end"), loaded.get(player, "test:quest").phases());
        assertFalse(loaded.get(player, "test:quest").phases().contains("right"));
        assertEquals(Set.of("reward"), loaded.get(player, "test:quest").claimedMilestones());
        assertEquals(JeiQuestKnowledge.KnownQuest.EMPTY, loaded.get(other, "test:quest"));
        assertThrows(UnsupportedOperationException.class, () -> loaded.get(player, "test:quest").phases().add("secret"));
    }

    @Test
    void legacyMissingHistoryNeverInfersPhasesAndResetRevokesKnowledge() {
        UUID player = UUID.randomUUID();
        var state = JeiQuestKnowledge.load(new CompoundTag());
        assertEquals(JeiQuestKnowledge.KnownQuest.EMPTY, state.get(player, "test:old_completed"));
        state.remember(player, "test:reset", Set.of("known"), Set.of(), Set.of(), false);
        state.remember(player, "test:keep", Set.of("known"), Set.of(), Set.of(), false);
        assertTrue(state.retainQuests(player, Set.of("test:keep")));
        assertEquals(JeiQuestKnowledge.KnownQuest.EMPTY, state.get(player, "test:reset"));
        assertEquals(Set.of("known"), state.get(player, "test:keep").phases());
        assertFalse(state.retainQuests(player, Set.of("test:keep")));
    }

    @Test
    void malformedPlayerEntriesCannotGrantKnowledge() {
        CompoundTag root = new CompoundTag();
        CompoundTag players = new CompoundTag();
        players.put("invalid player", new CompoundTag());
        root.put("Players", players);
        assertEquals(JeiQuestKnowledge.KnownQuest.EMPTY, JeiQuestKnowledge.load(root).get(UUID.randomUUID(), "test:quest"));
    }
}
