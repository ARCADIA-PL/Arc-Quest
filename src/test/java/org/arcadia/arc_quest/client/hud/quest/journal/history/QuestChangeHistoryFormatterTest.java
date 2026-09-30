package org.arcadia.arc_quest.client.hud.quest.journal.history;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.QuestBuilder;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestChangeHistoryFormatterTest {
    @Test void resolvesParallelAndCompletedPhaseThresholdsFromTheExistingSnapshot() {
        var definition = QuestBuilder.create("arc_quest:history_counts")
                .phase(PhaseBuilder.create("first").objective(ObjectiveBuilder.custom(ResourceLocation.parse("arc_quest:goal"), 1)))
                .phase(PhaseBuilder.create("second").objective(ObjectiveBuilder.custom(ResourceLocation.parse("arc_quest:goal"), 2)))
                .build();
        var runtime = new QuestRuntimeData(definition.getId().toString(), "first", 1, 0L, 0L, 0L);
        runtime.activatePhase("second", 1);
        runtime.setRequiredCount("first", 0, 65537);
        runtime.setRequiredCount("second", 0, 42);
        runtime.completePhase("first");
        assertEquals(65537, QuestChangeHistoryFormatter.objectiveRequired(definition, runtime, "first", 0));
        assertEquals(65537, QuestChangeHistoryFormatter.objectiveRequired(null, runtime, "first", 0));
        assertEquals(42, QuestChangeHistoryFormatter.objectiveRequired(definition, runtime, "second", 0));
        assertEquals(1, QuestChangeHistoryFormatter.objectiveRequired(definition, null, "first", 0));
    }

    @Test void absentSnapshotsUseTheDefinitionWithoutRunningServerCallbacks() {
        var definition = QuestBuilder.create("arc_quest:history_fallback")
                .phase(PhaseBuilder.create("phase").objective(ObjectiveBuilder.custom(ResourceLocation.parse("arc_quest:goal"), 7)
                        .countModifier(player -> { throw new AssertionError("History must not run a server callback"); })))
                .build();
        var legacyRuntime = new QuestRuntimeData(definition.getId().toString(), "phase", 1, 0L, 0L, 0L);
        assertEquals(7, QuestChangeHistoryFormatter.objectiveRequired(definition, legacyRuntime, "phase", 0));
        assertEquals(7, QuestChangeHistoryFormatter.objectiveRequired(definition, null, "phase", 0));
        assertEquals(-1, QuestChangeHistoryFormatter.objectiveRequired(definition, legacyRuntime, "unknown", 0));
        assertEquals(-1, QuestChangeHistoryFormatter.objectiveRequired(definition, legacyRuntime, "phase", 2));
        assertEquals(-1, QuestChangeHistoryFormatter.objectiveRequired(null, legacyRuntime, "phase", 0));
    }
}
