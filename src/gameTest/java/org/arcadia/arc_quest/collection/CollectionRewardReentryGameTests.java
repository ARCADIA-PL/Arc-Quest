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
import org.arcadia.arc_quest.quest.api.rule.collection.CompletedEntryCountRule;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.CollectionRuntimeData;
import org.arcadia.arc_quest.quest.logic.CollectionSheetService;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.logic.profile.CollectionQuestEngine;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionRewardResolver;
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
public final class CollectionRewardReentryGameTests {
    private static final String TEMPLATE = "jei_empty";
    private static final String BATCH = "arc_quest.collection";
    private static final ResourceLocation ENTRY = ResourceLocation.parse("arc_quest:gametest/milestone_reentry_entry");
    private CollectionRewardReentryGameTests() {}

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void automaticMilestoneReceiptPreventsRewardCallbackRefreshFromGrantingTwice(GameTestHelper helper) {
        AtomicInteger grants = new AtomicInteger();
        IReward refreshing = new IReward() {
            public void grant(ServerPlayer player) {
                int calls = grants.incrementAndGet();
                // Bound the callback in a broken implementation so the regression fails without a stack overflow.
                if (calls <= 2) CollectionSheetService.refresh(player);
            }
            public String describe() { return "Automatic milestone reentry regression"; }
        };
        QuestDefinition quest = quest("arc_quest:gametest/auto_milestone_reentry", EntryRewardGrantMode.AUTO,
                List.of(refreshing));
        withInstalled(helper, quest, player -> {
            var data = ArcQuestPlayerManager.getOrCreate(player); data.getCollectionRecords().discover(ENTRY);
            helper.assertTrue(QuestProgressHandler.acceptQuest(player, quest.getId().toString()), "Automatic milestone quest did not accept");
            var run = data.getActiveQuest(quest.getId().toString());
            helper.assertTrue(run != null && run.getCollectionData().isRewardClaimed("milestone"),
                    "Automatic milestone did not save its receipt");
            helper.assertTrue(grants.get() == 1, "Automatic milestone reward callback reentered and granted twice");
            CollectionSheetService.refresh(player);
            CollectionRewardResolver.grantNodeRewards(player, run.getCollectionData(),
                    quest.getCollectionConfig().getQuestRewardNodes().get(0));
            helper.assertTrue(grants.get() == 1, "Public helper replayed an already claimed automatic milestone");
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void manualMilestoneCallbackCannotClaimTheSameNodeDuringGrantOrAfterArchive(GameTestHelper helper) {
        AtomicInteger grants = new AtomicInteger(), nestedAccepted = new AtomicInteger(), followingRewards = new AtomicInteger();
        String id = "arc_quest:gametest/manual_milestone_reentry";
        IReward reclaiming = new IReward() {
            public void grant(ServerPlayer player) {
                int calls = grants.incrementAndGet();
                var data = ArcQuestPlayerManager.getOrCreate(player);
                var definition = QuestRegistry.getServerDefinition(ResourceLocation.parse(id));
                var run = data.getActiveQuest(id);
                if (calls <= 2 && CollectionQuestEngine.claimRewardWithResult(player, data, definition, run, "milestone").isOk())
                    nestedAccepted.incrementAndGet();
                CollectionSheetService.refresh(player);
            }
            public String describe() { return "Manual milestone reentry regression"; }
        };
        QuestDefinition quest = quest(id, EntryRewardGrantMode.MANUAL, List.of(reclaiming, counter(followingRewards)));
        withInstalled(helper, quest, player -> {
            var data = ArcQuestPlayerManager.getOrCreate(player); data.getCollectionRecords().discover(ENTRY);
            helper.assertTrue(QuestProgressHandler.acceptQuest(player, id), "Manual milestone quest did not accept");
            var run = data.getActiveQuest(id);
            helper.assertTrue(run != null && run.getCollectionData().isRewardClaimable("milestone"), "Manual milestone did not unlock");
            helper.assertTrue(CollectionQuestEngine.claimRewardWithResult(player, data, quest, run, "milestone").isOk(),
                    "First manual claim was rejected");
            helper.assertTrue(grants.get() == 1 && nestedAccepted.get() == 0 && followingRewards.get() == 1,
                    "Manual callback reentered the claim or prevented the rest of the rewards");
            helper.assertTrue(QuestProgressHandler.confirmManualPhaseAdvance(player, id, "survey") == QuestRejectCodeDictionary.Code.OK,
                    "Manual phase confirmation failed");
            var archive = data.getCollectionArchives().get(id);
            helper.assertTrue(archive != null && archive.getCollectionData().isRewardClaimed("milestone"),
                    "Archive lost the milestone receipt");
            helper.assertTrue(!CollectionQuestEngine.claimRewardWithResult(player, data, quest, archive, "milestone").isOk()
                            && grants.get() == 1 && followingRewards.get() == 1,
                    "Archived milestone allowed a replay");
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void throwingRewardKeepsReceiptContinuesLaterRewardsAndStillPublishesCompletion(GameTestHelper helper) {
        AtomicInteger attempted = new AtomicInteger(), following = new AtomicInteger();
        IReward throwing = new IReward() {
            public void grant(ServerPlayer player) { attempted.incrementAndGet(); throw new IllegalStateException("Expected reward failure regression"); }
            public String describe() { return "Failing milestone callback regression"; }
        };
        QuestDefinition quest = quest("arc_quest:gametest/failing_milestone_callback", EntryRewardGrantMode.AUTO,
                List.of(throwing, counter(following)));
        withInstalled(helper, quest, player -> {
            var data = ArcQuestPlayerManager.getOrCreate(player); data.getCollectionRecords().discover(ENTRY);
            helper.assertTrue(QuestProgressHandler.acceptQuest(player, quest.getId().toString()), "Reward exception interrupted acceptance");
            var run = data.getActiveQuest(quest.getId().toString());
            helper.assertTrue(run != null && run.isPhasePendingManualAdvance("survey")
                            && run.getCollectionData().isRewardClaimed("milestone"),
                    "Reward failure interrupted lifecycle progress or lost the receipt");
            CollectionRewardResolver.grantNodeRewards(player, run.getCollectionData(), quest.getCollectionConfig().getQuestRewardNodes().get(0));
            helper.assertTrue(attempted.get() == 1 && following.get() == 1,
                    "A throwing reward skipped later items or allowed the node to replay");
            helper.assertTrue(CollectionRuntimeData.deserializeNBT(run.getCollectionData().serializeNBT()).isRewardClaimed("milestone"),
                    "Milestone receipt did not survive persistence");
            helper.assertTrue(QuestProgressHandler.confirmManualPhaseAdvance(player, quest.getId().toString(), "survey")
                    == QuestRejectCodeDictionary.Code.OK, "Reward failure prevented final confirmation");
            helper.assertTrue(data.getCollectionArchives().get(quest.getId().toString()) != null, "Failed reward prevented archival");
        });
    }

    private static IReward counter(AtomicInteger count) {
        return new IReward() {
            public void grant(ServerPlayer player) { count.incrementAndGet(); }
            public String describe() { return "Milestone tail reward regression"; }
        };
    }
    private static QuestDefinition quest(String id, EntryRewardGrantMode mode, List<IReward> rewards) {
        return QuestBuilder.create(id).mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field")
                        .entry(CollectionEntryBuilder.create(ENTRY).category("field"))
                        .reward(new CollectionRewardNode("milestone", RewardScope.QUEST, mode, rewards,
                                List.of(new CompletedEntryCountRule(1)), id)).build())
                .phase(PhaseBuilder.create("survey").autoAdvanceOnComplete(false).collectionSheet(CollectionSheetBuilder.create()
                        .binding(EntryRequirementBuilder.create("entry", ENTRY).discovered()))).build();
    }
    private static void withInstalled(GameTestHelper helper, QuestDefinition quest, java.util.function.Consumer<ServerPlayer> test) {
        if (!org.arcadia.arc_quest.quest.data.CollectionRunDefinitionStore.hasCodeDefinitionFactory(quest.getId(), "gametest-v1"))
            org.arcadia.arc_quest.quest.data.CollectionRunDefinitionStore.registerCodeDefinitionFactory(quest.getId(), "gametest-v1", () -> quest, true);
        var previous = QuestRegistry.getDatapackSnapshot();
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "ArcQMilestoneTest"), net.minecraft.server.level.ClientInformation.createDefault());
        try {
            var next = new LinkedHashMap<>(previous); next.put(quest.getId(), quest); QuestRegistry.replaceDatapackSnapshot(next);
            test.accept(player);
        } finally {
            QuestRegistry.replaceDatapackSnapshot(previous);
            ArcQuestPlayerManager.unload(player.getUUID()); PlayerSessionEpochManager.endSession(player.getUUID());
        }
        helper.succeed();
    }
}
