package org.arcadia.arc_quest.quest.api;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.*;
import org.arcadia.arc_quest.quest.logic.CollectionSheetService;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CollectionSheetSemanticsTest {
    private static final ResourceLocation A = ResourceLocation.parse("example:a");
    private static final ResourceLocation B = ResourceLocation.parse("example:b");
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void recordOnlySheetIsOneRealPhaseRatherThanOnePhasePerEntry() {
        var sheet = CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("a", A).discovered())
                .binding(EntryRequirementBuilder.create("b", B).discovered()).build();
        var quest = quest(sheet);
        assertEquals(1, quest.getPhaseIds().size());
        assertTrue(quest.hasCollectionSheets());
        assertTrue(quest.getPhase("survey").getObjectives().isEmpty());
        assertEquals(2, sheet.getRequiredCount());
    }

    @Test void discoveredKnowledgeDoesNotSatisfyAnUnfinishedRunAction() {
        var entry = entry(A);
        var binding = EntryRequirementBuilder.create("a", A).discovered().objective("action").build();
        var phase = PhaseBuilder.create("survey").objective(ObjectiveBuilder.custom(A, 5).id("action"))
                .collectionSheet(CollectionSheetBuilder.create().binding(binding)).build();
        var quest = quest(phase, List.of(entry));
        var records = new CollectionRecordState(); records.discover(A);
        var run = runtime(quest); run.setObjectiveProgress("survey", 0, 2);
        var progress = CollectionProgressProjector.project(quest, phase, run, records);
        assertTrue(progress.bindings().get(0).discovered());
        assertFalse(progress.complete());
        assertEquals(2, progress.bindings().get(0).requirements().get(0).current());
    }

    @Test void anyBindingRequirementCanAcceptARecordWithoutCompletingRunAction() {
        var phase = PhaseBuilder.create("survey").objective(ObjectiveBuilder.custom(A, 5).id("action"))
                .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("a", A)
                        .objective("action").discovered().requirementMode(CollectionRequirementMode.ANY))).build();
        var quest = quest(phase, List.of(entry(A)));
        var records = new CollectionRecordState(); records.discover(A);
        assertTrue(CollectionProgressProjector.project(quest, phase, runtime(quest), records).complete());
    }

    @Test void quotaDisplaysItsThresholdAndIgnoresOptionalBindings() {
        var sheet = CollectionSheetBuilder.create().quota(1)
                .binding(EntryRequirementBuilder.create("a", A).discovered())
                .binding(EntryRequirementBuilder.create("b", B).discovered())
                .binding(EntryRequirementBuilder.create("extra", A).researched().optional()).build();
        var legacy = CollectionEntryBuilder.create(A).category("mobs").legacyGameplay().build();
        var quest = quest(PhaseBuilder.create("survey").collectionSheet(sheet).build(), List.of(legacy, entry(B)));
        assertFalse(quest.getCollectionConfig().getEntry(A).isUnifiedGameplay(),
                "This fixture explicitly exercises the former researched() compatibility requirement");
        var records = new CollectionRecordState(); records.discover(B);
        var progress = CollectionProgressProjector.project(quest, quest.getPhase("survey"), runtime(quest), records);
        assertEquals(1, progress.target()); assertEquals(2, progress.candidateTotal()); assertTrue(progress.complete());
        assertFalse(progress.binding("extra").complete(), "An unfinished optional legacy requirement cannot raise the quota");
        assertThrows(IllegalArgumentException.class, () -> CollectionSheetBuilder.create().quota(2)
                .binding(EntryRequirementBuilder.create("a", A).discovered())
                .binding(EntryRequirementBuilder.create("b", B).discovered().optional()).build());
    }

    @Test void distinctEntriesMergeRequirementsWithoutDoubleCountingSpecies() {
        var sheet = CollectionSheetBuilder.create().countDistinctEntries(true)
                .binding(EntryRequirementBuilder.create("discovery", A).discovered())
                .binding(EntryRequirementBuilder.create("research", A).researchStep("study"))
                .binding(EntryRequirementBuilder.create("b", B).discovered()).build();
        var research = CollectionEntryBuilder.create(A).category("mobs")
                .research(ObjectiveBuilder.custom(A, 2).id("study")).build();
        var quest = quest(PhaseBuilder.create("survey").collectionSheet(sheet).build(), List.of(research, entry(B)));
        var run = runtime(quest); var records = new CollectionRecordState(); records.discover(A); records.discover(B);
        var before = CollectionProgressProjector.project(quest, quest.getPhase("survey"), run, records);
        assertEquals(2, before.target()); assertEquals(1, before.completed()); assertFalse(before.complete());
        records.increment(A, CollectionProgressProjector.researchKey("study"), 2, 2);
        assertTrue(CollectionProgressProjector.project(quest, quest.getPhase("survey"), run, records).complete());
    }

    @Test void existingRecordsAreAcceptedAndNewDiscoveriesRespectTheAcceptanceBaseline() {
        var sheet = CollectionSheetBuilder.create()
                .binding(EntryRequirementBuilder.create("old", A).discovered())
                .binding(EntryRequirementBuilder.create("new", B).discovered().recordPolicy(CollectionRecordPolicy.NEW_DISCOVERIES)).build();
        var quest = quest(sheet); var records = new CollectionRecordState(); records.discover(A);
        var run = runtime(quest); CollectionSheetService.initialize(quest, run, records);
        assertFalse(CollectionProgressProjector.project(quest, quest.getPhase("survey"), run, records).complete());
        records.discover(B);
        assertTrue(CollectionProgressProjector.project(quest, quest.getPhase("survey"), run, records).complete());
        var laterRun = runtime(quest); CollectionSheetService.initialize(quest, laterRun, records);
        assertFalse(CollectionProgressProjector.project(quest, quest.getPhase("survey"), laterRun, records).complete());
        assertNotEquals(run.getCollectionData().getRunId(), laterRun.getCollectionData().getRunId());
    }

    @Test void newRunResetsActionsAndKeepsPermanentKnowledge() {
        var phase = PhaseBuilder.create("survey").objective(ObjectiveBuilder.custom(A, 2).id("act"))
                .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("a", A).objective("act"))).build();
        var quest = quest(phase, List.of(entry(A))); var records = new CollectionRecordState(); records.discover(A);
        var first = runtime(quest); first.setObjectiveProgress("survey", 0, 2);
        assertTrue(CollectionProgressProjector.project(quest, phase, first, records).complete());
        var next = runtime(quest);
        assertFalse(CollectionProgressProjector.project(quest, phase, next, records).complete());
        assertTrue(records.isDiscovered(A));
    }

    @Test void missingFrozenBindingAfterReloadCannotSilentlyCompleteTheTask() {
        var original = quest(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("a", A).discovered())
                .binding(EntryRequirementBuilder.create("b", B).discovered()).build());
        var records = new CollectionRecordState(); records.discover(A);
        var run = runtime(original); CollectionSheetService.initialize(original, run, records);
        var reloaded = quest(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("a", A).discovered()).build());
        var progress = CollectionProgressProjector.project(reloaded, reloaded.getPhase("survey"), run, records);
        assertEquals(2, progress.target()); assertFalse(progress.complete());
    }

    @Test void terminalRunKeepsItsOutcomeWhenPermanentKnowledgeGrows() {
        var quest = quest(CollectionSheetBuilder.create().quota(1)
                .binding(EntryRequirementBuilder.create("a", A).discovered())
                .binding(EntryRequirementBuilder.create("b", B).discovered()).build());
        var records = new CollectionRecordState(); records.discover(A);
        for (QuestState state : List.of(QuestState.COMPLETED, QuestState.FAILED)) {
            var run = runtime(quest); CollectionSheetService.initialize(quest, run, records);
            run.getCollectionData().markBindingComplete("survey", "a");
            run.setState(state);
            records.discover(B);
            var progress = CollectionProgressProjector.project(quest, quest.getPhase("survey"), run, records);
            assertEquals(1, progress.completed());
            assertTrue(progress.binding("a").complete());
            assertTrue(progress.binding("b").discovered());
            assertFalse(progress.binding("b").complete(), "Later knowledge cannot change the archived run outcome");
        }
    }

    @Test void stableIdsAndReferencesAreValidatedBeforeARunStarts() {
        assertThrows(IllegalArgumentException.class, () -> CollectionSheetBuilder.create()
                .binding(EntryRequirementBuilder.create("same", A).discovered())
                .binding(EntryRequirementBuilder.create("same", B).discovered()).build());
        assertThrows(IllegalArgumentException.class, () -> PhaseBuilder.create("survey")
                .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("a", A).objective("missing"))).build());
        assertThrows(IllegalArgumentException.class, () -> EntryRequirementBuilder.create("a", A)
                .researched().recordPolicy(CollectionRecordPolicy.NEW_DISCOVERIES).build());
        assertThrows(IllegalArgumentException.class, () -> CollectionEntryBuilder.create(A).category("mobs")
                .research(ObjectiveBuilder.custom(A, 1)).build());
    }

    private static CollectionEntryDefinition entry(ResourceLocation id) { return CollectionEntryBuilder.create(id).category("mobs").displayName(id.toString()).build(); }
    private static QuestDefinition quest(CollectionSheetDefinition sheet) { return quest(PhaseBuilder.create("survey").collectionSheet(sheet).build(), List.of(entry(A), entry(B))); }
    private static QuestDefinition quest(PhaseDefinition phase, List<CollectionEntryDefinition> entries) {
        var config = CollectionQuestConfigBuilder.create().category("mobs", "Mobs"); entries.forEach(config::entry);
        return QuestBuilder.create("example:survey").category(QuestCategory.ADVENTURE).mode(QuestMode.COLLECTION)
                .collectionConfig(config.build()).phase(phase).build();
    }
    private static QuestRuntimeData runtime(QuestDefinition quest) { return new QuestRuntimeData(quest.getId().toString(), "survey", quest.getPhase("survey").getObjectives().size(), 0L, 0L, 0L); }
}
