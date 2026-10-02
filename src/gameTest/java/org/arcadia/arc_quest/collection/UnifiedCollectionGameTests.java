package org.arcadia.arc_quest.collection;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.guide.registry.ArcQuestGuideContent;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.CollectionEntryRewardService;
import org.arcadia.arc_quest.quest.logic.CollectionRecordService;
import org.arcadia.arc_quest.quest.logic.CollectionSheetService;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.network.QuestRejectCodeDictionary;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
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
