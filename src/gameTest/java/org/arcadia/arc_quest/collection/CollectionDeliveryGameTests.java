package org.arcadia.arc_quest.collection;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.guide.registry.ArcQuestGuideContent;
import org.arcadia.arc_quest.mixin.PlayerListSaveInvoker;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.CollectionRunDefinitionStore;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.CollectionEntryRewardService;
import org.arcadia.arc_quest.quest.logic.CollectionRewardDelivery;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.network.QuestRejectCodeDictionary;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.reward.ItemReward;
import org.arcadia.arc_quest.quest.tracking.QuestEventManager;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;
import org.arcadia.arc_quest.questplayer.capability.ArcQuestCapabilities;
import org.arcadia.arc_quest.questplayer.persistence.PlayerNbtFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Full inventories exercise the real mailbox, Forge capability and vanilla player-file commit. */
@GameTestHolder(Arc_Quest.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CollectionDeliveryGameTests {
    private static final String TEMPLATE = "jei_empty", BATCH = "arc_quest.collection.delivery";
    private static final String PHASE = "survey", BINDING = "specimen", REWARD = "sample_payment";
    private static final int REWARD_COUNT = 70;
    private static final PlayerNbtFiles FILES = new PlayerNbtFiles(4 * 1024 * 1024, 8 * 1024 * 1024);

    private CollectionDeliveryGameTests() { }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void fullInventoryKeepsClaimedArchivedRewardUntilDiskReloadAndDeliversExactlyOnce(GameTestHelper helper) {
        QuestDefinition quest = quest("full_inventory_delivery");
        withInstalled(helper, quest, player -> {
            ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
            Set<UUID> dropsBefore = nearbyDrops(player);
            fillInventory(player);
            QuestRuntimeData first = complete(helper, player, quest);
            String firstRun = first.getCollectionData().getRunId();
            helper.assertTrue(claim(player, quest, firstRun) == QuestRejectCodeDictionary.Code.OK,
                    "A completed binding could not authorize its item payment");
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 0
                            && first.getCollectionData().hasPendingEntryRewards()
                            && first.getCollectionData().isEntryRewardClaimed(PHASE, BINDING, REWARD),
                    "Full inventory dropped, silently settled, or failed to retain the claimed delivery");
            helper.assertTrue(nearbyDrops(player).equals(dropsBefore), "A full-inventory reward created ground item entities");
            CompoundTag pendingJournal = read(deliveryPath(player));
            helper.assertTrue(pendingJournal.getCompound("Grants").size() == 1, "Pending delivery was not durable in the real mailbox file");
            CompoundTag pendingGrant = pendingJournal.getCompound("Grants").getCompound(
                    pendingJournal.getCompound("Grants").getAllKeys().iterator().next());
            helper.assertTrue(pendingGrant.getList("Chunks", Tag.TAG_COMPOUND).size() == 2,
                    "A seventy-item payment did not preserve both inventory-size chunks");
            helper.assertTrue(QuestProgressHandler.confirmManualPhaseAdvance(player, quest.getId().toString(), PHASE)
                            == QuestRejectCodeDictionary.Code.OK,
                    "A claimed but undelivered payment prevented normal task completion");
            // A later terminal run forces the first run into the earlier-run archive bucket.
            QuestRuntimeData second = complete(helper, player, quest);
            String secondRun = second.getCollectionData().getRunId();
            helper.assertTrue(!firstRun.equals(secondRun)
                            && QuestProgressHandler.confirmManualPhaseAdvance(player, quest.getId().toString(), PHASE) == QuestRejectCodeDictionary.Code.OK,
                    "The archive fixture did not finish a distinct second investigation");
            QuestRuntimeData retained = data.getCollectionArchives().get(quest.getId().toString(), firstRun);
            helper.assertTrue(retained != null && retained.getCollectionData().hasPendingEntryRewards(),
                    "Claimed delivery-pending flags failed to retain an earlier terminal run");

            CompoundTag saved = saveVanilla(player, data);
            helper.assertTrue(saved.contains("Inventory", Tag.TAG_LIST)
                            && saved.getCompound("ForgeCaps").contains("arc_quest:player_data", Tag.TAG_COMPOUND),
                    "The real PlayerList save did not persist inventory and the ArcQ capability together");
            data = reloadArcQ(player, saved);
            CollectionRewardDelivery.clearRuntime();
            retained = data.getCollectionArchives().get(quest.getId().toString(), firstRun);
            helper.assertTrue(retained != null && retained.getCollectionData().hasPendingEntryRewards(),
                    "Reloading the actual player-file ArcQ snapshot lost the earlier pending delivery");
            clearRewardSpace(player);
            CollectionRewardDelivery.recover(player, data);
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == REWARD_COUNT,
                    "Journal recovery did not deliver exactly seventy owed diamonds");
            helper.assertTrue(!retained.getCollectionData().hasPendingEntryRewards(), "Successful delivery did not clear its pending flag");
            helper.assertTrue(read(deliveryPath(player)).getCompound("Grants").isEmpty(),
                    "Delivered chunks remained authorized in the durable mailbox");
            CompoundTag inventoryCommit = read(playerPath(player));
            helper.assertTrue(savedDiamondCount(inventoryCommit) == REWARD_COUNT
                            && inventoryCommit.getCompound("ForgeCaps").getCompound("arc_quest:player_data")
                            .getCompound("DeliveredCollectionReceipts").size() == 2,
                    "Both item chunks and their matching delivery receipts were not committed in the same player file");
            // Restore the disk receipts, rather than relying on acknowledgements left only in memory.
            data = reloadArcQ(player, inventoryCommit);
            CollectionRewardDelivery.clearRuntime();
            CollectionRewardDelivery.recover(player, data);
            CollectionRewardDelivery.recover(player, data);
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == REWARD_COUNT
                            && nearbyDrops(player).equals(dropsBefore),
                    "Repeated process-style recovery duplicated or dropped already committed payment items");
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void resetRevokesUndeliveredRunAuthorizationBeforeSpaceIsFreed(GameTestHelper helper) {
        QuestDefinition quest = quest("reset_pending_delivery");
        withInstalled(helper, quest, player -> {
            ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
            fillInventory(player);
            QuestRuntimeData run = complete(helper, player, quest);
            helper.assertTrue(claim(player, quest, run.getCollectionData().getRunId()) == QuestRejectCodeDictionary.Code.OK
                            && run.getCollectionData().hasPendingEntryRewards(),
                    "Reset fixture never established an actual undelivered item grant");
            data.resetQuest(quest);
            CompoundTag saved = saveVanilla(player, data);
            data = reloadArcQ(player, saved);
            CollectionRewardDelivery.clearRuntime();
            clearRewardSpace(player);
            CollectionRewardDelivery.recover(player, data);
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 0
                            && read(deliveryPath(player)).getCompound("Grants").isEmpty(),
                    "Reset left the old run's unfulfilled delivery authorization redeemable");
            helper.assertTrue(data.getActiveQuest(quest.getId().toString()) == null
                            && data.getCollectionArchives().allRuns(quest.getId().toString()).isEmpty(),
                    "Reset did not revoke the old runtime and its payment archive");
            QuestRuntimeData fresh = complete(helper, player, quest);
            helper.assertTrue(claim(player, quest, fresh.getCollectionData().getRunId()) == QuestRejectCodeDictionary.Code.OK
                            && player.getInventory().countItem(Items.DIAMOND) == REWARD_COUNT,
                    "A new legitimate investigation could not earn its independent post-reset payment");
            CollectionRewardDelivery.recover(player, data);
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == REWARD_COUNT,
                    "Post-reset recovery replayed the revoked or newly committed payment");
        });
    }

    private static QuestDefinition quest(String suffix) {
        ResourceLocation action = ResourceLocation.parse("arc_quest:gametest/delivery_action_" + suffix);
        var entry = CollectionEntryBuilder.create("arc_quest:gametest/delivery_entry_" + suffix).category("field").build();
        return QuestBuilder.create("arc_quest:gametest/delivery_" + suffix).mode(QuestMode.COLLECTION).repeatable()
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field").entry(entry).build())
                .phase(PhaseBuilder.create(PHASE).autoAdvanceOnComplete(false)
                        .objective(ObjectiveBuilder.custom(action, 1).id("action"))
                        .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create(BINDING, entry.getEntryId())
                                .objective("action").reward(REWARD, new ItemReward(Items.DIAMOND, REWARD_COUNT)))))
                .build();
    }

    private static QuestRuntimeData complete(GameTestHelper helper, ServerPlayer player, QuestDefinition quest) {
        helper.assertTrue(QuestProgressHandler.acceptQuest(player, quest.getId().toString()), "Delivery investigation did not accept");
        var run = ArcQuestPlayerManager.getOrCreate(player).getActiveQuest(quest.getId().toString());
        helper.assertTrue(run != null && run.hasFrozenDefinitionHash(), "Accepted delivery investigation had no immutable definition");
        QuestEventManager.notifyCustom(player, quest.getPhase(PHASE).getObjective("action").getTargetId());
        helper.assertTrue(run.getObjectiveProgress(PHASE, 0) == 1
                        && run.getCollectionData().isBindingComplete(PHASE, BINDING)
                        && run.getCollectionData().isEntryRewardUnlocked(PHASE, BINDING, REWARD),
                "Production objective settlement did not complete and authorize the binding reward");
        return run;
    }

    private static QuestRejectCodeDictionary.Code claim(ServerPlayer player, QuestDefinition quest, String runId) {
        return CollectionEntryRewardService.claim(player, ArcQuestPlayerManager.getOrCreate(player),
                quest.getId().toString(), runId, PHASE, BINDING, REWARD);
    }

    private static void fillInventory(ServerPlayer player) {
        for (int index = 0; index < player.getInventory().items.size(); index++)
            player.getInventory().setItem(index, new ItemStack(Items.STONE, 64));
    }
    private static void clearRewardSpace(ServerPlayer player) {
        player.getInventory().setItem(0, ItemStack.EMPTY);
        player.getInventory().setItem(1, ItemStack.EMPTY);
    }
    private static Set<UUID> nearbyDrops(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8)).stream()
                .map(ItemEntity::getUUID).collect(Collectors.toSet());
    }
    private static Path playerPath(ServerPlayer player) {
        return player.server.getWorldPath(LevelResource.ROOT).resolve("playerdata").resolve(player.getUUID() + ".dat");
    }
    private static Path deliveryPath(ServerPlayer player) {
        return player.server.getWorldPath(LevelResource.ROOT).resolve("data/arc_quest/collection_deliveries").resolve(player.getUUID() + ".dat");
    }
    private static CompoundTag read(Path path) {
        try { return FILES.read(path); }
        catch (IOException failure) { throw new IllegalStateException("Cannot inspect actual delivery fixture file " + path, failure); }
    }
    private static CompoundTag saveVanilla(ServerPlayer player, ArcQuestPlayer data) {
        ArcQuestPlayerManager.persistSnapshotSynchronously(player, data);
        var saver = (PlayerListSaveInvoker) player.server.getPlayerList();
        if (player.connection == null) saver.arcq$playerStorage().save(player);
        else saver.arcq$savePlayer(player);
        if (!Files.isRegularFile(playerPath(player))) throw new IllegalStateException("PlayerList did not save the fixture player");
        return read(playerPath(player));
    }
    private static ArcQuestPlayer reloadArcQ(ServerPlayer player, CompoundTag saved) {
        ArcQuestPlayerManager.unload(player.getUUID());
        var capability = player.getCapability(ArcQuestCapabilities.PLAYER_DATA).resolve().orElseThrow();
        capability.deserializeNBT(saved.getCompound("ForgeCaps").getCompound("arc_quest:player_data"));
        player.getInventory().load(saved.getList("Inventory", Tag.TAG_COMPOUND));
        // Production reload selects the newest checkpoint; the vanilla inventory commit may contain older ArcQ state.
        return ArcQuestPlayerManager.getOrCreate(player);
    }

    private static int savedDiamondCount(CompoundTag saved) {
        int count = 0;
        for (Tag part : saved.getList("Inventory", Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.of((CompoundTag) part);
            if (stack.is(Items.DIAMOND)) count += stack.getCount();
        }
        return count;
    }

    private static void withInstalled(GameTestHelper helper, QuestDefinition quest, Consumer<ServerPlayer> test) {
        if (!CollectionRunDefinitionStore.hasCodeDefinitionFactory(quest.getId(), "delivery-gametest-v1"))
            CollectionRunDefinitionStore.registerCodeDefinitionFactory(quest.getId(), "delivery-gametest-v1", () -> quest, true);
        var previous = QuestRegistry.getDatapackSnapshot();
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "ArcQDeliveryTest"));
        try {
            helper.assertTrue(player.getCapability(ArcQuestCapabilities.PLAYER_DATA).isPresent(),
                    "Real ServerPlayer did not receive the ArcQ capability required by inventory delivery");
            var data = ArcQuestPlayerManager.getOrCreate(player);
            data.unlockGuide(ArcQuestGuideContent.TRACKING_MENU_GUIDE_ID);
            data.unlockGuide(ArcQuestGuideContent.PARALLEL_PHASES_GUIDE_ID);
            var next = new LinkedHashMap<>(previous); next.put(quest.getId(), quest);
            QuestRegistry.replaceDatapackSnapshot(next);
            test.accept(player);
        } finally {
            QuestRegistry.replaceDatapackSnapshot(previous);
            ArcQuestPlayerManager.unload(player.getUUID());
            PlayerSessionEpochManager.endSession(player.getUUID());
            player.invalidateCaps();
        }
        helper.succeed();
    }
}
