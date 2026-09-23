package org.arcadia.arc_quest.quest.logic;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.QuestCompletionPolicy;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.QuestBuilder;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestProgressRulesTest {
    @Test
    void allWaitsForEveryParallelPhase() {
        QuestDefinition definition = definition(QuestCompletionPolicy.ALL);
        QuestRuntimeData runtime = runtime();
        runtime.completePhase("arc_quest:first");
        assertFalse(QuestProgressRules.shouldCompleteQuest(definition, runtime));
        runtime.completePhase("arc_quest:second");
        assertTrue(QuestProgressRules.shouldCompleteQuest(definition, runtime));
    }

    @Test
    void anyCompletesWhenOneParallelPhaseFinishesWithoutMutatingOtherPhases() {
        QuestDefinition definition = definition(QuestCompletionPolicy.ANY);
        QuestRuntimeData runtime = runtime();
        assertFalse(QuestProgressRules.shouldCompleteQuest(definition, runtime));
        runtime.completePhase("arc_quest:first");
        assertTrue(QuestProgressRules.shouldCompleteQuest(definition, runtime));
        assertEquals(Set.of("arc_quest:second"), runtime.getActivePhaseIds());
    }

    @Test
    void nOfMCountsCompletedPhasesInsteadOfObjectiveProgress() {
        QuestDefinition definition = definition(QuestCompletionPolicy.N_OF_M);
        QuestRuntimeData runtime = runtime();
        runtime.setObjectiveProgress("arc_quest:first", 0, 10);
        runtime.completePhase("arc_quest:second");
        assertFalse(QuestProgressRules.shouldCompleteQuest(definition, runtime));
        runtime.completePhase("arc_quest:first");
        assertTrue(QuestProgressRules.shouldCompleteQuest(definition, runtime));
    }

    @Test
    void specificPhaseIgnoresCompletionOfOtherPhases() {
        QuestDefinition definition = definition(QuestCompletionPolicy.SPECIFIC_PHASE);
        QuestRuntimeData runtime = runtime();
        runtime.completePhase("arc_quest:first");
        assertFalse(QuestProgressRules.shouldCompleteQuest(definition, runtime));
        runtime.completePhase("arc_quest:second");
        assertTrue(QuestProgressRules.shouldCompleteQuest(definition, runtime));
    }

    @Test
    void manualAdvanceAndNbtReloadKeepTheSameCompletionDecision() {
        QuestDefinition definition = definition(QuestCompletionPolicy.ALL);
        QuestRuntimeData runtime = runtime();
        runtime.completePhase("arc_quest:first");
        runtime.markPhasePendingManualAdvance("arc_quest:second");
        assertFalse(QuestProgressRules.shouldCompleteQuest(definition, runtime));
        QuestRuntimeData restored = QuestRuntimeData.deserializeNBT(runtime.serializeNBT());
        assertTrue(restored.isPhasePendingManualAdvance("arc_quest:second"));
        assertFalse(QuestProgressRules.shouldCompleteQuest(definition, restored));
        restored.completePhase("arc_quest:second");
        assertTrue(QuestProgressRules.shouldCompleteQuest(definition, restored));
    }

    @Test
    void abandonedPhaseDoesNotCountAsCompleted() {
        QuestRuntimeData runtime = runtime();
        runtime.completePhase("arc_quest:first");
        runtime.abandonPhase("arc_quest:second");
        assertFalse(QuestProgressRules.shouldCompleteQuest(definition(QuestCompletionPolicy.ALL), runtime));
        assertEquals(Set.of("arc_quest:first"), runtime.getCompletedPhaseIds());
    }

    @ParameterizedTest
    @CsvSource({
            "fixed,        8,  5,  1, -1, 10, 2,  4, 3,  5",
            "player_level, 8,  5,  1, -1, 10, 2,  4, 3, 25",
            "level_scale,  8,  5,  1, -1, 10, 2,  4, 3, 25",
            "player_level, 8,  5,  1, -1, -2, 2,  4, 3,  5",
            "variable,     8,  5,  1, -1, 10, 2,  4, 3, 17",
            "variable,     8,  5,  1, -1, 10, 2, -4, 3,  1",
            "fixed,        8, -5, -2, -1,  0, 0,  0, 0,  1",
            "fixed,        8, 20,  1, 10,  0, 0,  0, 0, 10",
            "fixed,        8, 20, 10,  5,  0, 0,  0, 0, 10",
            "fixed,        8, 20,  1,  0,  0, 0,  0, 0, 20",
            "unknown,      8, 20,  1, -1,  0, 0,  0, 0,  8",
            "unknown,      8, 20,  1,  5,  0, 0,  0, 0,  5"
    })
    void preservesDynamicCountModesAndLimits(String mode, int fallback, int base, int min, int max,
                                            int level, int perLevel, int variable, int perVariable,
                                            int expected) {
        assertEquals(expected, QuestProgressRules.requiredCount(mode, fallback, base, min, max,
                level, perLevel, variable, perVariable));
        assertEquals(expected, QuestProgressHandler.computeRequiredCount(mode, mode, fallback, base, min, max,
                level, perLevel, variable, perVariable, "arc_quest:test"));
    }

    private static QuestDefinition definition(QuestCompletionPolicy policy) {
        QuestBuilder builder = QuestBuilder.create("arc_quest:progress_test")
                .phase(PhaseBuilder.create("arc_quest:first")
                        .objective(ObjectiveBuilder.custom(ResourceLocation.parse("arc_quest:goal"), 10)))
                .phase(PhaseBuilder.create("arc_quest:second")
                        .objective(ObjectiveBuilder.nullObjective()))
                .completionPolicy(policy);
        if (policy == QuestCompletionPolicy.N_OF_M) builder.completionRequiredCount(2);
        if (policy == QuestCompletionPolicy.SPECIFIC_PHASE) builder.completionTargetPhase("arc_quest:second");
        return builder.build();
    }

    private static QuestRuntimeData runtime() {
        QuestRuntimeData runtime = new QuestRuntimeData(
                "arc_quest:progress_test", "arc_quest:first", 1, 0L, 0L, 0L);
        runtime.activatePhase("arc_quest:second", 1);
        return runtime;
    }
}
