package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.QuestBuilder;
import org.arcadia.arc_quest.quest.data.CollectionRuntimeData;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class QuestNoticePolicyTest {
    @BeforeAll static void initialize() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void informationalNullObjectivesDoNotBlockABranch() {
        var definition = quest().phase(branch("choice")
                .objective(ObjectiveBuilder.nullObjective())).build();
        var runtime = runtime("choice", 1);
        assertTrue(QuestNoticePolicy.branchReady(definition, runtime, "choice"));
        assertEquals(List.of(new QuestNoticePolicy.PendingPhase("choice", QuestNoticePolicy.PendingKind.BRANCH_CHOICE)),
                QuestNoticePolicy.pendingPhases(definition, runtime, choice -> true));
    }

    @Test void dynamicRequiredCountDeterminesWhenTheActionBecomesReady() {
        var phase = branch("choice").objective(goal(2)).build();
        var runtime = runtime("choice", 1);
        runtime.setRequiredCount("choice", 0, 10);
        runtime.setObjectiveProgress("choice", 0, 2);
        assertFalse(QuestNoticePolicy.objectivesReady(runtime, phase));
        runtime.setObjectiveProgress("choice", 0, 10);
        assertTrue(QuestNoticePolicy.objectivesReady(runtime, phase));
    }

    @Test void hiddenAndOptionalObjectivesStillMatchServerCompletionRequirements() {
        var phase = branch("choice").objective(goal(2).hidden()).objective(goal(3).optional()).build();
        var runtime = runtime("choice", 2);
        assertFalse(QuestNoticePolicy.objectivesReady(runtime, phase));
        runtime.setObjectiveProgress("choice", 0, 2);
        assertFalse(QuestNoticePolicy.objectivesReady(runtime, phase));
        runtime.setObjectiveProgress("choice", 1, 3);
        assertTrue(QuestNoticePolicy.objectivesReady(runtime, phase));
        assertFalse(QuestNoticePolicy.visibleObjective(phase.getObjectives().get(0)));
        assertTrue(QuestNoticePolicy.visibleObjective(phase.getObjectives().get(1)));
        assertFalse(QuestNoticePolicy.visibleObjective(ObjectiveBuilder.nullObjective().build()));
    }

    @Test void allActionableParallelPhasesAreRetainedInDefinitionOrder() {
        var definition = quest()
                .phase(branch("a").objective(ObjectiveBuilder.nullObjective()))
                .phase(branch("b").objective(ObjectiveBuilder.nullObjective()))
                .phase(PhaseBuilder.create("manual").autoAdvanceOnComplete(false)
                        .objective(ObjectiveBuilder.nullObjective())).build();
        var runtime = runtime("b", 0);
        runtime.activatePhase("a", 0);
        runtime.activatePhase("manual", 0);
        runtime.markPhasePendingManualAdvance("manual");
        var pending = QuestNoticePolicy.pendingPhases(definition, runtime, choice -> true);
        assertEquals(List.of("a", "b", "manual"), pending.stream().map(QuestNoticePolicy.PendingPhase::phaseId).toList());
        assertEquals(QuestNoticePolicy.PendingKind.MANUAL_CONFIRM, pending.get(2).kind());
        runtime.abandonPhase("a");
        assertEquals(List.of("b", "manual"), QuestNoticePolicy.pendingPhases(definition, runtime, choice -> true)
                .stream().map(QuestNoticePolicy.PendingPhase::phaseId).toList());
    }

    @Test void invisibleChoicesAndInactiveOrTerminalPhasesProduceNoPendingActions() {
        var definition = quest().phase(branch("choice").objective(ObjectiveBuilder.nullObjective())).build();
        var runtime = runtime("choice", 0);
        assertTrue(QuestNoticePolicy.pendingPhases(definition, runtime, choice -> false).isEmpty());
        runtime.completePhase("choice");
        assertFalse(QuestNoticePolicy.branchReady(definition, runtime, "choice"));
        runtime.activatePhase("choice", 0);
        for (QuestState state : List.of(QuestState.COMPLETED, QuestState.FAILED)) {
            runtime.setState(state);
            assertFalse(QuestNoticePolicy.branchReady(definition, runtime, "choice"));
            assertTrue(QuestNoticePolicy.pendingPhases(definition, runtime, choice -> true).isEmpty());
        }
    }

    @Test void abandonedPhaseIsNotMistakenForACompletedPhase() {
        var before = runtime("abandoned", 0);
        before.activatePhase("finished", 0);
        var after = before.copy();
        after.abandonPhase("abandoned");
        after.completePhase("finished");
        assertEquals(Set.of("finished"), QuestNoticePolicy.newlyCompletedPhases(before, after));
    }

    @Test void terminalUpdatesSuppressPhaseAndObjectiveNoise() {
        var before = runtime("phase", 0);
        var after = before.copy();
        assertTrue(QuestNoticePolicy.transientUpdatesAllowed(before, after));
        after.setState(QuestState.COMPLETED);
        assertFalse(QuestNoticePolicy.transientUpdatesAllowed(before, after));
        assertFalse(QuestNoticePolicy.transientUpdatesAllowed(null, before));
    }

    @Test void completionDetectsThresholdCrossingsAndDynamicThresholdReductionOnlyOnce() {
        assertTrue(QuestNoticePolicy.completedNow(4, 5, 5, 5));
        assertTrue(QuestNoticePolicy.completedNow(4, 4, 5, 4));
        assertFalse(QuestNoticePolicy.completedNow(5, 6, 5, 5));
        assertFalse(QuestNoticePolicy.completedNow(4, 3, 5, 5));
        assertFalse(QuestNoticePolicy.completedNow(5, 5, 5, 10));
        assertFalse(QuestNoticePolicy.completedNow(0, 1, 1, 0));
    }

    @Test void collectionNotificationsRespectDiscoveryAndMaskedNames() {
        for (HiddenPresentationMode mask : HiddenPresentationMode.values()) {
            var entry = new CollectionEntryConfig("category", VisibilityMode.HIDDEN_BY_DEFAULT, mask,
                    List.of(), CountingMode.BINARY, 1, false, false, 0, EntryRewardGrantMode.AUTO, List.of(), 0, false);
            var definition = QuestBuilder.create("test:notices").phase(PhaseBuilder.create("entry")
                    .collectionEntryConfig(entry).objective(ObjectiveBuilder.nullObjective())).build();
            var runtime = runtime("entry", 0);
            var collection = new CollectionRuntimeData();
            runtime.setCollectionData(collection);
            assertFalse(QuestNoticePolicy.visibleCollectionEntry(definition, runtime, "entry"));
            collection.markVisible("entry");
            assertEquals(mask == HiddenPresentationMode.FULLY_HIDDEN,
                    QuestNoticePolicy.visibleCollectionEntry(definition, runtime, "entry"));
            collection.markDiscovered("entry");
            assertTrue(QuestNoticePolicy.visibleCollectionEntry(definition, runtime, "entry"));
        }
    }

    private static PhaseBuilder branch(String phase) {
        return PhaseBuilder.create(phase).choice(Component.literal("Choose"), "", "target");
    }

    @Test void confirmationReminderOnlyTriggersOnEntryAndNotOnRepeatedStateOrProgressUpdates() {
        var before = runtime("manual", 1);
        var after = before.copy();
        after.markPhasePendingManualAdvance("manual");
        assertEquals(Set.of("manual"), QuestNoticePolicy.newlyPendingConfirmations(before, after));
        assertTrue(QuestNoticePolicy.newlyPendingConfirmations(after, after.copy()).isEmpty());
        var resolved = after.copy();
        resolved.completePhase("manual");
        assertTrue(QuestNoticePolicy.newlyPendingConfirmations(after, resolved).isEmpty());
    }

    @Test void firstLiveQuestStateCanPromptForConfirmationButTerminalStatesCannot() {
        var after = runtime("manual", 1);
        after.markPhasePendingManualAdvance("manual");
        assertEquals(Set.of("manual"), QuestNoticePolicy.newlyPendingConfirmations(null, after));
        after.setState(QuestState.COMPLETED);
        assertTrue(QuestNoticePolicy.newlyPendingConfirmations(null, after).isEmpty());
    }
    private static QuestBuilder quest() {
        return QuestBuilder.create("test:notices")
                .phase(PhaseBuilder.create("target").objective(ObjectiveBuilder.nullObjective()));
    }
    private static ObjectiveBuilder goal(int required) {
        return ObjectiveBuilder.custom(ResourceLocation.parse("test:goal"), required);
    }
    private static QuestRuntimeData runtime(String phase, int objectives) {
        return new QuestRuntimeData("test:notices", phase, objectives, 0, 0, 0);
    }
}
