package org.arcadia.arc_quest.quest.logic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.*;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
import org.arcadia.arc_quest.quest.reward.ItemReward;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UnifiedCollectionOutcomeTest {
    private static final ResourceLocation ENTRY = ResourceLocation.parse("test:sample");
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }

    private static CollectionEntryDefinition entry() {
        return CollectionEntryBuilder.create(ENTRY).category("field").outcome("samples", "Sample record")
                .outcomeReward("samples", "first", new ItemReward(Items.IRON_NUGGET, 3)).build();
    }
    private static EntryRequirementBinding binding() {
        return EntryRequirementBuilder.create("sample", ENTRY).objective("work").recordOutcome("samples")
                .reward("payment", new ItemReward(Items.EMERALD, 1)).build();
    }
    private static CollectionRuntimeData run() {
        var result = new CollectionRuntimeData(); result.initializeSheet("survey", List.of("sample"), 1, Set.of()); return result;
    }

    @Test void discoveryAndOldCompletionLatchDoNotInventOutcome() {
        var records = new CollectionRecordState(); records.discover(ENTRY);
        var run = run(); run.markBindingComplete("survey", "sample");
        assertFalse(CollectionOutcomeService.recordCompletion(records, run, entry(), binding(), "test:quest", "survey"));
        assertFalse(records.hasOutcome(ENTRY, "samples"));
        assertFalse(CollectionProgressProjector.researchComplete(entry(), records));
    }

    @Test void completionRecordsExactlyOneFactAndSurvivesReloadWithoutReplay() {
        var records = new CollectionRecordState(); records.discover(ENTRY);
        var run = run(); run.markBindingComplete("survey", "sample"); run.markOutcomePending("survey", "sample", 0);
        assertTrue(CollectionOutcomeService.recordCompletion(records, run, entry(), binding(), "test:quest", "survey"));
        assertTrue(records.hasOutcome(ENTRY, "samples"));
        assertTrue(records.getRecord(ENTRY).getOutcomeSource("samples").contains(run.getRunId()));
        var restored = new CollectionRecordState(); restored.readSnapshot(records.serializeNBT());
        assertEquals(Set.of("samples"), restored.getRecord(ENTRY).getOutcomeIds());
        var restoredRun = CollectionRuntimeData.deserializeNBT(run.serializeNBT());
        assertFalse(CollectionOutcomeService.recordCompletion(restored, restoredRun, entry(), binding(), "test:quest", "survey"));
    }

    @Test void resetRejectsPendingOldGenerationAndAllowsNewActualCompletion() {
        var records = new CollectionRecordState(); records.discover(ENTRY);
        var old = run(); old.markBindingComplete("survey", "sample"); old.markOutcomePending("survey", "sample", 0);
        records.resetQuest("test:quest", Set.of(ENTRY)); records.discover(ENTRY);
        assertFalse(CollectionOutcomeService.recordCompletion(records, old, entry(), binding(), "test:other", "survey"));
        assertFalse(records.hasOutcome(ENTRY, "samples"));
        var next = run(); next.markBindingComplete("survey", "sample");
        next.markOutcomePending("survey", "sample", records.getGeneration(ENTRY));
        assertTrue(CollectionOutcomeService.recordCompletion(records, next, entry(), binding(), "test:quest", "survey"));
        assertFalse(CollectionOutcomeService.recordCompletion(records, old, entry(), binding(), "test:other", "survey"));
    }

    @Test void emptyOutcomesDoNotMeanInvestigationComplete() {
        var plain = CollectionEntryBuilder.create(ENTRY).category("field").build();
        var records = new CollectionRecordState(); records.discover(ENTRY);
        assertFalse(CollectionProgressProjector.researchComplete(plain, records));
    }

    @Test void migrationUsesOriginalThresholdAndNeverImportsAfterExplicitReset() {
        var definition = CollectionEntryBuilder.create(ENTRY).category("field").outcome("samples", "Samples")
                .migrateResearchStep(ObjectiveBuilder.collect(Items.COAL, 5).id("old_samples").build(), "samples").build();
        var records = new CollectionRecordState(); records.discover(ENTRY);
        records.increment(ENTRY, "research:old_samples", 2, 5);
        assertFalse(CollectionOutcomeMigration.migrate(records, definition));
        records.increment(ENTRY, "research:old_samples", 3, 5);
        assertTrue(CollectionOutcomeMigration.migrate(records, definition));
        records.resetQuest("test:quest", Set.of(ENTRY)); records.discover(ENTRY);
        records.importProgress(ENTRY, "research:old_samples", 5, 5);
        assertFalse(CollectionOutcomeMigration.migrate(records, definition));
        assertFalse(records.hasOutcome(ENTRY, "samples"));
    }

    @Test void resetAllPersistsInventorySuppressionForFutureRegisteredEntries() {
        var records = new CollectionRecordState(); records.discover(ENTRY);
        records.resetAll(Set.of(ENTRY));
        var restored = new CollectionRecordState(); restored.readSnapshot(records.serializeNBT());
        assertFalse(restored.isDiscovered(ENTRY));
        assertTrue(restored.isEntryReset(ResourceLocation.parse("test:new_content_after_reset")));
        assertEquals(1, restored.getGeneration(ENTRY));
        assertTrue(restored.discover(ENTRY), "Only inventory bootstrap is blocked, real new discovery is allowed");
    }

    @Test void firstRewardAndBindingPaymentUseDifferentEligibilityAndReceipts() {
        var entry = entry(); var binding = binding();
        var phase = PhaseBuilder.create("survey").objective(ObjectiveBuilder.custom(ENTRY, 1).id("work"))
                .collectionSheet(CollectionSheetBuilder.create().binding(binding)).build();
        var quest = QuestBuilder.create("test:quest").mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field").entry(entry).build()).phase(phase).build();
        var records = new CollectionRecordState(); records.discover(ENTRY);
        var runtime = new QuestRuntimeData("test:quest", "survey", 1, 0, 0, 0);
        CollectionSheetService.initialize(quest, runtime, records);
        var before = CollectionEntryRewardService.project(quest, phase, runtime, records, binding);
        assertEquals(2, before.size()); assertTrue(before.stream().noneMatch(CollectionEntryRewardProgress::canClaim));
        records.recordOutcome(ENTRY, "samples", "test:quest/first", 0);
        var after = CollectionEntryRewardService.project(quest, phase, runtime, records, binding);
        assertTrue(after.get(0).canClaim()); assertFalse(after.get(1).canClaim());
        records.unlockReward(ENTRY, "first"); records.claimReward(ENTRY, "first");
        runtime.getCollectionData().markBindingComplete("survey", "sample");
        runtime.getCollectionData().unlockEntryReward("survey", "sample", "payment");
        var paid = CollectionEntryRewardService.project(quest, phase, runtime, records, binding);
        assertTrue(paid.get(0).claimed()); assertTrue(paid.get(1).canClaim());
        assertEquals(runtime.getCollectionData().getRunId(), paid.get(1).sourceRunId());
    }
}
