package org.arcadia.arc_quest.quest.tracking.application;

import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackedPhaseFocusServiceTest {

    private final UUID playerId = UUID.randomUUID();

    @AfterEach
    void clearFocus() {
        TrackedPhaseFocusService.clearPlayer(playerId);
    }

    @Test
    void resolvesOnlyExplicitActivePhaseFocus() {
        QuestRuntimeData runtime = new QuestRuntimeData(
                "arc_quest:epic_prologue", "arc_quest:reinforce_gate", 1, 0L, 0L, 0L);
        runtime.activatePhase("arc_quest:scout_forest", 1);
        TrackedPhaseFocusService.prepare(
                playerId, "arc_quest:epic_prologue", "arc_quest:scout_forest");

        assertEquals("arc_quest:scout_forest", TrackedPhaseFocusService.resolve(
                playerId, "arc_quest:epic_prologue", runtime));
    }
}
