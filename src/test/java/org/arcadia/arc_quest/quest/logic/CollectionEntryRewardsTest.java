package org.arcadia.arc_quest.quest.logic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.data.sync.CollectionDefinitionSpecExporter;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.*;
import org.arcadia.arc_quest.quest.reward.ItemReward;
import org.arcadia.arc_quest.quest.registry.CollectionEntryRegistry;
import org.arcadia.arc_quest.quest.registry.CollectionFieldDemos;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
import org.arcadia.arc_quest.data.sync.CollectionContentDisclosure;
import org.arcadia.arc_quest.data.sync.DatapackContentSnapshot;
import org.arcadia.arc_quest.data.sync.DatapackContentModule;
import org.arcadia.arc_quest.data.sync.DatapackContentSyncService;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonWriter;
import org.arcadia.arc_quest.quest.spec.validate.QuestSpecValidator;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CollectionEntryRewardsTest {
    private static final ResourceLocation ENTRY = ResourceLocation.parse("example:entry_reward_test");
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void lifetimeReceiptsAreCopiedPersistedAndSeparatedByEntry() {
        var records = new CollectionRecordState(); var other = ResourceLocation.parse("example:other_reward_entry");
        records.discover(ENTRY); assertTrue(records.unlockReward(ENTRY, "first")); assertTrue(records.claimReward(ENTRY, "first"));
        assertFalse(records.claimReward(ENTRY, "first")); assertFalse(records.isRewardClaimed(other, "first"));
        var restored = new CollectionRecordState(); restored.readSnapshot(records.serializeNBT());
        assertTrue(restored.isRewardUnlocked(ENTRY, "first")); assertTrue(restored.isRewardClaimed(ENTRY, "first"));
        assertFalse(restored.claimReward(ENTRY, "first"));
        var root = restored.serializeEntries(java.util.Set.of(ENTRY));
        assertTrue(CollectionEntryRecord.deserializeNBT(root.getCompound("Entries").getCompound(ENTRY.toString())).copy().isRewardClaimed("first"));
    }

    @Test void runReceiptsAreScopedByPhaseBindingRewardAndNewRunAndSurviveArchival() {
        var run = new CollectionRuntimeData(); run.initializeSheet("a", java.util.List.of("one", "two"), 1, java.util.Set.of());
        run.initializeSheet("b", java.util.List.of("one"), 1, java.util.Set.of());
        assertFalse(run.claimEntryReward("a", "one", "reward"));
        assertTrue(run.unlockEntryReward("a", "one", "reward")); assertTrue(run.claimEntryReward("a", "one", "reward"));
        assertFalse(run.claimEntryReward("a", "one", "reward"));
        assertFalse(run.isEntryRewardClaimed("a", "two", "reward")); assertFalse(run.isEntryRewardClaimed("b", "one", "reward"));
        assertFalse(run.unlockEntryReward("a", "undeclared", "reward"));
        assertTrue(CollectionRuntimeData.deserializeNBT(run.serializeNBT()).copy().isEntryRewardClaimed("a", "one", "reward"));
        var next = new CollectionRuntimeData(); next.initializeSheet("a", java.util.List.of("one"), 1, java.util.Set.of());
        assertNotEquals(run.getRunId(), next.getRunId()); assertFalse(next.isEntryRewardClaimed("a", "one", "reward"));
    }

    @Test void jsonAndJavaExportPreserveEachTriggerAndManualDefault() {
        var entry = CollectionEntryBuilder.create(ENTRY).category("field")
                .discoveryReward("discovery", new ItemReward(Items.COAL, 1))
                .researchReward("research", new ItemReward(Items.EMERALD, 1))
                .reward("investigation", CollectionEntryRewardTrigger.BINDING_COMPLETE, EntryRewardGrantMode.AUTO, new ItemReward(Items.IRON_NUGGET, 2)).build();
        var quest = QuestBuilder.create("example:entry_rewards").mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field").entry(entry).build())
                .phase(PhaseBuilder.create("survey").collectionSheet(CollectionSheetBuilder.create()
                        .binding(EntryRequirementBuilder.create("entry", ENTRY).discovered()))).build();
        var spec = CollectionDefinitionSpecExporter.quest(quest, null);
        assertFalse(new QuestSpecValidator().validate(spec).hasErrors());
        var roundTrip = QuestSpecJsonReader.read(QuestSpecJsonWriter.write(spec));
        var result = new QuestSpecCompiler().compile(roundTrip).getCollectionConfig().getEntry(ENTRY);
        assertEquals(3, result.getRewards().size());
        assertEquals(EntryRewardGrantMode.MANUAL, result.getRewards().get(0).grantMode());
        assertEquals(CollectionEntryRewardTrigger.RESEARCH_COMPLETE, result.getRewards().get(1).trigger());
        assertEquals(EntryRewardGrantMode.AUTO, result.getRewards().get(2).grantMode());
        assertTrue(CollectionEntryRegistry.equivalent(entry, result));
        roundTrip.collectionConfig.entries.get(0).rewards.get(1).rewardId = "discovery";
        assertTrue(new QuestSpecValidator().validate(roundTrip).hasErrors());
        assertThrows(IllegalArgumentException.class, () -> CollectionEntryBuilder.create(ENTRY).category("field")
                .discoveryReward("same", new ItemReward(Items.COAL, 1)).bindingReward("same", new ItemReward(Items.COAL, 1)).build());
    }

    @Test void projectionSeparatesPermanentEligibilityFromUnfinishedInvestigation() {
        var entry = CollectionEntryBuilder.create(ENTRY).category("field")
                .discoveryReward("first", new ItemReward(Items.COAL, 1)).bindingReward("run", new ItemReward(Items.IRON_NUGGET, 1)).build();
        var phase = PhaseBuilder.create("survey").objective(ObjectiveBuilder.custom(ENTRY, 2).id("action"))
                .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("entry", ENTRY).objective("action"))).build();
        var quest = QuestBuilder.create("example:reward_projection").mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field").entry(entry).build()).phase(phase).build();
        var records = new CollectionRecordState(); records.discover(ENTRY);
        var runtime = new QuestRuntimeData(quest.getId().toString(), "survey", 1, 0, 0, 0); CollectionSheetService.initialize(quest, runtime, records);
        var progress = CollectionEntryRewardService.project(quest, phase, runtime, records, phase.getCollectionSheet().getBinding("entry"));
        assertTrue(progress.get(0).canClaim()); assertFalse(progress.get(1).unlocked());
        assertFalse(runtime.getCollectionData().isEntryRewardUnlocked("survey", "entry", "run"));
        assertFalse(records.isRewardUnlocked(ENTRY, "first"), "Projection must not mutate receipts");
    }

    @Test void olderUnpaidRunsSurviveFurtherArchivesCopyAndReloadAndProjectTheirExactClaimSource() {
        var entry = CollectionEntryBuilder.create(ENTRY).category("field")
                .bindingReward("run", new ItemReward(Items.IRON_NUGGET, 1)).build();
        var phase = PhaseBuilder.create("survey").objective(ObjectiveBuilder.custom(ENTRY, 1).id("action"))
                .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("entry", ENTRY).objective("action"))).build();
        var quest = QuestBuilder.create("example:unpaid_runs").mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field").entry(entry).build()).phase(phase).build();
        var records = new CollectionRecordState(); records.discover(ENTRY);
        var first = new QuestRuntimeData(quest.getId().toString(), "survey", 1, 0, 0, 0);
        var second = new QuestRuntimeData(quest.getId().toString(), "survey", 1, 0, 0, 0);
        var third = new QuestRuntimeData(quest.getId().toString(), "survey", 1, 0, 0, 0);
        var archives = new CollectionQuestArchives();
        for (var run : java.util.List.of(first, second, third)) {
            CollectionSheetService.initialize(quest, run, records);
            if (run != third) {
                run.getCollectionData().markBindingComplete("survey", "entry");
                run.getCollectionData().unlockEntryReward("survey", "entry", "run");
            }
            run.setState(QuestState.COMPLETED); archives.capture(run);
        }
        String firstId = first.getCollectionData().getRunId(), secondId = second.getCollectionData().getRunId();
        assertEquals(third.getCollectionData().getRunId(), archives.get(quest.getId().toString()).getCollectionData().getRunId());
        assertEquals(2, archives.pendingRuns(quest.getId().toString()).size());
        var root = new CompoundTag(); archives.writeToRoot(root);
        var restored = new CollectionQuestArchives(); restored.readFromRoot(root);
        var copied = new CollectionQuestArchives(); copied.copyFrom(restored);
        assertEquals(3, copied.allRuns(quest.getId().toString()).size());
        var rows = org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector
                .project(quest, phase, third, records, copied).binding("entry").entryRewards();
        assertEquals(java.util.Set.of(firstId, secondId), rows.stream().filter(CollectionEntryRewardProgress::canClaim)
                .map(CollectionEntryRewardProgress::sourceRunId).collect(java.util.stream.Collectors.toSet()));
        copied.get(quest.getId().toString(), firstId).getCollectionData().claimEntryReward("survey", "entry", "run");
        copied.pruneSettledPending(quest.getId().toString());
        assertNull(copied.get(quest.getId().toString(), firstId));
        assertNotNull(copied.get(quest.getId().toString(), secondId));
        assertTrue(restored.get(quest.getId().toString(), firstId).getCollectionData().hasPendingEntryRewards(), "Copies must not share receipts");
    }

    @Test void hiddenRuntimeRewardsAreOmittedFromFullAndIsolatedRecipientSnapshots() {
        var entry = CollectionEntryBuilder.create(ENTRY).category("field")
                .visibility(VisibilityMode.HIDDEN_BY_DEFAULT, HiddenPresentationMode.PLACEHOLDER)
                .bindingReward("secret_reward", new ItemReward(Items.NETHER_STAR, 1)).build();
        var quest = QuestBuilder.create("example:hidden_run_receipts").mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field").entry(entry).build())
                .phase(PhaseBuilder.create("survey").collectionSheet(CollectionSheetBuilder.create()
                        .binding(EntryRequirementBuilder.create("entry", ENTRY).discovered()))).build();
        var player = new org.arcadia.arc_quest.questplayer.ArcQuestPlayer(java.util.UUID.randomUUID());
        var runtime = new QuestRuntimeData(quest.getId().toString(), "survey", 0, 0, 0, 0);
        player.addActiveQuest(runtime); CollectionSheetService.initialize(quest, runtime, player.getCollectionRecords());
        runtime.getCollectionData().unlockEntryReward("survey", "entry", "secret_reward");
        runtime.getCollectionData().unlockEntryReward("survey", "entry", "removed_reward");
        CompoundTag original = runtime.serializeNBT(), isolated = original.copy();
        org.arcadia.arc_quest.data.sync.CollectionContentDisclosure.sanitizeRuntimeSnapshot(isolated, quest, player.getCollectionRecords());
        assertFalse(isolated.toString().contains("secret_reward"));
        assertFalse(org.arcadia.arc_quest.data.sync.CollectionContentDisclosure.sanitizePlayerSnapshot(player, id -> quest)
                .toString().contains("secret_reward"));
        assertEquals(original, runtime.serializeNBT());
        player.getCollectionRecords().discover(ENTRY);
        var revealed = original.copy();
        org.arcadia.arc_quest.data.sync.CollectionContentDisclosure.sanitizeRuntimeSnapshot(revealed, quest, player.getCollectionRecords());
        assertTrue(revealed.toString().contains("secret_reward")); assertFalse(revealed.toString().contains("removed_reward"));
    }

    @Test void hiddenAndLockedRewardPayloadsNeverEnterDisclosureDocumentsAndReceiptFilteringPreservesServerState() {
        var entry = CollectionEntryBuilder.create(ENTRY).category("field")
                .visibility(VisibilityMode.HIDDEN_BY_DEFAULT, HiddenPresentationMode.PLACEHOLDER)
                .research(ObjectiveBuilder.custom(ENTRY, 3).id("study"))
                .discoveryReward("first", new ItemReward(Items.NETHER_STAR, 1))
                .researchReward("research", new ItemReward(Items.DIAMOND, 1)).build();
        var quest = QuestBuilder.create("example:private_entry_rewards").mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field").entry(entry).build())
                .phase(PhaseBuilder.create("survey").collectionSheet(CollectionSheetBuilder.create()
                        .binding(EntryRequirementBuilder.create("entry", ENTRY).discovered()))).build();
        var records = new CollectionRecordState();
        var raw = new org.arcadia.arc_quest.data.sync.DatapackContentSnapshot(1, java.util.Map.of(
                org.arcadia.arc_quest.data.sync.DatapackContentModule.QUEST,
                java.util.List.of(QuestSpecJsonWriter.write(CollectionDefinitionSpecExporter.quest(quest, null)))));
        var hidden = org.arcadia.arc_quest.data.sync.CollectionContentDisclosure.project(raw, records, id -> true, id -> quest, null);
        String wire = hidden.documents(org.arcadia.arc_quest.data.sync.DatapackContentModule.QUEST).get(0);
        assertFalse(wire.contains("minecraft:nether_star")); assertFalse(wire.contains("minecraft:diamond"));
        records.discover(ENTRY); records.unlockReward(ENTRY, "first");
        var revealed = org.arcadia.arc_quest.data.sync.CollectionContentDisclosure.project(raw, records, id -> true, id -> quest, null);
        wire = revealed.documents(org.arcadia.arc_quest.data.sync.DatapackContentModule.QUEST).get(0);
        assertTrue(wire.contains("minecraft:nether_star")); assertFalse(wire.contains("minecraft:diamond"));
        var player = new org.arcadia.arc_quest.questplayer.ArcQuestPlayer(java.util.UUID.randomUUID());
        player.addActiveQuest(new QuestRuntimeData(quest.getId().toString(), "survey", 0, 0, 0, 0));
        player.getCollectionRecords().copyFrom(records); player.getCollectionRecords().unlockReward(ENTRY, "removed_secret");
        CompoundTag snapshot = player.getCollectionRecords().serializeNBT(), source = snapshot.copy();
        CompoundTag safe = org.arcadia.arc_quest.data.sync.CollectionContentDisclosure.sanitizeRecordSnapshot(player, snapshot, id -> quest);
        assertEquals(source, snapshot);
        assertFalse(safe.toString().contains("removed_secret"));
        assertTrue(player.getCollectionRecords().isRewardUnlocked(ENTRY, "removed_secret"));
    }

    @Test void zombieInvestigationProducesAnatomyAndBothRewardsWithoutASecondResearchCounter() {
        var quest = CollectionFieldDemos.field(CollectionFieldDemos.entries());
        var entry = quest.getCollectionConfig().getEntry(CollectionFieldDemos.ZOMBIE);
        var phase = quest.getPhase("survey"); var binding = phase.getCollectionSheet().getBinding("zombie");
        var data = new ArcQuestPlayer(UUID.randomUUID());
        var runtime = new QuestRuntimeData(quest.getId().toString(), "survey", phase.getObjectives().size(), 1, 2, 3);
        data.addActiveQuest(runtime); var records = data.getCollectionRecords();
        CollectionSheetService.initialize(quest, runtime, records);
        assertTrue(entry.isUnifiedGameplay());
        assertTrue(entry.getResearchObjectives().isEmpty());
        var rules = List.of(new CollectionRecordService.RuleRef(entry, entry.getDiscoveryObjectives().get(0), CollectionRecordService.Scope.DISCOVERY));
        DatapackContentSyncService.DisclosureGrant partialGrant = null;
        for (int kills = 1; kills <= 3; kills++) {
            var before = CollectionEntryRewardService.knowledge(entry, records);
            CollectionRecordService.applyMatchedRules(records, rules, 1, ignored -> true);
            CollectionEntryRewardService.updatePermanent(null, data, entry, before);
            runtime.setObjectiveProgress("survey", 0, Math.min(kills, 3));
            runtime.setObjectiveProgress("survey", 1, 2);
            CollectionSheetService.satisfied(quest, phase, runtime, records);
            CollectionEntryRewardService.updateBindings(null, data, quest, phase, runtime);
            assertEquals(0, records.getProgress(entry.getEntryId(), CollectionProgressProjector.researchKey("anatomy")));
            var progress = CollectionProgressProjector.project(quest, phase, runtime, records).binding("zombie");
            assertEquals(kills >= 3, progress.complete(), "The run requires three defeats and the submitted samples");
            assertTrue(CollectionProgressProjector.researchProgress(entry, records).isEmpty());
            assertEquals(kills == 3, records.hasOutcome(entry.getEntryId(), "anatomy"));
            var reward = CollectionEntryRewardService.project(quest, phase, runtime, records, binding).stream()
                    .filter(row -> row.definition().rewardId().equals("zombie_anatomy")).findFirst().orElseThrow();
            assertEquals(kills == 3, reward.unlocked()); assertEquals(kills == 3, reward.canClaim()); assertFalse(reward.claimed());
            assertTrue(CollectionEntryRewardService.disclosedRewardIds(data, quest, entry).contains("zombie_anatomy"));
            var authorized = CollectionContentDisclosure.project(DatapackContentSnapshot.empty(7), records,
                    ignored -> true, ignored -> quest, null, List.of(quest));
            var spec = QuestSpecJsonReader.read(authorized.documents(DatapackContentModule.QUEST).get(0));
            var zombieSpec = spec.collectionConfig.entries.stream().filter(row -> row.entryId.equals(entry.getEntryId().toString())).findFirst().orElseThrow();
            assertTrue(zombieSpec.rewards.stream().anyMatch(row -> row.rewardId.equals("zombie_anatomy")),
                    "Public reward previews remain visible before eligibility");
            assertEquals(kills == 3, zombieSpec.content.stream().anyMatch(block -> block.blockId.equals("anatomy_notes")));
            var client = QuestSpecCompiler.compileClientPresentation(spec, Map.of());
            var safeRecords = new CollectionRecordState(); safeRecords.readSnapshot(CollectionContentDisclosure.sanitizeRecordSnapshot(data, records.serializeNBT(), ignored -> quest));
            var clientProgress = CollectionProgressProjector.project(client, client.getPhase("survey"), runtime, safeRecords).binding("zombie");
            assertEquals(kills == 3, clientProgress.researchComplete());
            assertEquals(kills == 3, clientProgress.entryRewards().stream().anyMatch(row -> row.definition().rewardId().equals("zombie_anatomy") && row.canClaim()));
            var grant = DatapackContentSyncService.DisclosureGrant.of(entry, records);
            if (kills == 1) partialGrant = grant;
            if (kills == 2) assertEquals(partialGrant, grant, "Partial counts do not resend unchanged content");
            if (kills == 3) assertNotEquals(partialGrant, grant, "The outcome unlocks new content");
        }
    }

    @Test void resettingTheDemoAndGivingItAgainStartsEveryBindingAndPermanentRequirementFromZero() {
        var quest = CollectionFieldDemos.field(CollectionFieldDemos.entries());
        var phase = quest.getPhase("survey");
        var data = new ArcQuestPlayer(UUID.randomUUID()); var records = data.getCollectionRecords();
        for (var entry : quest.getCollectionConfig().getEntries()) {
            records.discover(entry.getEntryId()); records.markSeen(entry.getEntryId(), "entry");
            entry.getDiscoveryObjectives().forEach(step -> records.increment(entry.getEntryId(),
                    CollectionProgressProjector.discoveryKey(step.getObjectiveId()), step.getRequiredCount(), step.getRequiredCount()));
            entry.getResearchObjectives().forEach(step -> records.increment(entry.getEntryId(),
                    CollectionProgressProjector.researchKey(step.getObjectiveId()), step.getRequiredCount(), step.getRequiredCount()));
            entry.getOutcomes().forEach(outcome -> records.recordOutcome(entry.getEntryId(), outcome.outcomeId(),
                    "test/completed-investigation", records.getGeneration(entry.getEntryId())));
            entry.getContent().forEach(block -> records.markSeen(entry.getEntryId(), block.blockId()));
            CollectionEntryRewardService.updatePermanent(null, data, entry, new CollectionEntryRewardService.Knowledge(false, false));
            entry.getRewards().stream().filter(reward -> reward.trigger() != CollectionEntryRewardTrigger.BINDING_COMPLETE)
                    .forEach(reward -> assertTrue(records.claimReward(entry.getEntryId(), reward.rewardId())));
        }
        var old = new QuestRuntimeData(quest.getId().toString(), "survey", phase.getObjectives().size(), 1, 2, 3);
        CollectionSheetService.initialize(quest, old, records);
        phase.getCollectionSheet().getBindings().forEach(binding -> old.getCollectionData().markBindingComplete("survey", binding.getBindingId()));
        old.getCollectionData().unlockEntryReward("survey", "zombie", "zombie_investigation");
        old.setState(QuestState.COMPLETED); data.addActiveQuest(old); data.markCompleted(old.getQuestId());
        assertNotNull(data.getCollectionArchives().get(old.getQuestId()));
        data.markPhaseStoryRead(old.getQuestId(), "survey");
        var unrelatedId = ResourceLocation.parse("example:untouched_record");
        records.discover(unrelatedId); records.increment(unrelatedId, "research:keep", 4, 7);
        records.markSeen(unrelatedId, "entry"); records.unlockReward(unrelatedId, "keep"); records.claimReward(unrelatedId, "keep");
        var unrelatedRecord = records.getRecord(unrelatedId).serializeNBT();
        var unrelatedRuntime = new QuestRuntimeData("example:untouched_active", "other", 1, 1, 2, 3);
        unrelatedRuntime.getOrCreateCollectionData().initializeSheet("other", List.of("keep"), 1, java.util.Set.of(CollectionFieldDemos.COAL.toString()));
        unrelatedRuntime.getCollectionData().markBindingComplete("other", "keep");
        unrelatedRuntime.setObjectiveProgress("other", 0, 4); data.addActiveQuest(unrelatedRuntime);
        var unrelatedArchived = new QuestRuntimeData("example:untouched_archive", "other", 1, 1, 2, 3);
        unrelatedArchived.getOrCreateCollectionData().initializeSheet("other", List.of("keep"), 1, java.util.Set.of(CollectionFieldDemos.COAL.toString()));
        unrelatedArchived.getCollectionData().markBindingComplete("other", "keep");
        unrelatedArchived.setState(QuestState.COMPLETED); data.addActiveQuest(unrelatedArchived); data.markCompleted(unrelatedArchived.getQuestId());
        var archivedNbt = data.getCollectionArchives().get(unrelatedArchived.getQuestId()).serializeNBT();

        data.resetQuest(quest);
        assertNull(data.getActiveQuest(old.getQuestId())); assertFalse(data.getCompletedQuests().contains(old.getQuestId()));
        assertNull(data.getCollectionArchives().get(old.getQuestId()));
        assertTrue(data.getCollectionArchives().allRuns(old.getQuestId()).isEmpty());
        assertFalse(data.isPhaseStoryRead(old.getQuestId(), "survey"));
        assertEquals(unrelatedRecord, records.getRecord(unrelatedId).serializeNBT());
        assertSame(unrelatedRuntime, data.getActiveQuest(unrelatedRuntime.getQuestId()));
        assertEquals(4, unrelatedRuntime.getObjectiveProgress("other", 0));
        assertTrue(unrelatedRuntime.getCollectionData().isBindingComplete("other", "keep"), "Another run's latched result is independent of shared knowledge reset");
        assertEquals(archivedNbt, data.getCollectionArchives().get(unrelatedArchived.getQuestId()).serializeNBT());
        assertTrue(data.getCompletedQuests().contains(unrelatedArchived.getQuestId()));
        var next = new QuestRuntimeData(quest.getId().toString(), "survey", phase.getObjectives().size(), 4, 5, 6);
        data.addActiveQuest(next); CollectionSheetService.initialize(quest, next, records);
        assertNotEquals(old.getCollectionData().getRunId(), next.getCollectionData().getRunId());
        var projection = CollectionProgressProjector.project(quest, phase, next, records, data.getCollectionArchives());
        assertEquals(0, projection.completed()); assertFalse(projection.complete());
        assertEquals(8, projection.bindings().size());
        projection.bindings().forEach(binding -> {
            assertFalse(binding.discovered()); assertFalse(binding.researchComplete()); assertFalse(binding.complete());
            assertTrue(binding.content().isEmpty());
            binding.requirements().forEach(requirement -> { assertEquals(0, requirement.current()); assertFalse(requirement.complete()); });
            binding.entryRewards().forEach(reward -> { assertFalse(reward.unlocked()); assertFalse(reward.claimed()); assertFalse(reward.canClaim()); });
        });
        for (var entry : quest.getCollectionConfig().getEntries()) {
            assertTrue(records.isEntryReset(entry.getEntryId()));
            var record = records.getRecord(entry.getEntryId());
            assertFalse(record.isDiscovered()); assertFalse(record.isSeen()); assertTrue(record.getAllProgress().isEmpty());
            assertTrue(record.getUnlockedRewardIds().isEmpty()); assertTrue(record.getClaimedRewardIds().isEmpty());
            entry.getContent().forEach(block -> assertFalse(record.isSeen(block.blockId())));
        }
        for (int i = 0; i < phase.getObjectives().size(); i++) assertEquals(0, next.getObjectiveProgress("survey", i));
    }

    @Test void resetRevokesOutcomeEligibilityButPreservesPublicPreviewAndNewInvestigationCanEarnItAgain() {
        var quest = CollectionFieldDemos.field(CollectionFieldDemos.entries());
        var entry = quest.getCollectionConfig().getEntry(CollectionFieldDemos.ZOMBIE); var phase = quest.getPhase("survey");
        var data = new ArcQuestPlayer(UUID.randomUUID()); var records = data.getCollectionRecords();
        records.discover(entry.getEntryId()); records.recordOutcome(entry.getEntryId(), "anatomy", "test/previous", records.getGeneration(entry.getEntryId()));
        CollectionEntryRewardService.updatePermanent(null, data, entry, new CollectionEntryRewardService.Knowledge(false, false));
        assertTrue(records.claimReward(entry.getEntryId(), "zombie_anatomy"));
        data.resetQuest(quest);
        var next = new QuestRuntimeData(quest.getId().toString(), "survey", phase.getObjectives().size(), 4, 5, 6);
        data.addActiveQuest(next); CollectionSheetService.initialize(quest, next, records);
        var reward = CollectionEntryRewardService.project(quest, phase, next, records, phase.getCollectionSheet().getBinding("zombie")).stream()
                .filter(row -> row.definition().rewardId().equals("zombie_anatomy")).findFirst().orElseThrow();
        assertFalse(reward.unlocked()); assertFalse(reward.claimed()); assertFalse(reward.canClaim());
        assertFalse(records.hasOutcome(entry.getEntryId(), "anatomy"));
        assertTrue(CollectionProgressProjector.researchProgress(entry, records).isEmpty());
        var authorized = CollectionContentDisclosure.project(DatapackContentSnapshot.empty(7), records,
                ignored -> true, ignored -> quest, null, List.of(quest));
        var spec = QuestSpecJsonReader.read(authorized.documents(DatapackContentModule.QUEST).get(0));
        var entrySpec = spec.collectionConfig.entries.stream().filter(row -> row.entryId.equals(entry.getEntryId().toString())).findFirst().orElseThrow();
        assertTrue(entrySpec.content.isEmpty());
        assertTrue(entrySpec.rewards.stream().anyMatch(row -> row.rewardId.equals("zombie_anatomy")));
        var safe = CollectionContentDisclosure.sanitizeRecordSnapshot(data, records.serializeNBT(), ignored -> quest);
        assertFalse(safe.contains("ResetEntryIds"), "Server-only reset markers must not be disclosed");
        assertFalse(safe.contains("EntryGenerations"));

        var rules = List.of(new CollectionRecordService.RuleRef(entry, entry.getDiscoveryObjectives().get(0), CollectionRecordService.Scope.DISCOVERY));
        var before = CollectionEntryRewardService.knowledge(entry, records);
        CollectionRecordService.applyMatchedRules(records, rules, 1, ignored -> true);
        CollectionEntryRewardService.updatePermanent(null, data, entry, before);
        assertFalse(records.isRewardUnlocked(entry.getEntryId(), "zombie_anatomy"));
        next.setObjectiveProgress("survey", 0, 3); next.setObjectiveProgress("survey", 1, 2);
        CollectionSheetService.satisfied(quest, phase, next, records);
        CollectionEntryRewardService.updateBindings(null, data, quest, phase, next);
        assertTrue(records.hasOutcome(entry.getEntryId(), "anatomy"));
        assertTrue(records.claimReward(entry.getEntryId(), "zombie_anatomy"));
        assertFalse(records.claimReward(entry.getEntryId(), "zombie_anatomy"));
    }
}
