package org.arcadia.arc_quest.quest.tracking.application;

import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.QuestBuilder;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackedPhaseFocusServiceTest {

    @Test
    void restoresPersistedActivePhaseFocus() {
        ArcQuestPlayer playerData = new ArcQuestPlayer(UUID.randomUUID());
        playerData.setTrackedQuestId("arc_quest:epic_prologue");
        playerData.setTrackedPhaseId("arc_quest:scout_forest");
        QuestRuntimeData runtime = new QuestRuntimeData(
                "arc_quest:epic_prologue", "arc_quest:reinforce_gate", 1, 0L, 0L, 0L);
        runtime.activatePhase("arc_quest:scout_forest", 1);
        QuestDefinition definition = definition();

        assertEquals("arc_quest:scout_forest", TrackedPhaseFocusService.resolve(
                playerData, "arc_quest:epic_prologue", runtime, definition));
    }

    @Test
    void fallsBackUsingDefinitionOrderAndPersistsResult() {
        ArcQuestPlayer playerData = new ArcQuestPlayer(UUID.randomUUID());
        playerData.setTrackedQuestId("arc_quest:epic_prologue");
        playerData.setTrackedPhaseId("arc_quest:missing_phase");
        QuestRuntimeData runtime = new QuestRuntimeData(
                "arc_quest:epic_prologue", "arc_quest:scout_forest", 1, 0L, 0L, 0L);
        runtime.activatePhase("arc_quest:reinforce_gate", 1);
        QuestDefinition definition = definition();

        assertEquals("arc_quest:reinforce_gate", TrackedPhaseFocusService.resolve(
                playerData, "arc_quest:epic_prologue", runtime, definition));
        assertEquals("arc_quest:reinforce_gate", playerData.getTrackedPhaseId());
    }

    private static QuestDefinition definition() {
        return QuestBuilder.create("arc_quest:epic_prologue")
                .phase(PhaseBuilder.create("arc_quest:reinforce_gate")
                        .objective(ObjectiveBuilder.nullObjective()))
                .phase(PhaseBuilder.create("arc_quest:scout_forest")
                        .objective(ObjectiveBuilder.nullObjective()))
                .build();
    }
}
