package org.arcadia.arc_quest.client.quest.tracking;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.*;
import org.arcadia.arc_quest.quest.logic.CollectionSheetService;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
import org.arcadia.arc_quest.quest.registry.CollectionFieldDemos;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CollectionTrackingFocusSelectorTest {
    private static final ResourceLocation A = ResourceLocation.parse("example:a");
    private static final ResourceLocation B = ResourceLocation.parse("example:b");
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void defaultSelectionUsesStableProjectionOrderAndDoesNotChangeAuthoritativeProgress() {
        var quest = quest();
        var runtime = runtime(quest);
        var second = binding("b", B, false, requirement(false));
        var first = binding("a", A, false, requirement(false));
        var sheets = Map.of("field", sheet(false, second, first));
        var before = runtime.serializeNBT().copy();
        var selected = CollectionTrackingFocusSelector.select(quest, runtime, null, null, sheets::get);
        assertNotNull(selected);
        assertEquals("field", selected.phaseId());
        assertEquals("b", selected.bindingId());
        assertEquals(before, runtime.serializeNBT());
    }

    @Test void explicitOtherEntryWinsOverTheFirstUnfinishedEntryAndDuplicateIdsStayInTheirPhase() {
        var quest = quest();
        var runtime = runtime(quest);
        runtime.activatePhase("mine", 0);
        var sheets = Map.of("field", sheet(false, binding("a", A, false, requirement(false)),
                        binding("b", B, false, requirement(false))),
                "mine", sheet(false, binding("a", A, false, requirement(false))));
        var second = CollectionTrackingFocusSelector.select(quest, runtime, "field", "b", sheets::get);
        assertNotNull(second);
        assertEquals("b", second.bindingId());
        var duplicate = CollectionTrackingFocusSelector.select(quest, runtime, "mine", "a", sheets::get);
        assertNotNull(duplicate);
        assertEquals("mine", duplicate.phaseId());
        assertEquals("a", duplicate.bindingId());
    }

    @Test void completedHiddenAndUnrevealedEntriesAreSkipped() {
        var quest = quest();
        var runtime = runtime(quest);
        var hiddenRequirement = new CollectionRequirementProgress("secret", Component.literal("secret"),
                ObjectiveBuilder.nullObjective().hidden().build(), 0, 0, 1, false, false);
        var invisible = new CollectionBindingProgress("invisible", A, false, true, false, false, false,
                List.of(requirement(false)), List.of());
        var unrevealed = new CollectionBindingProgress("unrevealed", A, true, false, false, false, false,
                List.of(requirement(false)), List.of());
        var hidden = binding("secret", A, false, hiddenRequirement);
        var done = binding("done", A, true, requirement(true));
        var visible = binding("b", B, false, requirement(false));
        var projected = sheet(false, done, invisible, unrevealed, hidden, visible);
        var selected = CollectionTrackingFocusSelector.select(quest, runtime, "field", "done",
                phase -> projected);
        assertNotNull(selected);
        assertEquals("b", selected.bindingId());
        for (var excluded : List.of(done, invisible, unrevealed, hidden)) {
            assertFalse(CollectionTrackingFocusSelector.actionable(excluded));
        }
    }

    @Test void completionSelectsTheNextActivePhaseAndPendingConfirmationDoesNotCreateAnOverview() {
        var quest = quest();
        var runtime = runtime(quest);
        runtime.activatePhase("mine", 0);
        var sheets = Map.of("field", sheet(true, binding("a", A, true, requirement(true))),
                "mine", sheet(false, binding("a", A, false, requirement(false))));
        runtime.markPhasePendingManualAdvance("field");
        var selected = CollectionTrackingFocusSelector.select(quest, runtime, "field", "a", sheets::get);
        assertNotNull(selected);
        assertEquals("mine", selected.phaseId());
        runtime.completePhase("mine");
        assertNull(CollectionTrackingFocusSelector.select(quest, runtime, "field", null, sheets::get));
    }

    @Test void terminalRunsAndInactivePhasesNeverAcquireNewFocus() {
        var quest = quest();
        var runtime = runtime(quest);
        var sheets = Map.of("field", CollectionSheetProgress.EMPTY,
                "mine", sheet(false, binding("a", A, false, requirement(false))));
        assertNull(CollectionTrackingFocusSelector.select(quest, runtime, "mine", "a", sheets::get));
        runtime.activatePhase("mine", 0);
        for (QuestState terminal : List.of(QuestState.COMPLETED, QuestState.FAILED)) {
            runtime.setState(terminal);
            assertNull(CollectionTrackingFocusSelector.select(quest, runtime, "mine", "a", sheets::get));
        }
    }

    @Test void runIdentityUsesTheRealUuidAndFallsBackWhileTheSheetHasNotInitialized() {
        var quest = quest();
        var runtime = runtime(quest);
        assertEquals("20:10", CollectionTrackingFocusSelector.runId(runtime));
        runtime.setCollectionData(new CollectionRuntimeData());
        assertEquals("20:10", CollectionTrackingFocusSelector.runId(runtime));
        runtime.getCollectionData().initializeSheet("field", List.of("a"), 1, Set.of());
        assertEquals(runtime.getCollectionData().getRunId(), CollectionTrackingFocusSelector.runId(runtime));
        var repeat = runtime(quest);
        repeat.getOrCreateCollectionData().initializeSheet("field", List.of("a"), 1, Set.of());
        assertNotEquals(CollectionTrackingFocusSelector.runId(runtime), CollectionTrackingFocusSelector.runId(repeat));
    }

    @Test void demoCowAndBoneDiscoveriesAndCoalSubmissionHaveTheSameTrackingEligibility() {
        var quest = CollectionFieldDemos.field(CollectionFieldDemos.entries());
        var runtime = new QuestRuntimeData(quest.getId().toString(), "survey", quest.getPhase("survey").getObjectives().size(), 10, 20, 30);
        var records = new CollectionRecordState();
        CollectionSheetService.initialize(quest, runtime, records);
        var sheet = CollectionProgressProjector.project(quest, quest.getPhase("survey"), runtime, records);
        var before = runtime.serializeNBT().copy();
        for (String id : List.of("cow", "coal", "bone")) {
            var binding = sheet.binding(id);
            assertNotNull(binding);
            if (id.equals("coal")) {
                var sample = binding.requirements().stream().filter(row -> row.objective() != null).findFirst().orElseThrow();
                assertEquals(ObjectiveType.OFFER, sample.objective().getType());
                assertEquals(0, sample.current()); assertEquals(5, sample.target());
                assertTrue(binding.requirements().stream().filter(row -> row.objective() == null)
                        .allMatch(row -> row.requirementId().equals("record:DISCOVERED:")),
                        "Unified investigation only permits permanent discovery, never independent research counters");
            } else {
                assertTrue(binding.requirements().stream().allMatch(row -> row.objective() == null), id + " is a record requirement");
            }
            assertTrue(CollectionTrackingFocusSelector.canTrack(quest, runtime, "survey", id, phase -> sheet), id);
            assertEquals(id, CollectionTrackingFocusSelector.selectRequested(quest, runtime, "survey", id, phase -> sheet).bindingId());
        }
        assertTrue(CollectionTrackingFocusSelector.canTrack(quest, runtime, "survey", "logs", phase -> sheet), "Run submission remains trackable");
        assertEquals(before, runtime.serializeNBT(), "Tracking never manufactures run objectives or progress");
    }

    @Test void completedDiscoveriesAreExcludedAndEachCoalInvestigationStartsWithFreshSubmissionProgress() {
        var quest = CollectionFieldDemos.field(CollectionFieldDemos.entries());
        var records = new CollectionRecordState();
        records.discover(CollectionFieldDemos.COW); records.discover(CollectionFieldDemos.BONE); records.discover(CollectionFieldDemos.COAL);
        records.increment(CollectionFieldDemos.COAL, CollectionProgressProjector.researchKey("fuel_samples"), 3, 5);
        for (int round = 0; round < 2; round++) {
            var runtime = new QuestRuntimeData(quest.getId().toString(), "survey", quest.getPhase("survey").getObjectives().size(), round, round, 30);
            CollectionSheetService.initialize(quest, runtime, records);
            var sheet = CollectionProgressProjector.project(quest, quest.getPhase("survey"), runtime, records);
            assertFalse(CollectionTrackingFocusSelector.canTrack(quest, runtime, "survey", "cow", phase -> sheet));
            assertFalse(CollectionTrackingFocusSelector.canTrack(quest, runtime, "survey", "bone", phase -> sheet));
            assertTrue(CollectionTrackingFocusSelector.canTrack(quest, runtime, "survey", "coal", phase -> sheet));
            var coal = sheet.binding("coal").requirements().get(0);
            assertNotNull(coal.objective()); assertEquals(ObjectiveType.OFFER, coal.objective().getType());
            assertEquals(0, coal.current()); assertEquals(5, coal.target());
            assertEquals(3, records.getProgress(CollectionFieldDemos.COAL, CollectionProgressProjector.researchKey("fuel_samples")),
                    "Existing lifetime data is preserved but never becomes a fresh run's submission");
            var logs = sheet.binding("logs").requirements().get(0);
            assertNotNull(logs.objective()); assertEquals(0, logs.current()); assertEquals(8, logs.target());
            assertNull(CollectionTrackingFocusSelector.selectRequested(quest, runtime, "survey", "cow", phase -> sheet),
                    "An explicit completed record must not silently focus a different entry");
            runtime.setObjectiveProgress("survey", coal.objectiveIndex(), 3);
            assertEquals(3, CollectionProgressProjector.project(quest, quest.getPhase("survey"), runtime, records)
                    .binding("coal").requirements().get(0).current(), "This run can accumulate its own real submissions");
        }
    }

    @Test void exactEligibilityPreservesHiddenQuotaPendingAndInactivePhaseRules() {
        var quest = quest(); var runtime = runtime(quest);
        var a = binding("a", A, false, requirement(false));
        var projected = sheet(false, a);
        assertTrue(CollectionTrackingFocusSelector.canTrack(quest, runtime, "field", "a", phase -> projected));
        assertFalse(CollectionTrackingFocusSelector.canTrack(quest, runtime, "mine", "a", phase -> projected));
        assertFalse(CollectionTrackingFocusSelector.canTrack(quest, runtime, "field", "missing", phase -> projected));
        assertFalse(CollectionTrackingFocusSelector.canTrack(quest, runtime, null, "a", phase -> projected));
        assertFalse(CollectionTrackingFocusSelector.canTrack(quest, runtime, "field", "a", phase -> sheet(true, a)),
                "An already satisfied sheet keeps the existing selection rules");
        runtime.markPhasePendingManualAdvance("field");
        assertFalse(CollectionTrackingFocusSelector.canTrack(quest, runtime, "field", "a", phase -> projected));
        runtime.clearPhasePendingManualAdvance("field");
        var unrevealed = new CollectionBindingProgress("a", A, true, false, false, false, false,
                List.of(requirement(false)), List.of());
        assertFalse(CollectionTrackingFocusSelector.canTrack(quest, runtime, "field", "a", phase -> sheet(false, unrevealed)));
        runtime.setState(QuestState.COMPLETED);
        assertFalse(CollectionTrackingFocusSelector.canTrack(quest, runtime, "field", "a", phase -> projected));
    }

    @Test void anonymousPublicClueIsTrackableWithoutManufacturingAnObjectiveOrRevealingItsIdentity() {
        var quest = quest(); var runtime = runtime(quest);
        var clue = new CollectionBindingProgress("a", A, true, false, false, false, false,
                List.of(), List.of(), List.of(), Component.literal("Survey the night."));
        var sheet = sheet(false, clue);
        assertTrue(CollectionTrackingFocusSelector.actionable(clue));
        assertEquals("a", CollectionTrackingFocusSelector.selectRequested(quest, runtime, "field", "a", ignored -> sheet).bindingId());
        assertTrue(clue.requirements().isEmpty()); assertFalse(clue.revealed());
        assertFalse(CollectionTrackingFocusSelector.actionable(new CollectionBindingProgress("a", A, false, false,
                false, false, false, List.of(), List.of(), List.of(), clue.publicClue())));
        assertFalse(CollectionTrackingFocusSelector.actionable(new CollectionBindingProgress("a", A, true, false,
                false, false, false, List.of(), List.of())));
    }

    private static CollectionRequirementProgress requirement(boolean done) {
        return new CollectionRequirementProgress("discovered", Component.literal("Discover"), null, -1,
                done ? 1 : 0, 1, done, false);
    }

    private static CollectionBindingProgress binding(String id, ResourceLocation entry, boolean done,
                                                      CollectionRequirementProgress... requirements) {
        return new CollectionBindingProgress(id, entry, true, true, done, false, done,
                List.of(requirements), List.of());
    }

    private static CollectionSheetProgress sheet(boolean done, CollectionBindingProgress... bindings) {
        return new CollectionSheetProgress(done ? bindings.length : 0, bindings.length, bindings.length, done,
                List.of(bindings), List.of());
    }

    private static QuestDefinition quest() {
        var entries = CollectionQuestConfigBuilder.create().category("materials", "Materials")
                .entry(CollectionEntryBuilder.create(A).category("materials").displayName("A"))
                .entry(CollectionEntryBuilder.create(B).category("materials").displayName("B")).build();
        return QuestBuilder.create("example:tracking").mode(QuestMode.COLLECTION).collectionConfig(entries)
                .phase(PhaseBuilder.create("field").collectionSheet(CollectionSheetBuilder.create()
                        .binding(EntryRequirementBuilder.create("a", A).discovered())
                        .binding(EntryRequirementBuilder.create("b", B).discovered())))
                .phase(PhaseBuilder.create("mine").collectionSheet(CollectionSheetBuilder.create()
                        .binding(EntryRequirementBuilder.create("a", A).discovered())))
                .build();
    }

    private static QuestRuntimeData runtime(QuestDefinition quest) {
        return new QuestRuntimeData(quest.getId().toString(), "field", 0, 10, 20, 30);
    }
}
