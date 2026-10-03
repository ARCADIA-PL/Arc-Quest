package org.arcadia.arc_quest.collection;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.guide.registry.ArcQuestGuideContent;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.data.CollectionRunDefinitionStore;
import org.arcadia.arc_quest.quest.logic.CollectionEntryRewardService;
import org.arcadia.arc_quest.quest.logic.CollectionRecordService;
import org.arcadia.arc_quest.quest.logic.CollectionSheetService;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.network.QuestRejectCodeDictionary;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.registry.CollectionDemoDefinitionFactories;
import org.arcadia.arc_quest.quest.registry.CollectionFieldDemos;
import org.arcadia.arc_quest.quest.reward.ItemReward;
import org.arcadia.arc_quest.quest.service.QuestOfferService;
import org.arcadia.arc_quest.quest.tracking.QuestEventManager;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Real production event/command/reward paths. No test writes an investigation completion latch. */
@GameTestHolder(Arc_Quest.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UnifiedCollectionGameTests {
    private static final String TEMPLATE = "jei_empty";
    private static final String BATCH = "arc_quest.collection.unified";
    private static final String OUTCOME = "preparation", FIRST_REWARD = "first_preparation", RUN_REWARD = "payment";
    private static final ResourceLocation IRON = ResourceLocation.parse("minecraft:iron_ingot");

    private UnifiedCollectionGameTests() {}

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void craftInvestigationRecordsOneOutcomeAndPaysLifetimeAndExactRunRewards(GameTestHelper helper) {
        var entry = entry("arc_quest:gametest/unified_craft_entry");
        var quest = quest("arc_quest:gametest/unified_repeat_investigation", entry, true);
        withInstalled(helper, List.of(quest), player -> {
            var data = ArcQuestPlayerManager.getOrCreate(player);
            give(player, quest);
            var first = data.getActiveQuest(quest.getId().toString());
            helper.assertTrue(first != null && first.getObjectiveProgress("survey", 0) == 0,
                    "Actual give did not create a fresh investigation");
            String firstRun = first.getCollectionData().getRunId();

            craftIron(player, 1);
            helper.assertTrue(data.getCollectionRecords().isDiscovered(entry.getEntryId())
                            && first.getObjectiveProgress("survey", 0) == 1
                            && !data.getCollectionRecords().hasOutcome(entry.getEntryId(), OUTCOME),
                    "First real craft did not discover and advance once, or awarded the unfinished outcome");
            helper.assertTrue(claim(player, quest, "", FIRST_REWARD) == QuestRejectCodeDictionary.Code.COLLECTION_REWARD_NOT_UNLOCKED
                            && claim(player, quest, firstRun, RUN_REWARD) == QuestRejectCodeDictionary.Code.COLLECTION_REWARD_NOT_UNLOCKED,
                    "Incomplete investigation could claim lifetime or run payment");

            craftIron(player, 1);
            helper.assertTrue(first.getObjectiveProgress("survey", 0) == 2
                            && !first.getCollectionData().isBindingComplete("survey", "investigation"),
                    "A repeatable paid investigation must still require its actual material fulfillment");
            int heldBefore = player.getInventory().countItem(Items.IRON_INGOT);
            helper.assertTrue(QuestOfferService.submitOffer(player, quest.getId().toString(), "survey", 1, 1).accepted()
                            && player.getInventory().countItem(Items.IRON_INGOT) == heldBefore - 1,
                    "Real sample submission did not consume the investigation cost");
            helper.assertTrue(first.getCollectionData().isBindingComplete("survey", "investigation")
                            && data.getCollectionRecords().hasOutcome(entry.getEntryId(), OUTCOME),
                    "Completed production objectives did not latch binding and record its permanent outcome");
            helper.assertTrue(claim(player, quest, "", FIRST_REWARD) == QuestRejectCodeDictionary.Code.OK
                            && claim(player, quest, firstRun, RUN_REWARD) == QuestRejectCodeDictionary.Code.OK,
                    "Completed investigation did not authorize both legitimate reward scopes");
            helper.assertTrue(player.getInventory().countItem(Items.IRON_NUGGET) == 3
                            && player.getInventory().countItem(Items.EMERALD) == 1,
                    "Actual lifetime and run item rewards were not delivered");
            helper.assertTrue(claim(player, quest, "", FIRST_REWARD) == QuestRejectCodeDictionary.Code.COLLECTION_REWARD_ALREADY_CLAIMED
                            && claim(player, quest, firstRun, RUN_REWARD) == QuestRejectCodeDictionary.Code.COLLECTION_REWARD_ALREADY_CLAIMED,
                    "Duplicate same-run requests replayed a reward");
            data.deserializeNBT(data.serializeNBT());
            helper.assertTrue(data.getCollectionRecords().hasOutcome(entry.getEntryId(), OUTCOME)
                            && data.getCollectionRecords().isRewardClaimed(entry.getEntryId(), FIRST_REWARD),
                    "Player snapshot reload lost permanent outcome or claimed receipt");
            helper.assertTrue(QuestProgressHandler.confirmManualPhaseAdvance(player, quest.getId().toString(), "survey")
                            == QuestRejectCodeDictionary.Code.OK,
                    "Completed investigation could not archive normally");
            helper.assertTrue(QuestProgressHandler.acceptQuest(player, quest.getId().toString()), "Repeat investigation did not accept");
            var second = data.getActiveQuest(quest.getId().toString());
            String secondRun = second.getCollectionData().getRunId();
            helper.assertTrue(!firstRun.equals(secondRun) && second.getObjectiveProgress("survey", 0) == 0,
                    "Repeat reused the previous run's actions");
            craftIron(player, 2);
            helper.assertTrue(QuestOfferService.submitOffer(player, quest.getId().toString(), "survey", 1, 1).accepted(),
                    "Repeat must fulfill and consume its own sample instead of replaying an earlier cost");
            helper.assertTrue(claim(player, quest, "", FIRST_REWARD) == QuestRejectCodeDictionary.Code.COLLECTION_REWARD_ALREADY_CLAIMED
                            && claim(player, quest, secondRun, RUN_REWARD) == QuestRejectCodeDictionary.Code.OK,
                    "Repeat did not keep the lifetime reward once while paying its independent run");
            helper.assertTrue(player.getInventory().countItem(Items.IRON_NUGGET) == 3
                            && player.getInventory().countItem(Items.EMERALD) == 2,
                    "Repeat reissued lifetime items or failed to deliver its actual payment");
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void resetSharedEntryCannotReplayAnotherTasksOldCompletedOutcome(GameTestHelper helper) {
        var entry = entry("arc_quest:gametest/unified_shared_reset_entry");
        var first = quest("arc_quest:gametest/unified_shared_reset_first", entry, false);
        var second = quest("arc_quest:gametest/unified_shared_reset_second", entry, false);
        withInstalled(helper, List.of(first, second), player -> {
            var data = ArcQuestPlayerManager.getOrCreate(player);
            give(player, first); give(player, second);
            craftIron(player, 2);
            var oldSecond = data.getActiveQuest(second.getId().toString());
            String secondRun = oldSecond.getCollectionData().getRunId();
            helper.assertTrue(oldSecond.getCollectionData().isBindingComplete("survey", "investigation")
                            && data.getCollectionRecords().hasOutcome(entry.getEntryId(), OUTCOME),
                    "The shared investigation was not completed through its production event");
            reset(player, first);
            helper.assertTrue(!data.getCollectionRecords().isDiscovered(entry.getEntryId())
                            && !data.getCollectionRecords().hasOutcome(entry.getEntryId(), OUTCOME)
                            && data.getActiveQuest(second.getId().toString()).getCollectionData().isBindingComplete("survey", "investigation"),
                    "Single-task reset failed to clear shared knowledge or incorrectly cleared the other task's own completion");

            data.deserializeNBT(data.serializeNBT());
            CollectionRecordService.discoverInventory(player, Map.of(IRON, player.getInventory().countItem(Items.IRON_INGOT)));
            CollectionSheetService.refresh(player);
            var restoredSecond = data.getActiveQuest(second.getId().toString());
            CollectionEntryRewardService.updateBindings(player, data, second, second.getPhase("survey"), restoredSecond);
            helper.assertTrue(!data.getCollectionRecords().hasOutcome(entry.getEntryId(), OUTCOME)
                            && !data.getCollectionRecords().isRewardUnlocked(entry.getEntryId(), FIRST_REWARD),
                    "Reload, old inventory or a repeated old binding scan resurrected reset knowledge");
            helper.assertTrue(claim(player, second, secondRun, RUN_REWARD) == QuestRejectCodeDictionary.Code.OK,
                    "Reset incorrectly revoked the other task's independent earned run payment");

            give(player, first);
            craftIron(player, 1);
            CollectionEntryRewardService.updateBindings(player, data, second, second.getPhase("survey"), restoredSecond);
            helper.assertTrue(data.getCollectionRecords().isDiscovered(entry.getEntryId())
                            && !data.getCollectionRecords().hasOutcome(entry.getEntryId(), OUTCOME),
                    "New discovery allowed an old completion latch to regrant the reset outcome");
            craftIron(player, 1);
            helper.assertTrue(data.getCollectionRecords().hasOutcome(entry.getEntryId(), OUTCOME)
                            && claim(player, first, "", FIRST_REWARD) == QuestRejectCodeDictionary.Code.OK
                            && player.getInventory().countItem(Items.IRON_NUGGET) == 3,
                    "Fresh post-reset investigation failed to earn the outcome and first reward legitimately");
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void resetAllAndSnapshotReloadCannotImportOldHeldItemsAsFreshDiscovery(GameTestHelper helper) {
        var entry = entry("arc_quest:gametest/unified_reset_all_entry");
        var quest = quest("arc_quest:gametest/unified_reset_all_investigation", entry, false);
        withInstalled(helper, List.of(quest), player -> {
            var data = ArcQuestPlayerManager.getOrCreate(player);
            give(player, quest); craftIron(player, 2);
            helper.assertTrue(data.getCollectionRecords().hasOutcome(entry.getEntryId(), OUTCOME),
                    "Reset-all setup did not complete a genuine investigation");
            command(player, "arcquest quest reset @s");
            data.deserializeNBT(data.serializeNBT());
            CollectionRecordService.discoverInventory(player, Map.of(IRON, player.getInventory().countItem(Items.IRON_INGOT)));
            helper.assertTrue(!data.getCollectionRecords().isDiscovered(entry.getEntryId())
                            && !data.getCollectionRecords().hasOutcome(entry.getEntryId(), OUTCOME)
                            && data.getCollectionRecords().getRecord(entry.getEntryId()).getAllProgress().isEmpty(),
                    "Reset-all state was refilled by the production login inventory import");
            unlockTutorialGuides(player);
            give(player, quest);
            var next = data.getActiveQuest(quest.getId().toString());
            helper.assertTrue(next != null && next.getObjectiveProgress("survey", 0) == 0
                            && !data.getCollectionRecords().isDiscovered(entry.getEntryId()),
                    "Actual give restored old discovery or action progress after reset all");
            craftIron(player, 1);
            helper.assertTrue(data.getCollectionRecords().isDiscovered(entry.getEntryId())
                            && next.getObjectiveProgress("survey", 0) == 1
                            && !data.getCollectionRecords().hasOutcome(entry.getEntryId(), OUTCOME),
                    "Fresh post-reset event was suppressed or replayed old investigation completion");
            craftIron(player, 1);
            helper.assertTrue(data.getCollectionRecords().hasOutcome(entry.getEntryId(), OUTCOME)
                            && claim(player, quest, "", FIRST_REWARD) == QuestRejectCodeDictionary.Code.OK
                            && player.getInventory().countItem(Items.IRON_NUGGET) == 3,
                    "Reset all prevented a new completed investigation from earning real first-reward items");
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void acceptingThenAbandoningCannotReplayAPaidHoldingInvestigationBeforeCooldown(GameTestHelper helper) {
        var entry = CollectionEntryBuilder.create("arc_quest:gametest/cooldown_entry").category("field").item(Items.IRON_INGOT)
                .visibility(VisibilityMode.VISIBLE_BY_DEFAULT, HiddenPresentationMode.FULLY_HIDDEN).build();
        var quests = List.of(holdingQuest("arc_quest:gametest/cooldown_once", entry, false),
                holdingQuest("arc_quest:gametest/cooldown_repeat", entry, true));
        withInstalled(helper, quests, player -> {
            var data = ArcQuestPlayerManager.getOrCreate(player);
            for (var quest : quests) {
                int paidBefore = player.getInventory().countItem(Items.EMERALD);
                player.getInventory().add(new ItemStack(Items.IRON_INGOT, 2));
                helper.assertTrue(QuestProgressHandler.acceptQuestWithCode(player, quest.getId().toString()) == QuestRejectCodeDictionary.Code.OK,
                        "An initial paid preparation investigation should be accepted");
                var run = data.getActiveQuest(quest.getId().toString());
                helper.assertTrue(run != null && claim(player, quest, run.getCollectionData().getRunId(), RUN_REWARD)
                                == QuestRejectCodeDictionary.Code.OK && player.getInventory().countItem(Items.EMERALD) == paidBefore + 1,
                        "The actual holding investigation did not deliver its earned early payment");
                helper.assertTrue(QuestProgressHandler.abandonQuest(player, quest.getId().toString()), "Actual abandonment failed");
                data.deserializeNBT(data.serializeNBT());
                var expected = quest.isRepeatable() ? QuestRejectCodeDictionary.Code.COLLECTION_REPEAT_COOLDOWN
                        : QuestRejectCodeDictionary.Code.ALREADY_COMPLETED_NOT_REPEATABLE;
                var actual = QuestProgressHandler.acceptQuestWithCode(player, quest.getId().toString());
                helper.assertTrue(actual == expected && data.getActiveQuest(quest.getId().toString()) == null
                                && player.getInventory().countItem(Items.EMERALD) == paidBefore + 1,
                        "Abandon/reload bypassed terminal or cooldown protection: expected " + expected + " but got " + actual);
                reset(player, quest);
                helper.assertTrue(QuestProgressHandler.acceptQuestWithCode(player, quest.getId().toString()) == QuestRejectCodeDictionary.Code.OK,
                        "An explicit administrator reset must clear this quest's failure and acceptance interval");
            }
        });
    }

    private static QuestDefinition holdingQuest(String id, CollectionEntryDefinition entry, boolean repeat) {
        var builder = QuestBuilder.create(id).mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field").entry(entry).repeatCooldownTicks(repeat ? 1200 : 0).build())
                .phase(PhaseBuilder.create("survey").autoAdvanceOnComplete(false).objective(ObjectiveBuilder.possess(Items.IRON_INGOT, 2).id("hold"))
                        .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("investigation", entry.getEntryId())
                                .objective("hold").reward(RUN_REWARD, new ItemReward(Items.EMERALD, 1)))));
        if (repeat) builder.repeatable();
        return builder.build();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void onePositionSampleSettlesAllExistingTasksWithoutFillingANewPhase(GameTestHelper helper) {
        var id = ResourceLocation.parse("arc_quest:gametest/location_subject");
        var entry = CollectionEntryBuilder.create(id).category("field")
                .visibility(VisibilityMode.VISIBLE_BY_DEFAULT, HiddenPresentationMode.FULLY_HIDDEN).build();
        var config = CollectionQuestConfigBuilder.create().category("field", "Field").entry(entry).build();
        var sequential = QuestBuilder.create("arc_quest:gametest/location_sequential").mode(QuestMode.COLLECTION).collectionConfig(config)
                .phase(locationPhase("first", id).thenGoTo("second"))
                .phase(locationPhase("second", id)).build();
        var independent = QuestBuilder.create("arc_quest:gametest/location_independent").mode(QuestMode.COLLECTION).collectionConfig(config)
                .phase(locationPhase("first", id)).build();
        withInstalled(helper, List.of(sequential, independent), player -> {
            player.setPos(0, 64, 0);
            var data = ArcQuestPlayerManager.getOrCreate(player);
            give(player, sequential); give(player, independent);
            var sequentialRun = data.getActiveQuest(sequential.getId().toString());
            player.tickCount = 20;
            QuestEventManager.onPlayerTick(new PlayerTickEvent.Post(player));
            helper.assertTrue(data.isQuestCompleted(independent.getId().toString())
                            && sequentialRun.isPhaseCompleted("first") && sequentialRun.isPhaseActive("second")
                            && sequentialRun.getObjectiveProgress("second", 0) == 0,
                    "A single position sample missed an existing task or leaked into its newly activated phase");
            player.tickCount = 40;
            QuestEventManager.onPlayerTick(new PlayerTickEvent.Post(player));
            helper.assertTrue(data.isQuestCompleted(sequential.getId().toString()),
                    "The new phase could not receive the next independent position sample");
        });
    }

    private static PhaseBuilder locationPhase(String phaseId, ResourceLocation entryId) {
        return PhaseBuilder.create(phaseId).objective(ObjectiveBuilder.reachLocation(entryId, 0, 64, 0, 4).id("visit"))
                .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("visit", entryId).objective("visit")));
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void supplyDemoConsumesAnyThreeAndRejectsExtrasUntilTheRealCooldownExpires(GameTestHelper helper) {
        var quest = CollectionFieldDemos.renewable(CollectionFieldDemos.entries());
        withInstalled(helper, List.of(quest), player -> {
            var data = ArcQuestPlayerManager.getOrCreate(player);
            player.getInventory().add(new ItemStack(Items.COAL, 4));
            player.getInventory().add(new ItemStack(Items.IRON_INGOT, 1));
            player.getInventory().add(new ItemStack(Items.BONE, 3));
            player.getInventory().add(new ItemStack(Items.STRING, 3));
            helper.assertTrue(QuestProgressHandler.acceptQuestWithCode(player, quest.getId().toString())
                            == QuestRejectCodeDictionary.Code.OK,
                    "The actual supply demo could not be accepted");
            var run = data.getActiveQuest(quest.getId().toString());
            String firstRunId = run.getCollectionData().getRunId();
            long acceptedAt = data.getCollectionAcceptedAt(quest.getId().toString());
            helper.assertTrue(CollectionDemoDefinitionFactories.CURRENT_VERSION.equals(
                            CollectionRunDefinitionStore.capability(quest).version()),
                    "The demo fixture replaced the production version factory");

            submit(helper, player, quest, "round", "coal_action", 4);
            submit(helper, player, quest, "round", "iron_action", 1);
            helper.assertTrue(!run.getCollectionData().isSheetSettled("round")
                            && run.getCollectionData().getCompletedBindingCount("round") == 2,
                    "The supply sheet stopped before three real material deliveries");
            submit(helper, player, quest, "round", "bone_action", 3);
            helper.assertTrue(player.getInventory().countItem(Items.COAL) == 0
                            && player.getInventory().countItem(Items.IRON_INGOT) == 0
                            && player.getInventory().countItem(Items.BONE) == 0
                            && run.getCollectionData().isSheetSettled("round")
                            && run.getCollectionData().getCompletedBindingCount("round") == 3,
                    "Any-three completion failed to consume exactly the three selected supply costs");
            helper.assertTrue(!QuestOfferService.submitOffer(player, quest.getId().toString(), "round",
                            quest.getPhase("round").getObjectiveIndex("string_action"), 3).accepted()
                            && player.getInventory().countItem(Items.STRING) == 3
                            && run.getCollectionData().getCompletedBindingCount("round") == 3
                            && run.getObjectiveProgress("round", quest.getPhase("round").getObjectiveIndex("string_action")) == 0,
                    "A settled supply sheet accepted or charged an unselected fourth supply");
            helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0
                            && QuestProgressHandler.confirmManualPhaseAdvance(player, quest.getId().toString(), "round")
                            == QuestRejectCodeDictionary.Code.OK
                            && player.getInventory().countItem(Items.EMERALD) == 2,
                    "The supply demo did not pay exactly two emeralds at manual confirmation");

            data.deserializeNBT(data.serializeNBT());
            helper.assertTrue(data.getCollectionAcceptedAt(quest.getId().toString()) == acceptedAt,
                    "Snapshot reload lost the production acceptance timestamp");
            var clock = (ServerLevelData) helper.getLevel().getLevelData();
            long originalTime = helper.getLevel().getGameTime();
            try {
                // No world tick runs inside this synchronous fixture; restore the clock afterward.
                clock.setGameTime(acceptedAt + 1199);
                helper.assertTrue(QuestProgressHandler.acceptQuestWithCode(player, quest.getId().toString())
                                == QuestRejectCodeDictionary.Code.COLLECTION_REPEAT_COOLDOWN
                                && data.getActiveQuest(quest.getId().toString()) == null,
                        "The supply demo could be replayed one tick before its 1200-tick acceptance interval");
                clock.setGameTime(acceptedAt + 1200);
                helper.assertTrue(QuestProgressHandler.acceptQuestWithCode(player, quest.getId().toString())
                                == QuestRejectCodeDictionary.Code.OK,
                        "The supply demo stayed blocked at the exact 1200-tick boundary");
                var next = data.getActiveQuest(quest.getId().toString());
                helper.assertTrue(next != null && !firstRunId.equals(next.getCollectionData().getRunId())
                                && next.getCollectionData().getCompletedBindingCount("round") == 0
                                && quest.getPhase("round").getObjectives().stream().allMatch(objective ->
                                next.getObjectiveProgress("round", quest.getPhase("round")
                                        .getObjectiveIndex(objective.getObjectiveId())) == 0)
                                && player.getInventory().countItem(Items.EMERALD) == 2,
                        "A lawful repeat reused prior deliveries or paid without new material costs");
            } finally {
                clock.setGameTime(originalTime);
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void campDemoKeepsHeldEquipmentSeparateFromFreshCraftsAndJoinsBothRoutes(GameTestHelper helper) {
        var quest = CollectionFieldDemos.parallel(CollectionFieldDemos.entries());
        var fork = quest.getPhase("preparation").getTransitions();
        helper.assertTrue(fork.size() == 2 && fork.stream().allMatch(transition -> transition.getTargetPhaseIds().size() == 1)
                        && fork.stream().map(PhaseTransition::getTargetPhaseId).toList().containsAll(List.of("wildlife", "materials")),
                "The preparation must declare two independent transitions; a multi-target transition randomly selects only one route");
        withInstalled(helper, List.of(quest), player -> {
            var data = ArcQuestPlayerManager.getOrCreate(player);
            player.getInventory().add(new ItemStack(Items.CRAFTING_TABLE, 1));
            player.getInventory().add(new ItemStack(Items.TORCH, 8));
            give(player, quest);
            inventoryTick(player);
            var run = data.getActiveQuest(quest.getId().toString());
            helper.assertTrue(run != null, "The actual camp demo acceptance did not leave an active run");
            helper.assertTrue(run.getObjectiveProgress("preparation", quest.getPhase("preparation").getObjectiveIndex("prepare_bench")) == 1
                            && run.getObjectiveProgress("preparation", quest.getPhase("preparation").getObjectiveIndex("prepare_lighting")) == 8,
                    "The real inventory tick did not satisfy both POSSESSION checks: " + run);
            helper.assertTrue(run.isPhaseCompleted("preparation"), "The satisfied preparation did not advance: " + run);
            helper.assertTrue(run.isPhaseActive("wildlife") && run.isPhaseActive("materials"),
                    "The preparation did not activate both independent routes: " + run);
            helper.assertTrue(!run.isPhaseActive("report"), "The report opened before either investigation route finished: " + run);
            helper.assertTrue(player.getInventory().countItem(Items.CRAFTING_TABLE) == 1
                            && player.getInventory().countItem(Items.TORCH) == 8,
                    "The preparation consumed held equipment instead of checking possession");
            helper.assertTrue(quest.getPhase("materials").getObjectives().stream().allMatch(objective ->
                            run.getObjectiveProgress("materials", quest.getPhase("materials")
                                    .getObjectiveIndex(objective.getObjectiveId())) == 0),
                    "Already-held equipment was imported as a new craft in the materials route");

            craft(player, new ItemStack(Items.CRAFTING_TABLE, 1));
            helper.assertTrue(run.isPhaseActive("materials") && !run.isPhaseActive("report"),
                    "A single fresh craft prematurely completed the two-of-three materials route");
            craft(player, new ItemStack(Items.TORCH, 4));
            helper.assertTrue(run.isPhaseCompleted("materials") && run.isPhaseActive("wildlife")
                            && !run.isPhaseActive("report")
                            && run.getObjectiveProgress("materials", quest.getPhase("materials").getObjectiveIndex("iron_action")) == 0,
                    "Completing one route opened the report before the wildlife route finished");

            var zombie = EntityType.ZOMBIE.create(helper.getLevel());
            helper.assertTrue(zombie != null, "Could not create the real zombie event subject");
            QuestEventManager.onLivingDeath(new LivingDeathEvent(zombie, player.damageSources().playerAttack(player)));
            zombie.discard();
            helper.assertTrue(run.isPhaseActive("wildlife") && !run.isPhaseActive("report"),
                    "The wildlife route completed after only one of its two required actions");
            var cow = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(cow != null, "Could not create the real cow event subject");
            QuestEventManager.onRightClickEntity(new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, cow));
            cow.discard();
            helper.assertTrue(run.isPhaseCompleted("wildlife") && run.isPhaseCompleted("materials")
                            && run.isPhaseActive("report")
                            && run.getCollectionData().getCompletedBindingCount("wildlife") == 2
                            && run.getCollectionData().getCompletedBindingCount("materials") == 2,
                    "Two independently completed routes did not converge into the single report phase");

            player.getInventory().add(new ItemStack(Items.OAK_PLANKS, 8));
            player.getInventory().add(new ItemStack(Items.BIRCH_PLANKS, 8));
            submit(helper, player, quest, "report", "submit_planks", 16);
            submit(helper, player, quest, "report", "submit_torches", 8);
            helper.assertTrue(player.getInventory().countItem(Items.OAK_PLANKS) == 0
                            && player.getInventory().countItem(Items.BIRCH_PLANKS) == 0
                            && player.getInventory().countItem(Items.TORCH) == 4
                            && player.getInventory().countItem(Items.CRAFTING_TABLE) == 2
                            && player.getInventory().countItem(Items.DIAMOND) == 0,
                    "The final report did not consume mixed planks and exactly eight torches while retaining crafted gear");
            helper.assertTrue(QuestProgressHandler.confirmManualPhaseAdvance(player, quest.getId().toString(), "report")
                            == QuestRejectCodeDictionary.Code.OK
                            && data.isQuestCompleted(quest.getId().toString())
                            && player.getInventory().countItem(Items.DIAMOND) == 1,
                    "The completed camp report did not deliver its real final diamond reward");
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void handbookDemoMilkAndBoneMealRequireRealActionsAndRecordPermanentOutcomes(GameTestHelper helper) {
        var quest = CollectionFieldDemos.field(CollectionFieldDemos.entries());
        withInstalled(helper, List.of(quest), player -> {
            var data = ArcQuestPlayerManager.getOrCreate(player);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            player.getInventory().add(new ItemStack(Items.BONE_MEAL, 3));
            give(player, quest);
            inventoryTick(player);
            var run = data.getActiveQuest(quest.getId().toString());
            helper.assertTrue(run != null && run.getObjectiveProgress("survey",
                            quest.getPhase("survey").getObjectiveIndex("bone_processing")) == 0
                            && !data.getCollectionRecords().hasOutcome(CollectionFieldDemos.BONE, "cultivation"),
                    "Preexisting bone meal fulfilled the handbook's new-craft investigation");

            var cow = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(cow != null, "Could not create the real dairy investigation subject");
            QuestEventManager.onRightClickEntity(new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, cow));
            helper.assertTrue(cow.mobInteract(player, InteractionHand.MAIN_HAND).consumesAction()
                            && player.getInventory().countItem(Items.MILK_BUCKET) == 1
                            && player.getInventory().countItem(Items.BUCKET) == 0,
                    "The vanilla cow interaction did not turn the player's empty bucket into a real milk sample");
            cow.discard();
            helper.assertTrue(!run.getCollectionData().isBindingComplete("survey", "cow")
                            && !data.getCollectionRecords().hasOutcome(CollectionFieldDemos.COW, "dairy"),
                    "Contacting a cow alone recorded the unfinished dairy investigation");
            submit(helper, player, quest, "survey", "milk_sample", 1);
            helper.assertTrue(player.getInventory().countItem(Items.MILK_BUCKET) == 0
                            && run.getCollectionData().isBindingComplete("survey", "cow")
                            && data.getCollectionRecords().hasOutcome(CollectionFieldDemos.COW, "dairy"),
                    "The actual milk delivery failed to consume its sample or record the dairy outcome");
            helper.assertTrue(CollectionEntryRewardService.claim(player, data, quest.getId().toString(), "",
                            "survey", "cow", "cow_dairy_bucket") == QuestRejectCodeDictionary.Code.OK
                            && player.getInventory().countItem(Items.BUCKET) == 1,
                    "The dairy outcome did not return its earned empty-bucket lifetime reward");
            helper.assertTrue(CollectionEntryRewardService.claim(player, data, quest.getId().toString(), "",
                            "survey", "cow", "cow_dairy_bucket") == QuestRejectCodeDictionary.Code.COLLECTION_REWARD_ALREADY_CLAIMED
                            && player.getInventory().countItem(Items.BUCKET) == 1,
                    "Repeated dairy reward clicks duplicated the returned bucket");

            player.getInventory().add(new ItemStack(Items.BONE, 1));
            inventoryTick(player);
            helper.assertTrue(data.getCollectionRecords().isDiscovered(CollectionFieldDemos.BONE),
                    "A new real inventory acquisition did not discover the bone subject");
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.is(Items.BONE)) { stack.shrink(1); break; }
            }
            craft(player, new ItemStack(Items.BONE_MEAL, 3));
            helper.assertTrue(player.getInventory().countItem(Items.BONE) == 0
                            && player.getInventory().countItem(Items.BONE_MEAL) == 6
                            && run.getObjectiveProgress("survey", quest.getPhase("survey").getObjectiveIndex("bone_processing")) == 3
                            && run.getCollectionData().isBindingComplete("survey", "bone")
                            && data.getCollectionRecords().hasOutcome(CollectionFieldDemos.BONE, "cultivation")
                            && run.getCollectionData().getCompletedBindingCount("survey") == 2,
                    "The fresh three-item craft did not complete exactly the bone investigation and its cultivation record");
            data.deserializeNBT(data.serializeNBT());
            helper.assertTrue(data.getCollectionRecords().hasOutcome(CollectionFieldDemos.COW, "dairy")
                            && data.getCollectionRecords().hasOutcome(CollectionFieldDemos.BONE, "cultivation")
                            && data.getCollectionRecords().isRewardClaimed(CollectionFieldDemos.COW, "cow_dairy_bucket"),
                    "Reload lost genuine dairy/cultivation records or the already-returned bucket receipt");

            QuestProgressHandler.rebuildTrackingIndex(player, data);
            var resumed = data.getActiveQuest(quest.getId().toString());
            helper.assertTrue(resumed != null && resumed.getCollectionData().getCompletedBindingCount("survey") == 2,
                    "Reload did not restore the two legitimately completed handbook investigations");

            player.getInventory().add(new ItemStack(Items.ROTTEN_FLESH, 2));
            player.getInventory().add(new ItemStack(Items.ARROW, 2));
            player.getInventory().add(new ItemStack(Items.STRING, 2));
            player.getInventory().add(new ItemStack(Items.COAL, 5));
            player.getInventory().add(new ItemStack(Items.OAK_LOG, 4));
            player.getInventory().add(new ItemStack(Items.BIRCH_LOG, 4));
            player.getInventory().add(new ItemStack(Items.IRON_INGOT, 3));
            inventoryTick(player);
            helper.assertTrue(data.getCollectionRecords().isDiscovered(CollectionFieldDemos.IRON)
                            && data.getCollectionRecords().isDiscovered(CollectionFieldDemos.COAL)
                            && data.getCollectionRecords().isDiscovered(CollectionFieldDemos.LOGS),
                    "Actual sample acquisitions did not discover the three material subjects");

            defeat(helper, player, EntityType.ZOMBIE, 1);
            helper.assertTrue(resumed.getObjectiveProgress("survey", quest.getPhase("survey").getObjectiveIndex("zombie_defeats")) == 1
                            && !resumed.getCollectionData().isBindingComplete("survey", "zombie")
                            && resumed.getCollectionData().getCompletedBindingCount("survey") == 2
                            && !resumed.getCollectionData().isSheetSettled("survey"),
                    "A partial one-of-three zombie action prematurely settled an investigation or the handbook");
            defeat(helper, player, EntityType.ZOMBIE, 2);
            helper.assertTrue(!resumed.getCollectionData().isBindingComplete("survey", "zombie"),
                    "All zombie defeats incorrectly bypassed the required flesh sample delivery");
            submit(helper, player, quest, "survey", "zombie_samples", 2);
            helper.assertTrue(player.getInventory().countItem(Items.ROTTEN_FLESH) == 0
                            && resumed.getCollectionData().isBindingComplete("survey", "zombie"),
                    "The flesh delivery did not consume two samples and finish the genuine zombie investigation");

            defeat(helper, player, EntityType.SKELETON, 2);
            helper.assertTrue(!resumed.getCollectionData().isBindingComplete("survey", "skeleton"),
                    "Skeleton defeats alone bypassed the arrow sample requirement");
            submit(helper, player, quest, "survey", "arrow_samples", 2);
            defeat(helper, player, EntityType.SPIDER, 1);
            helper.assertTrue(data.getCollectionRecords().isDiscovered(CollectionFieldDemos.SPIDER)
                            && !resumed.getCollectionData().isBindingComplete("survey", "spider"),
                    "The real spider defeat did not reveal its hidden subject or bypassed the silk sample requirement");
            submit(helper, player, quest, "survey", "spider_samples", 2);
            craft(player, new ItemStack(Items.IRON_PICKAXE, 1));

            int coalBeforeDelivery = player.getInventory().countItem(Items.COAL);
            submit(helper, player, quest, "survey", "coal_samples", 5);
            helper.assertTrue(player.getInventory().countItem(Items.COAL) == coalBeforeDelivery - 5
                            && !resumed.getCollectionData().isBindingComplete("survey", "coal"),
                    "Coal delivery did not consume exactly five samples or bypassed the fresh torch craft");
            craft(player, new ItemStack(Items.TORCH, 4));
            submit(helper, player, quest, "survey", "log_samples", 8);
            helper.assertTrue(player.getInventory().countItem(Items.OAK_LOG) == 0
                            && player.getInventory().countItem(Items.BIRCH_LOG) == 0
                            && !resumed.getCollectionData().isBindingComplete("survey", "logs")
                            && resumed.getCollectionData().getCompletedBindingCount("survey") == 7
                            && !resumed.getCollectionData().isSheetSettled("survey"),
                    "Mixed-tag log delivery did not consume eight logs or incorrectly skipped the final workbench craft");
            craft(player, new ItemStack(Items.CRAFTING_TABLE, 1));

            var expectedOutcomes = Map.of(CollectionFieldDemos.ZOMBIE, "anatomy",
                    CollectionFieldDemos.SKELETON, "combat", CollectionFieldDemos.SPIDER, "samples",
                    CollectionFieldDemos.COW, "dairy", CollectionFieldDemos.IRON, "preparation",
                    CollectionFieldDemos.COAL, "fuel_samples", CollectionFieldDemos.LOGS, "wood_samples",
                    CollectionFieldDemos.BONE, "cultivation");
            helper.assertTrue(resumed.getCollectionData().getCompletedBindingCount("survey") == 8
                            && resumed.getCollectionData().isSheetSettled("survey")
                            && resumed.isPhasePendingManualAdvance("survey")
                            && quest.getPhase("survey").getCollectionSheet().getBindings().stream().allMatch(binding ->
                            resumed.getCollectionData().isBindingComplete("survey", binding.getBindingId()))
                            && expectedOutcomes.entrySet().stream().allMatch(outcome ->
                            data.getCollectionRecords().hasOutcome(outcome.getKey(), outcome.getValue())),
                    "Real kills, consumed samples and fresh crafts did not finish all eight bindings and permanent outcomes");
            helper.assertTrue(player.getInventory().countItem(Items.ARROW) == 0
                            && player.getInventory().countItem(Items.STRING) == 0
                            && player.getInventory().countItem(Items.IRON_PICKAXE) == 1
                            && player.getInventory().countItem(Items.TORCH) == 4
                            && player.getInventory().countItem(Items.CRAFTING_TABLE) == 1
                            && player.getInventory().countItem(Items.COAL) == 1
                            && player.getInventory().countItem(Items.STICK) == 2
                            && player.getInventory().countItem(Items.EMERALD) == 0
                            && !resumed.getCollectionData().isEntryRewardClaimed("survey", "zombie", "zombie_investigation"),
                    "Handbook sample costs, retained craft outputs or automatic coal/stick rewards were wrong, or manual payments leaked early");
            helper.assertTrue(QuestProgressHandler.confirmManualPhaseAdvance(player, quest.getId().toString(), "survey")
                            == QuestRejectCodeDictionary.Code.OK
                            && data.isQuestCompleted(quest.getId().toString())
                            && data.getActiveQuest(quest.getId().toString()) == null
                            && player.getInventory().countItem(Items.EMERALD) == 2
                            && expectedOutcomes.entrySet().stream().allMatch(outcome ->
                            data.getCollectionRecords().hasOutcome(outcome.getKey(), outcome.getValue())),
                    "The fully playable handbook did not archive all outcomes and pay exactly its two final emeralds at confirmation");
        });
    }

    private static void submit(GameTestHelper helper, ServerPlayer player, QuestDefinition quest,
                               String phaseId, String objectiveId, int amount) {
        int index = quest.getPhase(phaseId).getObjectiveIndex(objectiveId);
        helper.assertTrue(index >= 0 && QuestOfferService.submitOffer(player, quest.getId().toString(),
                        phaseId, index, amount).accepted(),
                "Actual demo delivery was rejected: " + phaseId + "/" + objectiveId);
    }

    private static void craft(ServerPlayer player, ItemStack output) {
        player.getInventory().add(output.copy());
        QuestEventManager.onItemCrafted(new PlayerEvent.ItemCraftedEvent(player, output, new SimpleContainer(9)));
    }

    private static void defeat(GameTestHelper helper, ServerPlayer player,
                               EntityType<? extends LivingEntity> type, int count) {
        for (int i = 0; i < count; i++) {
            var target = type.create(helper.getLevel());
            helper.assertTrue(target != null, "Could not create the real defeat event subject: " + type);
            try {
                QuestEventManager.onLivingDeath(new LivingDeathEvent(target, player.damageSources().playerAttack(player)));
            } finally {
                target.discard();
            }
        }
    }

    private static void inventoryTick(ServerPlayer player) {
        player.tickCount = (player.tickCount / 20 + 1) * 20;
        QuestEventManager.onPlayerTick(new PlayerTickEvent.Post(player));
    }

    private static CollectionEntryDefinition entry(String id) {
        return CollectionEntryBuilder.create(id).category("field").item(Items.IRON_INGOT)
                .discover(ObjectiveBuilder.collect(Items.IRON_INGOT, 1).id("discover"))
                .outcome(OUTCOME, "Metal preparation record")
                .outcomeReward(OUTCOME, FIRST_REWARD, new ItemReward(Items.IRON_NUGGET, 3)).build();
    }

    private static QuestDefinition quest(String id, CollectionEntryDefinition entry, boolean repeat) {
        var binding = EntryRequirementBuilder.create("investigation", entry.getEntryId())
                .objective("produce").recordOutcome(OUTCOME).reward(RUN_REWARD, new ItemReward(Items.EMERALD, 1));
        var phase = PhaseBuilder.create("survey").autoAdvanceOnComplete(false)
                .objective(ObjectiveBuilder.craft(Items.IRON_INGOT, 2).id("produce"));
        if (repeat) {
            phase.objective(ObjectiveBuilder.offer(Items.IRON_INGOT, 1).id("sample"));
            binding.objective("sample");
        }
        var builder = QuestBuilder.create(id).mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field").entry(entry).build())
                .phase(phase.collectionSheet(CollectionSheetBuilder.create().binding(binding)));
        if (repeat) builder.repeatable();
        return builder.build();
    }

    private static void craftIron(ServerPlayer player, int count) {
        ItemStack output = new ItemStack(Items.IRON_INGOT, count);
        player.getInventory().add(output.copy());
        QuestEventManager.onItemCrafted(new PlayerEvent.ItemCraftedEvent(player, output, new SimpleContainer(9)));
    }

    private static QuestRejectCodeDictionary.Code claim(ServerPlayer player, QuestDefinition quest, String runId, String rewardId) {
        return CollectionEntryRewardService.claim(player, ArcQuestPlayerManager.getOrCreate(player),
                quest.getId().toString(), runId, "survey", "investigation", rewardId);
    }

    private static void give(ServerPlayer player, QuestDefinition quest) { command(player, "arcquest quest give @s " + quest.getId()); }
    private static void reset(ServerPlayer player, QuestDefinition quest) { command(player, "arcquest quest reset @s " + quest.getId()); }
    private static void command(ServerPlayer player, String command) {
        var server = player.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withEntity(player).withPermission(4).withSuppressedOutput(), command);
    }

    private static void unlockTutorialGuides(ServerPlayer player) {
        var data = ArcQuestPlayerManager.getOrCreate(player);
        // Synthetic players have no network connection. Onboarding delivery is outside these fixtures.
        data.unlockGuide(ArcQuestGuideContent.TRACKING_MENU_GUIDE_ID);
        data.unlockGuide(ArcQuestGuideContent.PARALLEL_PHASES_GUIDE_ID);
    }

    private static void withInstalled(GameTestHelper helper, List<QuestDefinition> definitions, Consumer<ServerPlayer> test) {
        definitions.forEach(quest -> {
            if (CollectionDemoDefinitionFactories.isHistoricalDemo(quest.getId())) {
                CollectionDemoDefinitionFactories.ensureRegistered();
                return;
            }
            if (!org.arcadia.arc_quest.quest.data.CollectionRunDefinitionStore.hasCodeDefinitionFactory(quest.getId(), "gametest-v1"))
                org.arcadia.arc_quest.quest.data.CollectionRunDefinitionStore.registerCodeDefinitionFactory(quest.getId(), "gametest-v1", () -> quest, true);
        });
        var previous = QuestRegistry.getDatapackSnapshot();
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "ArcQUnifiedTest"), net.minecraft.server.level.ClientInformation.createDefault());
        try {
            unlockTutorialGuides(player);
            var next = new LinkedHashMap<>(previous);
            definitions.forEach(definition -> next.put(definition.getId(), definition));
            QuestRegistry.replaceDatapackSnapshot(next);
            test.accept(player);
        } finally {
            QuestRegistry.replaceDatapackSnapshot(previous);
            QuestEventManager.onPlayerLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
            ArcQuestPlayerManager.unload(player.getUUID());
            PlayerSessionEpochManager.endSession(player.getUUID());

        }
        helper.succeed();
    }
}
