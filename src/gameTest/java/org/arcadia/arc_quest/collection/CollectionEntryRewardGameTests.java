package org.arcadia.arc_quest.collection;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.*;
import org.arcadia.arc_quest.quest.network.QuestRejectCodeDictionary;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder(Arc_Quest.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CollectionEntryRewardGameTests {
    private static final String TEMPLATE = "jei_empty", BATCH = "arc_quest.collection.entry_rewards";
    private CollectionEntryRewardGameTests() {}

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void permanentEntryRewardsQualifyOutsideTasksAndSharedReferencesCannotGrantTwice(GameTestHelper helper) {
        ResourceLocation entryId = ResourceLocation.parse("arc_quest:gametest/lifetime_entry_reward");
        AtomicInteger discovery = new AtomicInteger(), research = new AtomicInteger(), runRewards = new AtomicInteger();
        var entry = CollectionEntryBuilder.create(entryId).category("field")
                .discover(ObjectiveBuilder.custom(entryId, 1).id("discover"))
                .research(ObjectiveBuilder.custom(entryId, 2).id("research"))
                .discoveryReward("first", counter(discovery)).researchReward("study", counter(research))
                .bindingReward("investigation", counter(runRewards)).build();
        var first = quest("arc_quest:gametest/shared_reward_first", entry, false);
        var second = quest("arc_quest:gametest/shared_reward_second", entry, false);
        withInstalled(helper, List.of(first, second), player -> {
            var data = ArcQuestPlayerManager.getOrCreate(player);
            CollectionRecordService.dispatch(player, ObjectiveType.CUSTOM, entryId, 1);
            helper.assertTrue(data.getAllActiveQuests().isEmpty() && data.getCollectionRecords().isRewardUnlocked(entryId, "first"),
                    "Permanent qualification required accepting a task");
            helper.assertTrue(discovery.get() == 0 && research.get() == 0, "Default rewards did not wait for manual claim");
            helper.assertTrue(claim(player, first, "", "first") == QuestRejectCodeDictionary.Code.NOT_ACTIVE,
                    "An unknown quest reference authorized a reward claim");
            helper.assertTrue(QuestProgressHandler.acceptQuest(player, first.getId().toString()), "First shared-reference task did not accept");
            helper.assertTrue(claim(player, first, "", "first") == QuestRejectCodeDictionary.Code.OK, "Earned discovery reward could not be claimed");
            helper.assertTrue(QuestProgressHandler.acceptQuest(player, second.getId().toString()), "Second shared-reference task did not accept");
            helper.assertTrue(claim(player, second, "", "first") == QuestRejectCodeDictionary.Code.COLLECTION_REWARD_ALREADY_CLAIMED
                            && discovery.get() == 1, "Another quest replayed a lifetime reward");
            CollectionRecordService.dispatch(player, ObjectiveType.CUSTOM, entryId, 1);
            helper.assertTrue(claim(player, second, "", "study") == QuestRejectCodeDictionary.Code.OK && research.get() == 1,
                    "Permanent research reward did not use permanent research scope");
            helper.assertTrue(runRewards.get() == 0, "Permanent progress granted an unfinished binding's reward");
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void bindingRewardUsesExactRunSurvivesArchiveAndRepeatsWithoutReissuingPermanentReward(GameTestHelper helper) {
        ResourceLocation entryId = ResourceLocation.parse("arc_quest:gametest/round_entry_reward");
        AtomicInteger permanent = new AtomicInteger(), round = new AtomicInteger();
        var entry = CollectionEntryBuilder.create(entryId).category("field")
                .discover(ObjectiveBuilder.custom(entryId, 1).id("discover"))
                .discoveryReward("permanent", counter(permanent)).bindingReward("round", counter(round)).build();
        var quest = quest("arc_quest:gametest/repeat_entry_reward", entry, true);
        withInstalled(helper, List.of(quest), player -> {
            var data = ArcQuestPlayerManager.getOrCreate(player);
            helper.assertTrue(QuestProgressHandler.acceptQuest(player, quest.getId().toString()), "Repeat task did not accept");
            String firstId = data.getActiveQuest(quest.getId().toString()).getCollectionData().getRunId();
            org.arcadia.arc_quest.quest.tracking.QuestEventManager.notifyCustom(player, entryId);
            org.arcadia.arc_quest.quest.tracking.QuestEventManager.notifyCustom(player, entryId);
            helper.assertTrue(claim(player, quest, "", "round") == QuestRejectCodeDictionary.Code.NOT_ACTIVE,
                    "An omitted run ID could claim this round");
            helper.assertTrue(QuestProgressHandler.confirmManualPhaseAdvance(player, quest.getId().toString(), "survey") == QuestRejectCodeDictionary.Code.OK,
                    "Reward task did not archive");
            helper.assertTrue(claim(player, quest, firstId, "round") == QuestRejectCodeDictionary.Code.OK && round.get() == 1,
                    "Archived earned binding reward could not be claimed");
            helper.assertTrue(claim(player, quest, "", "permanent") == QuestRejectCodeDictionary.Code.OK && permanent.get() == 1,
                    "Permanent reward was unavailable after archive");
            helper.assertTrue(QuestProgressHandler.acceptQuest(player, quest.getId().toString()), "Second run did not accept");
            var next = data.getActiveQuest(quest.getId().toString()); String nextId = next.getCollectionData().getRunId();
            helper.assertTrue(!nextId.equals(firstId) && next.getObjectiveProgress("survey", 0) == 0, "Repeat reused previous action state");
            helper.assertTrue(claim(player, quest, firstId, "round") == QuestRejectCodeDictionary.Code.COLLECTION_REWARD_ALREADY_CLAIMED
                            && !next.getCollectionData().isEntryRewardClaimed("survey", "entry", "round"),
                    "A stale modal consumed the new run's reward");
            helper.assertTrue(claim(player, quest, nextId, "round") == QuestRejectCodeDictionary.Code.COLLECTION_REWARD_NOT_UNLOCKED,
                    "New run reward was unlocked before its actions");
            org.arcadia.arc_quest.quest.tracking.QuestEventManager.notifyCustom(player, entryId);
            org.arcadia.arc_quest.quest.tracking.QuestEventManager.notifyCustom(player, entryId);
            helper.assertTrue(claim(player, quest, nextId, "round") == QuestRejectCodeDictionary.Code.OK && round.get() == 2,
                    "A new run could not independently earn its binding reward");
            helper.assertTrue(claim(player, quest, nextId, "permanent") == QuestRejectCodeDictionary.Code.COLLECTION_REWARD_ALREADY_CLAIMED && permanent.get() == 1,
                    "Repeat reissued the permanent reward");
            helper.assertTrue(QuestRuntimeData.deserializeNBT(next.serializeNBT()).getCollectionData().isEntryRewardClaimed("survey", "entry", "round"),
                    "Run reward receipt was not persisted");
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void unpaidBindingRewardsSurviveLaterTerminalRunsAndPlayerSnapshotReload(GameTestHelper helper) {
        ResourceLocation entryId = ResourceLocation.parse("arc_quest:gametest/unpaid_round_entry_reward");
        AtomicInteger granted = new AtomicInteger();
        var entry = CollectionEntryBuilder.create(entryId).category("field")
                .discover(ObjectiveBuilder.custom(entryId, 1).id("discover"))
                .bindingReward("round", counter(granted)).build();
        var quest = quest("arc_quest:gametest/unpaid_repeat_entry_reward", entry, true);
        withInstalled(helper, List.of(quest), player -> {
            var data = ArcQuestPlayerManager.getOrCreate(player);
            var ids = new java.util.ArrayList<String>();
            for (int i = 0; i < 3; i++) {
                helper.assertTrue(QuestProgressHandler.acceptQuest(player, quest.getId().toString()), "An unpaid prior run blocked repeat acceptance");
                ids.add(data.getActiveQuest(quest.getId().toString()).getCollectionData().getRunId());
                org.arcadia.arc_quest.quest.tracking.QuestEventManager.notifyCustom(player, entryId);
                org.arcadia.arc_quest.quest.tracking.QuestEventManager.notifyCustom(player, entryId);
                helper.assertTrue(QuestProgressHandler.confirmManualPhaseAdvance(player, quest.getId().toString(), "survey") == QuestRejectCodeDictionary.Code.OK,
                        "Repeat did not finish for unpaid archive regression");
            }
            helper.assertTrue(granted.get() == 0 && data.getCollectionArchives().pendingRuns(quest.getId().toString()).size() == 3,
                    "MANUAL rewards were automatically settled or lost with later archives");
            data.deserializeNBT(data.serializeNBT());
            helper.assertTrue(data.getCollectionArchives().pendingRuns(quest.getId().toString()).size() == 3,
                    "Player reload lost earlier unpaid reward runs");
            helper.assertTrue(claim(player, quest, ids.get(0), "round") == QuestRejectCodeDictionary.Code.OK && granted.get() == 1,
                    "The oldest unpaid run was not independently claimable");
            helper.assertTrue(!data.getCollectionArchives().get(quest.getId().toString()).getCollectionData().isEntryRewardClaimed("survey", "entry", "round"),
                    "An old-run claim consumed the latest run's reward");
            helper.assertTrue(claim(player, quest, ids.get(0), "round") != QuestRejectCodeDictionary.Code.OK && granted.get() == 1,
                    "A settled old-run claim was replayed");
            helper.assertTrue(claim(player, quest, ids.get(1), "round") == QuestRejectCodeDictionary.Code.OK
                            && claim(player, quest, ids.get(2), "round") == QuestRejectCodeDictionary.Code.OK && granted.get() == 3,
                    "Remaining unpaid runs could not be settled exactly once");
            helper.assertTrue(data.getCollectionArchives().allRuns(quest.getId().toString()).size() == 1,
                    "Settled earlier runs were not released");
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void automaticPermanentRewardsTriggerOutsideTasksAndReentryOrFailureCannotReplay(GameTestHelper helper) {
        ResourceLocation entryId = ResourceLocation.parse("arc_quest:gametest/auto_entry_reward");
        AtomicInteger first = new AtomicInteger(), tail = new AtomicInteger(), research = new AtomicInteger();
        IReward reentrant = new IReward() {
            public void grant(ServerPlayer player) {
                first.incrementAndGet(); CollectionSheetService.refresh(player);
                throw new IllegalStateException("Expected entry reward failure");
            }
            public String describe() { return "Expected entry reward failure regression"; }
        };
        var entry = CollectionEntryBuilder.create(entryId).category("field")
                .discover(ObjectiveBuilder.custom(entryId, 1).id("discover"))
                .research(ObjectiveBuilder.custom(entryId, 2).id("study"))
                .reward("first", CollectionEntryRewardTrigger.DISCOVERED, EntryRewardGrantMode.AUTO, reentrant, counter(tail))
                .reward("research", CollectionEntryRewardTrigger.RESEARCH_COMPLETE, EntryRewardGrantMode.AUTO, counter(research)).build();
        var quest = quest("arc_quest:gametest/auto_entry_reward_task", entry, false);
        withInstalled(helper, List.of(quest), player -> {
            var data = ArcQuestPlayerManager.getOrCreate(player);
            CollectionRecordService.dispatch(player, ObjectiveType.CUSTOM, entryId, 1);
            helper.assertTrue(data.getAllActiveQuests().isEmpty() && first.get() == 1 && tail.get() == 1 && research.get() == 0,
                    "Automatic first-discovery reward required a task, failed to continue, or granted research early");
            CollectionRecordService.dispatch(player, ObjectiveType.CUSTOM, entryId, 1);
            CollectionRecordService.dispatch(player, ObjectiveType.CUSTOM, entryId, 1);
            helper.assertTrue(first.get() == 1 && tail.get() == 1 && research.get() == 1, "Permanent AUTO reward replayed or ignored the research transition");
            helper.assertTrue(data.getCollectionRecords().isRewardClaimed(entryId, "first") && data.getCollectionRecords().isRewardClaimed(entryId, "research"),
                    "Failed/reentrant automatic reward did not retain lifetime receipts");
        });
    }

    private static QuestRejectCodeDictionary.Code claim(ServerPlayer player, QuestDefinition quest, String runId, String rewardId) {
        return CollectionEntryRewardService.claim(player, ArcQuestPlayerManager.getOrCreate(player), quest.getId().toString(), runId, "survey", "entry", rewardId);
    }
    private static IReward counter(AtomicInteger counter) { return new IReward() {
        public void grant(ServerPlayer player) { counter.incrementAndGet(); }
        public String describe() { return "Entry reward counter"; }
    }; }
    private static QuestDefinition quest(String id, CollectionEntryDefinition entry, boolean repeat) {
        var builder = QuestBuilder.create(id).mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field").entry(entry).build())
                .phase(PhaseBuilder.create("survey").autoAdvanceOnComplete(false)
                        .objective(ObjectiveBuilder.custom(entry.getEntryId(), 2).id("action"))
                        .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("entry", entry.getEntryId()).objective("action"))));
        if (repeat) builder.repeatable(); return builder.build();
    }
    private static void withInstalled(GameTestHelper helper, List<QuestDefinition> quests, java.util.function.Consumer<ServerPlayer> test) {
        var previous = QuestRegistry.getDatapackSnapshot();
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "ArcQEntryRewards"), net.minecraft.server.level.ClientInformation.createDefault());
        try {
            // Synthetic players have no connection; onboarding guide delivery is outside this reward fixture.
            var profile = ArcQuestPlayerManager.getOrCreate(player);
            profile.unlockGuide(org.arcadia.arc_quest.guide.registry.ArcQuestGuideContent.TRACKING_MENU_GUIDE_ID);
            profile.unlockGuide(org.arcadia.arc_quest.guide.registry.ArcQuestGuideContent.PARALLEL_PHASES_GUIDE_ID);
            var next = new LinkedHashMap<>(previous); quests.forEach(quest -> next.put(quest.getId(), quest)); QuestRegistry.replaceDatapackSnapshot(next);
            test.accept(player);
        } finally {
            QuestRegistry.replaceDatapackSnapshot(previous); ArcQuestPlayerManager.unload(player.getUUID());
            PlayerSessionEpochManager.endSession(player.getUUID());
        }
        helper.succeed();
    }
}
