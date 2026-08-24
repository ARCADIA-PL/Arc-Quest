package org.arcadia.arc_quest.client.hud.quest.trackingmenu;

import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.QuestBuilder;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestTrackingMenuPhaseSelectorTest {

    @Test
    void prefersAuthoritativeTrackedPhaseAndCyclesLocally() {
        QuestDefinition definition = QuestBuilder.create("arc_quest:parallel_test")
                .phase(PhaseBuilder.create("arc_quest:first")
                        .objective(ObjectiveBuilder.nullObjective()))
                .phase(PhaseBuilder.create("arc_quest:second")
                        .objective(ObjectiveBuilder.nullObjective()))
                .build();
        QuestRuntimeData runtime = new QuestRuntimeData(
                "arc_quest:parallel_test", "arc_quest:first", 1, 0L, 0L, 0L);
        runtime.activatePhase("arc_quest:second", 1);
        QuestTrackingMenuEntry entry = new QuestTrackingMenuEntry(
                "arc_quest:parallel_test", definition, runtime,
                QuestTrackingMenuEntry.orderedActivePhases(definition, runtime), null);
        QuestTrackingMenuPhaseSelector selector = new QuestTrackingMenuPhaseSelector();

        selector.reconcile(List.of(entry), "arc_quest:parallel_test", "arc_quest:second");
        assertEquals("arc_quest:second", selector.selectedPhase(entry).phaseId());

        selector.cycle(entry, 1);
        assertEquals("arc_quest:first", selector.selectedPhase(entry).phaseId());
    }
}
