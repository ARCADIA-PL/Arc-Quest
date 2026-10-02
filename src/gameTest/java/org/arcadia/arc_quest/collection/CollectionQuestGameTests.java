package org.arcadia.arc_quest.collection;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.CollectionRecordService;
import org.arcadia.arc_quest.quest.logic.CollectionSheetService;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.tracking.QuestEventManager;
import org.arcadia.arc_quest.quest.logic.profile.CollectionQuestEngine;
import org.arcadia.arc_quest.quest.api.rule.collection.CompletedEntryCountRule;
import org.arcadia.arc_quest.quest.network.QuestRejectCodeDictionary;
import org.arcadia.arc_quest.quest.service.QuestOfferService;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder(Arc_Quest.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CollectionQuestGameTests {
    private static final String TEMPLATE = "jei_empty";
    private static final String BATCH = "arc_quest.collection";
    private CollectionQuestGameTests() {}

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void permanentTagDiscoveryOutsideQuestAndInventoryLoginNeverFabricatesResearch(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.parse("arc_quest:gametest/permanent_logs");
        var entry = CollectionEntryBuilder.create(id).category("materials").itemTag(ResourceLocation.parse("minecraft:logs"))
                .discover(ObjectiveBuilder.collectTag(ResourceLocation.parse("minecraft:logs"), 1).id("discover"))
                .research(ObjectiveBuilder.collectTag(ResourceLocation.parse("minecraft:logs"), 4).id("samples")).build();
        var definition = QuestBuilder.create("arc_quest:gametest/permanent_logs_quest").mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("materials", "Materials").entry(entry).build())
                .phase(PhaseBuilder.create("survey").collectionSheet(CollectionSheetBuilder.create()
                        .binding(EntryRequirementBuilder.create("logs", id).discovered()))).build();
        withInstalled(helper, definition, player -> {
            var data = ArcQuestPlayerManager.getOrCreate(player);
            CollectionRecordService.rebuildIndex();
            helper.assertTrue(CollectionRecordService.rulesFor(ObjectiveType.COLLECT, ResourceLocation.parse("minecraft:birch_log")).stream()
                            .filter(rule -> rule.entry().getEntryId().equals(id)).count() == 2,
                    "Loaded Tag must index discovery and research rules");
            helper.assertTrue(CollectionRecordService.rulesFor(ObjectiveType.COLLECT, ResourceLocation.parse("minecraft:stone")).stream()
                    .noneMatch(rule -> rule.entry().getEntryId().equals(id)), "Non-tag member indexed");
            CollectionRecordService.discoverInventory(player, Map.of(ResourceLocation.parse("minecraft:oak_log"), 16));
            helper.assertTrue(data.getCollectionRecords().isDiscovered(id), "Held item should establish discovery outside any Quest");
            helper.assertTrue(data.getAllActiveQuests().isEmpty(), "Discovery should not accept a Quest");
            helper.assertTrue(data.getCollectionRecords().getProgress(id, CollectionProgressProjector.researchKey("samples")) == 0,
                    "Login inventory fabricated a research pickup");
            CollectionRecordService.dispatch(player, ObjectiveType.COLLECT, ResourceLocation.parse("minecraft:birch_log"), 2);
            helper.assertTrue(data.getCollectionRecords().getProgress(id, CollectionProgressProjector.researchKey("samples")) == 2,
                    "Real acquisition of a Tag member was not recorded");
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void discoveryEventActivatesNextPhaseWithoutBackfillingItsRunObjectives(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.parse("arc_quest:gametest/discovery_transition");
        var entry = CollectionEntryBuilder.create(id).category("field")
                .discover(ObjectiveBuilder.custom(id, 1).id("discover")).build();
        var definition = QuestBuilder.create("arc_quest:gametest/discovery_transition_quest").mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field").entry(entry).build())
                .phase(PhaseBuilder.create("discover").collectionSheet(CollectionSheetBuilder.create()
                        .binding(EntryRequirementBuilder.create("first", id).discovered())).thenGoTo("followup"))
                .phase(PhaseBuilder.create("followup").objective(ObjectiveBuilder.custom(id, 2).id("action"))
                        .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("second", id).objective("action"))))
                .build();
        withInstalled(helper, definition, player -> {
            var data = ArcQuestPlayerManager.getOrCreate(player);
            var runtime = new QuestRuntimeData(definition.getId().toString(), "discover", 0, 0L, 0L, 0L);
            data.addActiveQuest(runtime); CollectionSheetService.initialize(definition, runtime, data.getCollectionRecords());
            QuestEventManager.notifyCustom(player, id);
            helper.assertTrue(runtime.isPhaseCompleted("discover") && runtime.isPhaseActive("followup"), "Discovery did not enter the actual next phase");
            helper.assertTrue(runtime.getObjectiveProgress("followup", 0) == 0, "The activating event counted in a phase not active at event start");
            QuestEventManager.notifyCustom(player, id);
            helper.assertTrue(runtime.getObjectiveProgress("followup", 0) == 1, "A subsequent active-phase event was not counted");
            helper.assertTrue(QuestProgressHandler.abandonQuest(player, definition.getId().toString()), "Modern Quest should support normal abandonment");
            helper.assertTrue(data.getCollectionRecords().isDiscovered(id), "Abandoning a task erased permanent knowledge");
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void quotaUsesManualConfirmationAndRewardsOncePerRunIncludingArchivedClaims(GameTestHelper helper) {
        ResourceLocation questId = ResourceLocation.parse("arc_quest:gametest/quota_rewards");
        ResourceLocation a = ResourceLocation.parse("arc_quest:gametest/quota_a"), b = ResourceLocation.parse("arc_quest:gametest/quota_b"), c = ResourceLocation.parse("arc_quest:gametest/quota_c");
        AtomicInteger automatic = new AtomicInteger(), manual = new AtomicInteger(), finalReward = new AtomicInteger();
        var config = CollectionQuestConfigBuilder.create().category("field", "Field")
                .entry(CollectionEntryBuilder.create(a).category("field").discover(ObjectiveBuilder.custom(a, 1).id("discover")))
                .entry(CollectionEntryBuilder.create(b).category("field").discover(ObjectiveBuilder.custom(b, 1).id("discover")))
                .entry(CollectionEntryBuilder.create(c).category("field").discover(ObjectiveBuilder.custom(c, 1).id("discover")))
                .reward(new CollectionRewardNode("quota_auto", RewardScope.QUEST, EntryRewardGrantMode.AUTO,
                        List.of(counter(automatic)), List.of(new CompletedEntryCountRule(1)), questId.toString()))
                .reward(new CollectionRewardNode("quota_manual", RewardScope.QUEST, EntryRewardGrantMode.MANUAL,
                        List.of(counter(manual)), List.of(new CompletedEntryCountRule(2)), questId.toString())).build();
        var definition = QuestBuilder.create(questId).mode(QuestMode.COLLECTION).repeatable().collectionConfig(config)
                .phase(PhaseBuilder.create("round").autoAdvanceOnComplete(false)
                        .objective(ObjectiveBuilder.custom(a, 1).id("a"))
                        .objective(ObjectiveBuilder.custom(b, 1).id("b"))
                        .objective(ObjectiveBuilder.custom(c, 1).id("c"))
                        .collectionSheet(CollectionSheetBuilder.create().quota(2)
                                .binding(EntryRequirementBuilder.create("a", a).objective("a"))
                                .binding(EntryRequirementBuilder.create("b", b).objective("b"))
                                .binding(EntryRequirementBuilder.create("c", c).objective("c"))))
                .reward(counter(finalReward)).build();
        withInstalled(helper, definition, player -> {
            var data = ArcQuestPlayerManager.getOrCreate(player);
            helper.assertTrue(QuestProgressHandler.acceptQuest(player, questId.toString()), "Repeatable modern Quest acceptance failed");
            var first = data.getActiveQuest(questId.toString()); var firstRun = first.getCollectionData().getRunId();
            QuestEventManager.notifyCustom(player, a); QuestEventManager.notifyCustom(player, a);
            helper.assertTrue(automatic.get() == 1, "Automatic milestone was not granted exactly once");
            QuestEventManager.notifyCustom(player, b);
            helper.assertTrue(first.getState() == QuestState.ACTIVE && first.isPhasePendingManualAdvance("round"), "QUOTA should wait for explicit confirmation");
            helper.assertTrue(first.getObjectiveProgress("round", 2) == 0 && finalReward.get() == 0, "QUOTA forced all candidates or granted final rewards early");
            helper.assertTrue(QuestProgressHandler.confirmManualPhaseAdvance(player, questId.toString(), "round") == QuestRejectCodeDictionary.Code.OK, "Manual confirmation failed");
            helper.assertTrue(finalReward.get() == 1, "Final task reward was not granted once");
            var archived = data.getCollectionArchives().get(questId.toString());
            helper.assertTrue(archived != null && archived.getState() == QuestState.COMPLETED, "Terminal sheet was not archived");
            helper.assertTrue(CollectionQuestEngine.claimRewardWithResult(player, data, definition, archived, "quota_manual").isOk(), "Archived manual reward could not be claimed");
            helper.assertTrue(!CollectionQuestEngine.claimRewardWithResult(player, data, definition, archived, "quota_manual").isOk() && manual.get() == 1, "Archived reward was granted twice");
            helper.assertTrue(QuestProgressHandler.acceptQuest(player, questId.toString()), "Repeatable Quest could not start another run");
            var next = data.getActiveQuest(questId.toString());
            helper.assertTrue(!firstRun.equals(next.getCollectionData().getRunId()), "Repeat acceptance reused the run ID");
            helper.assertTrue(next.getObjectiveProgress("round", 0) == 0 && !next.isPhasePendingManualAdvance("round"), "Repeat acceptance inherited the old action counters");
            helper.assertTrue(data.getCollectionRecords().isDiscovered(a) && data.getCollectionRecords().isDiscovered(b), "Repeat acceptance erased shared knowledge");
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void tagOfferConsumesOnlyRealItemsAndUpdatesPermanentResearchWithTheConsumedAmount(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.parse("arc_quest:gametest/tag_offer_entry");
        var entry = CollectionEntryBuilder.create(id).category("materials").itemTag(ResourceLocation.parse("minecraft:logs"))
                .discover(ObjectiveBuilder.collectTag(ResourceLocation.parse("minecraft:logs"), 1).id("discovery"))
                .research(ObjectiveBuilder.offerTag(ResourceLocation.parse("minecraft:logs"), 5).id("submitted_samples")).build();
        var definition = QuestBuilder.create("arc_quest:gametest/tag_offer_quest").mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("materials", "Materials").entry(entry).build())
                .phase(PhaseBuilder.create("submit").autoAdvanceOnComplete(false)
                        .objective(ObjectiveBuilder.offerTag(ResourceLocation.parse("minecraft:logs"), 5).id("offer"))
                        .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("logs", id).objective("offer"))))
                .build();
        withInstalled(helper, definition, player -> {
            var data = ArcQuestPlayerManager.getOrCreate(player);
            player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 4));
            player.getInventory().setItem(1, new ItemStack(Items.BIRCH_LOG, 3));
            player.getInventory().setItem(2, new ItemStack(Items.STONE, 9));
            helper.assertTrue(QuestProgressHandler.acceptQuest(player, definition.getId().toString()), "Tag offer Quest did not accept");
            var result = QuestOfferService.submitOffer(player, definition.getId().toString(), "submit", 0, 99);
            helper.assertTrue(result.accepted() && result.objectiveReached(), "Tag submission did not reach the configured amount");
            helper.assertTrue(player.getInventory().getItem(0).isEmpty() && player.getInventory().getItem(1).getCount() == 2,
                    "Tag submission consumed more or fewer than five actual log items");
            helper.assertTrue(player.getInventory().getItem(2).getCount() == 9, "Tag submission consumed a nonmember");
            helper.assertTrue(data.getCollectionRecords().getProgress(id, CollectionProgressProjector.researchKey("submitted_samples")) == 5,
                    "Permanent submission research counted requested amount instead of actual consumed items");
            helper.assertTrue(!QuestOfferService.submitOffer(player, definition.getId().toString(), "submit", 0, 1).accepted(), "Completed offer accepted a replay");
        });
    }

    private static IReward counter(AtomicInteger counter) {
        return new IReward() {
            public void grant(ServerPlayer player) { counter.incrementAndGet(); }
            public String describe() { return "Collection regression counter"; }
        };
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void threeAlreadyKnownChaptersCompleteAndReentrantRewardCallbackCannotAwardTwice(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.parse("arc_quest:gametest/known_history_entry");
        var entry = CollectionEntryBuilder.create(id).category("field").build();
        AtomicInteger grants = new AtomicInteger();
        IReward reentrant = new IReward() {
            public void grant(ServerPlayer player) { grants.incrementAndGet(); CollectionSheetService.refresh(player); }
            public String describe() { return "Reentrant collection completion regression"; }
        };
        var definition = QuestBuilder.create("arc_quest:gametest/known_history_quest").mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field").entry(entry).build())
                .phase(PhaseBuilder.create("one").collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("a", id).discovered())).thenGoTo("two"))
                .phase(PhaseBuilder.create("two").collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("b", id).discovered())).thenGoTo("three"))
                .phase(PhaseBuilder.create("three").collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("c", id).discovered())))
                .reward(reentrant).build();
        withInstalled(helper, definition, player -> {
            var data = ArcQuestPlayerManager.getOrCreate(player); data.getCollectionRecords().discover(id);
            helper.assertTrue(QuestProgressHandler.acceptQuest(player, definition.getId().toString()), "Known-entry chapters did not accept");
            var archive = data.getCollectionArchives().get(definition.getId().toString());
            helper.assertTrue(archive != null && archive.isPhaseCompleted("one") && archive.isPhaseCompleted("two") && archive.isPhaseCompleted("three"), "Already-known chapters did not follow the actual phase chain");
            helper.assertTrue(grants.get() == 1, "Nested immediate completion or a callback granted the final reward more than once");
            CollectionSheetService.refresh(player); helper.assertTrue(grants.get() == 1, "Terminal refresh reissued the reward");
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void migratedLegacyRewardReceiptPreventsModernMilestoneFromGrantingAgain(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.parse("arc_quest:gametest/legacy_receipt_entry");
        ResourceLocation questId = ResourceLocation.parse("arc_quest:gametest/legacy_receipt_quest");
        AtomicInteger grants = new AtomicInteger();
        var definition = QuestBuilder.create(questId).mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field")
                        .entry(CollectionEntryBuilder.create(id).category("field"))
                        .reward(new CollectionRewardNode("legacy_auto_receipt", RewardScope.QUEST, EntryRewardGrantMode.AUTO,
                                List.of(counter(grants)), List.of(new CompletedEntryCountRule(1)), questId.toString())).build())
                .phase(PhaseBuilder.create("survey").autoAdvanceOnComplete(false)
                        .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("known", id).discovered())))
                .build();
        withInstalled(helper, definition, player -> {
            var data = ArcQuestPlayerManager.getOrCreate(player); data.getCollectionRecords().discover(id);
            data.getCollectionRecords().markLegacyRewardClaimed(questId.toString(), "legacy_auto_receipt");
            helper.assertTrue(QuestProgressHandler.acceptQuest(player, questId.toString()), "Migrated reward task did not accept");
            var run = data.getActiveQuest(questId.toString());
            helper.assertTrue(run.getCollectionData().isRewardClaimed("legacy_auto_receipt"), "Legacy reward receipt was not inherited");
            helper.assertTrue(grants.get() == 0, "The modern task duplicated a reward already granted by the legacy task");
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void publicVisibilityDoesNotBypassRecordConditionsAndDiscoveryCanStartResearchInTheSameEvent(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.parse("arc_quest:gametest/qualified_entry");
        var entry = CollectionEntryBuilder.create(id).category("field")
                .recordWhen(ICondition.flagSet("collection_test_record_permission"))
                .researchAfterDiscovery(true)
                .discover(ObjectiveBuilder.custom(id, 1).id("discover"))
                .research(ObjectiveBuilder.custom(id, 2).id("research")).build();
        var definition = QuestBuilder.create("arc_quest:gametest/qualified_quest").mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field").entry(entry).build())
                .phase(PhaseBuilder.create("survey").collectionSheet(CollectionSheetBuilder.create()
                        .binding(EntryRequirementBuilder.create("entry", id).discovered()))).build();
        withInstalled(helper, definition, player -> {
            var data = ArcQuestPlayerManager.getOrCreate(player);
            helper.assertTrue(data.getAllActiveQuests().isEmpty(), "This case must not require accepting a task");
            QuestEventManager.notifyCustom(player, id);
            helper.assertTrue(!data.getCollectionRecords().isDiscovered(id), "PUBLIC display bypassed recordConditions");
            data.setFlag("collection_test_record_permission"); QuestEventManager.notifyCustom(player, id);
            helper.assertTrue(data.getCollectionRecords().isDiscovered(id), "A qualified event did not establish permanent knowledge");
            helper.assertTrue(data.getCollectionRecords().getProgress(id, CollectionProgressProjector.researchKey("research")) == 1,
                    "The qualified discovery event should research once after discovery");
            data.removeFlag("collection_test_record_permission"); QuestEventManager.notifyCustom(player, id);
            helper.assertTrue(data.getCollectionRecords().getProgress(id, CollectionProgressProjector.researchKey("research")) == 1,
                    "Revoking record qualification did not stop research accumulation");
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void impossibleNewDiscoveryAcceptanceHasNoRuntimeTrackingFlagsOrRewards(GameTestHelper helper) {
        ResourceLocation a = ResourceLocation.parse("arc_quest:gametest/impossible_known"),
                b = ResourceLocation.parse("arc_quest:gametest/impossible_fresh");
        AtomicInteger grants = new AtomicInteger();
        var definition = QuestBuilder.create("arc_quest:gametest/impossible_new_discoveries").mode(QuestMode.COLLECTION)
                .setFlagOnAccept("impossible_accept_side_effect")
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field")
                        .entry(CollectionEntryBuilder.create(a).category("field"))
                        .entry(CollectionEntryBuilder.create(b).category("field"))
                        .reward(new CollectionRewardNode("impossible_milestone", RewardScope.QUEST, EntryRewardGrantMode.AUTO,
                                List.of(counter(grants)), List.of(new CompletedEntryCountRule(1)), "arc_quest:gametest/impossible_new_discoveries")).build())
                .phase(PhaseBuilder.create("survey").objective(ObjectiveBuilder.custom(a, 1).id("action"))
                        .reward(counter(grants)).collectionSheet(CollectionSheetBuilder.create().quota(2)
                                .binding(EntryRequirementBuilder.create("a", a).discovered().recordPolicy(CollectionRecordPolicy.NEW_DISCOVERIES))
                                .binding(EntryRequirementBuilder.create("b", b).discovered().recordPolicy(CollectionRecordPolicy.NEW_DISCOVERIES))))
                .reward(counter(grants)).build();
        withInstalled(helper, definition, player -> {
            var data = ArcQuestPlayerManager.getOrCreate(player); data.getCollectionRecords().discover(a);
            var result = QuestProgressHandler.acceptQuestWithCode(player, definition.getId().toString());
            helper.assertTrue(result == QuestRejectCodeDictionary.Code.COLLECTION_NEW_DISCOVERIES_UNAVAILABLE,
                    "Old discovery left too few eligible candidates but acceptance succeeded");
            helper.assertTrue(data.getActiveQuest(definition.getId().toString()) == null
                            && data.getCollectionArchives().get(definition.getId().toString()) == null,
                    "Rejected acceptance registered a run or archive");
            helper.assertTrue(!data.getAllFlags().contains("impossible_accept_side_effect") && grants.get() == 0,
                    "Rejected acceptance changed flags or granted rewards");
            helper.assertTrue(org.arcadia.arc_quest.quest.tracking.ObjectiveTracker.INSTANCE.lookup(player.getUUID(),
                    new org.arcadia.arc_quest.quest.tracking.ObjectiveKey(ObjectiveType.CUSTOM, a)).isEmpty(),
                    "Rejected acceptance registered objective trackers");
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void phaseRewardRefreshCannotReenterAutomaticOrManualCollectionCompletion(GameTestHelper helper) {
        ResourceLocation entryId = ResourceLocation.parse("arc_quest:gametest/reentrant_phase_entry");
        AtomicInteger automatic = new AtomicInteger(), manual = new AtomicInteger(), finalGrant = new AtomicInteger();
        IReward autoReward = refreshingCounter(automatic), manualReward = refreshingCounter(manual);
        var definition = QuestBuilder.create("arc_quest:gametest/reentrant_phase_rewards").mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field")
                        .entry(CollectionEntryBuilder.create(entryId).category("field")).build())
                .phase(PhaseBuilder.create("automatic").reward(autoReward).collectionSheet(CollectionSheetBuilder.create()
                        .binding(EntryRequirementBuilder.create("auto", entryId).discovered())).thenGoTo("manual"))
                .phase(PhaseBuilder.create("manual").autoAdvanceOnComplete(false).reward(manualReward)
                        .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("manual", entryId).discovered())))
                .reward(counter(finalGrant)).build();
        withInstalled(helper, definition, player -> {
            var data = ArcQuestPlayerManager.getOrCreate(player); data.getCollectionRecords().discover(entryId);
            helper.assertTrue(QuestProgressHandler.acceptQuest(player, definition.getId().toString()), "Reentrant phase quest did not accept");
            var run = data.getActiveQuest(definition.getId().toString());
            helper.assertTrue(run != null && run.isPhaseCompleted("automatic") && run.isPhasePendingManualAdvance("manual"),
                    "Callbacks skipped or duplicated actual phase transitions");
            helper.assertTrue(automatic.get() == 1 && manual.get() == 1 && finalGrant.get() == 0,
                    "A refreshing phase reward reentered completion or completed the manual phase early");
            CollectionSheetService.refresh(player);
            helper.assertTrue(automatic.get() == 1 && manual.get() == 1, "Pending refresh granted phase rewards again");
            helper.assertTrue(QuestProgressHandler.confirmManualPhaseAdvance(player, definition.getId().toString(), "manual")
                    == QuestRejectCodeDictionary.Code.OK, "Manual confirmation failed after a reentrant callback");
            helper.assertTrue(automatic.get() == 1 && manual.get() == 1 && finalGrant.get() == 1,
                    "Confirmation duplicated phase rewards or final completion");
        });
    }

    private static IReward refreshingCounter(AtomicInteger counter) {
        return new IReward() {
            public void grant(ServerPlayer player) { counter.incrementAndGet(); CollectionSheetService.refresh(player); }
            public String describe() { return "Phase callback reentry regression"; }
        };
    }

    private static void withInstalled(GameTestHelper helper, QuestDefinition definition, java.util.function.Consumer<ServerPlayer> test) {
        if (!org.arcadia.arc_quest.quest.data.CollectionRunDefinitionStore.hasCodeDefinitionFactory(definition.getId(), "gametest-v1"))
            org.arcadia.arc_quest.quest.data.CollectionRunDefinitionStore.registerCodeDefinitionFactory(definition.getId(), "gametest-v1", () -> definition, true);
        var previous = QuestRegistry.getDatapackSnapshot();
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "ArcQCodexTest"));
        try {
            var next = new LinkedHashMap<>(previous); next.put(definition.getId(), definition); QuestRegistry.replaceDatapackSnapshot(next);
            test.accept(player);
        } finally {
            QuestRegistry.replaceDatapackSnapshot(previous);
            ArcQuestPlayerManager.unload(player.getUUID()); PlayerSessionEpochManager.endSession(player.getUUID()); player.invalidateCaps();
        }
        helper.succeed();
    }
}
