package org.arcadia.arc_quest.integration.storage;

import com.mojang.authlib.GameProfile;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.inventory.PlayerItemAccess;
import org.arcadia.arc_quest.dialogue.api.DialogueCondition;
import org.arcadia.arc_quest.dialogue.runtime.DialogueEvalContext;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.CollectionRecordService;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.service.QuestOfferService;
import org.arcadia.arc_quest.quest.tracking.QuestEventManager;
import org.arcadia.arc_quest.questplayer.*;
import org.arcadia.arc_quest.trade.api.*;
import org.arcadia.arc_quest.trade.offer.ItemTradeOffer;
import org.arcadia.arc_quest.trade.runtime.TradeTransactionCoordinator;

@PrefixGameTestTemplate(false)
public final class BeyondDimensionsGameTests {
    private static final String TEMPLATE = "jei_empty";
    private static final String BATCH = "arc_quest.beyond_storage";
    private static final ResourceLocation DIAMOND = ResourceLocation.parse("minecraft:diamond");

    record Fixture(GameTestHelper helper, ServerPlayer player, DimensionsNet net) {
        void put(ItemStack stack, long amount) {
            var remainder = net.getUnifiedStorage().insert(new ItemStackKey(stack), amount, false);
            helper.assertTrue(remainder.amount() == 0, "Fixture insertion failed");
        }
        long amount(ItemStack stack) { return net.getUnifiedStorage().getStackByKey(new ItemStackKey(stack)).amount(); }
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "ArcQStorage"));
        ArcQuestPlayerManager.getOrCreate(player);
        return player;
    }

    private static void close(ServerPlayer player) {
        QuestEventManager.onPlayerLogout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));
        ArcQuestPlayerManager.unload(player.getUUID());
        PlayerSessionEpochManager.endSession(player.getUUID());
        player.invalidateCaps();
    }

    static void run(GameTestHelper helper, Consumer<Fixture> test) {
        var actor = player(helper);
        var net = Objects.requireNonNull(DimensionsNet.createNewNetForPlayer(actor, Long.MAX_VALUE, 64));
        try { test.accept(new Fixture(helper, actor, net)); }
        finally { net.destroySelf(); close(actor); }
        helper.succeed();
    }

    private static QuestRuntimeData install(ServerPlayer player, QuestDefinition definition) {
        var snapshot = new LinkedHashMap<>(QuestRegistry.getDatapackSnapshot());
        snapshot.put(definition.getId(), definition);
        QuestRegistry.replaceDatapackSnapshot(snapshot);
        var runtime = new QuestRuntimeData(definition.getId().toString(), "work", 1, 0, 0, 0);
        ArcQuestPlayerManager.get(player).addActiveQuest(runtime);
        return runtime;
    }

    private static QuestDefinition quest(String name, ObjectiveBuilder objective) {
        return QuestBuilder.create("arc_quest:storage/" + name).category(QuestCategory.ADVENTURE)
                .phase(PhaseBuilder.create("work").objective(objective)).build();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Arc_Quest.MOD_ID, batch = BATCH, timeoutTicks = 100)
    public static void durableConstructionPaymentSavesResourcesAndReceiptTogetherAndChargesOnce(GameTestHelper h) {
        run(h, f -> {
            var named = new ItemStack(Items.DIAMOND); named.setHoverName(Component.literal("Construction material"));
            f.put(named, 7);
            var source = PlayerItemAccess.durableSources(f.player).get(0);
            var cost = List.of(new PlayerItemAccess.StackAmount(named, 3));
            String receipt = "construction/" + UUID.randomUUID();
            source.commit(receipt, f.player.getUUID(), cost); source.commit(receipt, f.player.getUUID(), cost);
            h.assertTrue(f.amount(named) == 4 && source.committed(receipt, f.player.getUUID(), cost), "Durable payment replayed");
            var saved = f.net.save(new net.minecraft.nbt.CompoundTag());
            var reloaded = DimensionsNet.load(saved);
            h.assertTrue(((StoragePaymentReceipts) reloaded).arcQuest$payments().contains(receipt)
                    && reloaded.getUnifiedStorage().getStackByKey(new ItemStackKey(named)).amount() == 4,
                    "Reload lost the paired receipt or resources");
            var file = f.player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("data/BDNet_" + f.net.getId() + ".dat");
            try {
                var disk = new org.arcadia.arc_quest.questplayer.persistence.PlayerNbtFiles(128L << 20, 512L << 20).read(file).getCompound("data");
                h.assertTrue(disk.equals(saved), "Durable payment was acknowledged before disk persistence");
            } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
            source.finish(receipt, f.player.getUUID(), cost);
            h.assertTrue(!((StoragePaymentReceipts) f.net).arcQuest$payments().contains(receipt), "Finished receipt leaked");
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = Arc_Quest.MOD_ID, batch = BATCH, timeoutTicks = 100)
    public static void durableRecoveryRefundsOriginalNetworkOnceAfterPrimaryChanges(GameTestHelper h) {
        run(h, f -> {
            f.put(new ItemStack(Items.DIAMOND), 7);
            var source = PlayerItemAccess.durableSources(f.player).get(0);
            var cost = List.of(new PlayerItemAccess.StackAmount(new ItemStack(Items.DIAMOND), 3));
            String receipt = "construction/" + UUID.randomUUID();
            source.commit(receipt, f.player.getUUID(), cost);
            f.put(new ItemStack(Items.EMERALD), 5);
            DimensionsNet.setPrimaryNetForPlayer(f.player, null);
            var recorded = PlayerItemAccess.recoverStorage(f.player.server, source.identity());
            recorded.cancel(receipt, f.player.getUUID(), cost); recorded.cancel(receipt, f.player.getUUID(), cost);
            h.assertTrue(f.amount(new ItemStack(Items.DIAMOND)) == 7 && f.amount(new ItemStack(Items.EMERALD)) == 5,
                    "Recovery refunded wrong network, duplicated or lost unrelated resources");
            h.assertTrue(!recorded.committed(receipt, f.player.getUUID(), cost), "Cancelled payment still paid");
            recorded.finish(receipt, f.player.getUUID(), cost);
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = Arc_Quest.MOD_ID, batch = BATCH, timeoutTicks = 100)
    public static void durableFailureRefundsWholeBundleAndRejectsChangedReceiptOwner(GameTestHelper h) {
        run(h, f -> {
            f.put(new ItemStack(Items.DIAMOND), 3);
            var source = PlayerItemAccess.durableSources(f.player).get(0);
            String receipt = "construction/" + UUID.randomUUID();
            var cost = List.of(new PlayerItemAccess.StackAmount(new ItemStack(Items.DIAMOND), 2),
                    new PlayerItemAccess.StackAmount(new ItemStack(Items.EMERALD), 1));
            boolean rejected = false;
            try { source.commit(receipt, f.player.getUUID(), cost); } catch (IllegalStateException expected) { rejected = true; }
            h.assertTrue(rejected && f.amount(new ItemStack(Items.DIAMOND)) == 3 && !source.committed(receipt, f.player.getUUID(), cost),
                    "Failed bundle left a debit or payment receipt");
            var valid = List.of(cost.get(0)); source.commit(receipt, f.player.getUUID(), valid);
            rejected = false;
            try { source.cancel(receipt, UUID.randomUUID(), valid); } catch (IllegalStateException expected) { rejected = true; }
            h.assertTrue(rejected && f.amount(new ItemStack(Items.DIAMOND)) == 1, "Another payer could redeem receipt");
            source.cancel(receipt, f.player.getUUID(), valid); source.finish(receipt, f.player.getUUID(), valid);
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = Arc_Quest.MOD_ID, batch = BATCH, timeoutTicks = 100)
    public static void countsLongStorageWithoutIntegerOverflow(GameTestHelper h) {
        run(h, f -> {
            f.put(new ItemStack(Items.DIAMOND), (long) Integer.MAX_VALUE + 500);
            f.player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
            h.assertTrue(PlayerItemAccess.count(f.player, stack -> stack.is(Items.DIAMOND)) == Integer.MAX_VALUE, "Count overflow");
            h.assertTrue(QuestEventManager.inventorySnapshot(f.player).get(DIAMOND) == Integer.MAX_VALUE, "Snapshot overflow");
            ItemTradeOffer.cost(Items.DIAMOND, 3).execute(f.player);
            h.assertTrue(f.amount(new ItemStack(Items.DIAMOND)) == (long) Integer.MAX_VALUE + 500, "Backpack was not preferred");
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = Arc_Quest.MOD_ID, batch = BATCH, timeoutTicks = 100)
    public static void cursorIsObservedButCannotPay(GameTestHelper h) {
        run(h, f -> {
            f.player.containerMenu.setCarried(new ItemStack(Items.DIAMOND, 4));
            h.assertTrue(QuestEventManager.inventorySnapshot(f.player).get(DIAMOND) == 4, "Cursor not observed");
            h.assertTrue(!ItemTradeOffer.cost(Items.DIAMOND, 1).canAfford(f.player), "Cursor was spendable");
            h.assertTrue(f.player.containerMenu.getCarried().getCount() == 4, "Cursor changed");
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = Arc_Quest.MOD_ID, batch = BATCH, timeoutTicks = 100)
    public static void tagAndExactNbtUseSameMatcherForPreviewAndPayment(GameTestHelper h) {
        run(h, f -> {
            ItemStack named = new ItemStack(Items.DIAMOND);
            named.setHoverName(Component.literal("Rare specimen"));
            f.put(named, 3);
            f.put(new ItemStack(Items.DIAMOND), 5);
            var exact = new ItemTradeOffer(named, 2, true, null);
            h.assertTrue(exact.previewCost(f.player).owned() == 3, "NBT preview lost identity");
            exact.execute(f.player);
            h.assertTrue(f.amount(named) == 1 && f.amount(new ItemStack(Items.DIAMOND)) == 5, "Wrong variant spent");
            f.put(new ItemStack(Items.OAK_LOG), 2);
            f.put(new ItemStack(Items.BIRCH_LOG), 3);
            var tag = ItemTradeOffer.costTag(ItemTags.LOGS, 5);
            h.assertTrue(tag.canAfford(f.player), "Tag alternatives not aggregated");
            tag.execute(f.player);
            h.assertTrue(f.amount(new ItemStack(Items.OAK_LOG)) + f.amount(new ItemStack(Items.BIRCH_LOG)) == 0, "Tag cost not spent");
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = Arc_Quest.MOD_ID, batch = BATCH, timeoutTicks = 100)
    public static void combinedPaymentAndIdempotentRefundPreserveNbt(GameTestHelper h) {
        run(h, f -> {
            f.player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 2));
            ItemStack named = new ItemStack(Items.DIAMOND);
            named.setHoverName(Component.literal("Kept identity"));
            f.put(named, 7);
            var payment = PlayerItemAccess.remove(f.player, stack -> stack.is(Items.DIAMOND), 5, false);
            h.assertTrue(payment.amount() == 5 && f.player.getInventory().getItem(0).isEmpty() && f.amount(named) == 4, "Combined cost incorrect");
            f.net.getUnifiedStorage().setSlotCapacity(1);
            payment.rollback(); payment.rollback();
            h.assertTrue(f.player.getInventory().getItem(0).getCount() == 2 && f.amount(named) == 7, "Refund lost NBT/capacity or duplicated");
            h.assertTrue(f.net.getUnifiedStorage().slotCapacity == 1, "Refund changed configured capacity");
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = Arc_Quest.MOD_ID, batch = BATCH, timeoutTicks = 100)
    public static void refundPreservesUnrelatedChangesEvenWhenNetworkSlotsAreFull(GameTestHelper h) {
        run(h, f -> {
            f.put(new ItemStack(Items.DIAMOND), 3);
            var storage = f.net.getUnifiedStorage();
            storage.setSlotMaxSize(1);
            var payment = PlayerItemAccess.remove(f.player, stack -> stack.is(Items.DIAMOND), 3, false);
            f.put(new ItemStack(Items.EMERALD), 1);
            storage.setSlotCapacity(1);
            payment.rollback(); payment.rollback();
            h.assertTrue(f.amount(new ItemStack(Items.DIAMOND)) == 3 && f.amount(new ItemStack(Items.EMERALD)) == 1,
                    "Refund lost original payment or overwrote an unrelated change");
            h.assertTrue(storage.slotCapacity == 1 && storage.slotMaxSize == 1, "Refund changed configured limits");
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = Arc_Quest.MOD_ID, batch = BATCH, timeoutTicks = 100)
    public static void insufficientPaymentLeavesBothInventoriesUntouched(GameTestHelper h) {
        run(h, f -> {
            f.player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 2)); f.put(new ItemStack(Items.DIAMOND), 3);
            boolean rejected = false;
            try { ItemTradeOffer.cost(Items.DIAMOND, 6).execute(f.player); } catch (IllegalStateException expected) { rejected = true; }
            h.assertTrue(rejected && f.player.getInventory().getItem(0).getCount() == 2 && f.amount(new ItemStack(Items.DIAMOND)) == 3, "Failed cost removed items");
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = Arc_Quest.MOD_ID, batch = BATCH, timeoutTicks = 100)
    public static void fullRewardInventoryRefundsDimensionalCosts(GameTestHelper h) {
        run(h, f -> {
            for (int slot = 0; slot < f.player.getInventory().items.size(); slot++)
                f.player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            f.put(new ItemStack(Items.DIAMOND), 8);
            var result = new TradeTransactionCoordinator().execute(f.player,
                    List.of(ItemTradeOffer.cost(Items.DIAMOND, 3)), List.of(ItemTradeOffer.reward(Items.EMERALD, 1)));
            h.assertTrue(!result.succeeded() && result.rollbackSucceeded() && f.amount(new ItemStack(Items.DIAMOND)) == 8, "Trade refund failed");
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = Arc_Quest.MOD_ID, batch = BATCH, timeoutTicks = 100)
    public static void sharedMembersCannotDoubleSpendAndUnrelatedPlayerCannotRead(GameTestHelper h) {
        run(h, f -> {
            var member = player(h); var stranger = player(h);
            try {
                f.put(new ItemStack(Items.DIAMOND), 4); f.net.addPlayer(member.getUUID());
                DimensionsNet.setPrimaryNetForPlayer(member, f.net);
                h.assertTrue(PlayerItemAccess.count(member, stack -> stack.is(Items.DIAMOND)) == 4, "Member cannot read");
                h.assertTrue(PlayerItemAccess.count(stranger, stack -> stack.is(Items.DIAMOND)) == 0, "Foreign network leaked");
                ItemTradeOffer.cost(Items.DIAMOND, 3).execute(f.player);
                h.assertTrue(!ItemTradeOffer.cost(Items.DIAMOND, 3).canAfford(member), "Shared cost used stale contents");
                f.net.removePlayer(member.getUUID());
                h.assertTrue(PlayerItemAccess.count(member, stack -> stack.is(Items.DIAMOND)) == 0, "Revoked membership still reads");
            } finally { close(member); close(stranger); }
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = Arc_Quest.MOD_ID, batch = BATCH, timeoutTicks = 100)
    public static void primaryNetworkSwitchDoesNotFabricateHistoricalPickup(GameTestHelper h) {
        run(h, f -> {
            var owner = player(h);
            var second = DimensionsNet.createNewNetForPlayer(owner, 1024, 32);
            var previous = QuestRegistry.getDatapackSnapshot();
            try {
                var definition = quest("primary_switch", ObjectiveBuilder.collect(Items.DIAMOND, 100));
                var runtime = install(f.player, definition);
                QuestEventManager.observeInventory(f.player);
                second.getUnifiedStorage().insert(new ItemStackKey(new ItemStack(Items.DIAMOND)), 10, false);
                second.addPlayer(f.player.getUUID()); DimensionsNet.setPrimaryNetForPlayer(f.player, second);
                QuestEventManager.observeInventory(f.player);
                h.assertTrue(runtime.getObjectiveProgress("work", 0) == 0, "Network switch fabricated pickup");
                second.getUnifiedStorage().insert(new ItemStackKey(new ItemStack(Items.DIAMOND)), 2, false);
                QuestEventManager.observeInventory(f.player);
                h.assertTrue(runtime.getObjectiveProgress("work", 0) == 2, "Fresh network acquisition not counted");
            } finally { QuestRegistry.replaceDatapackSnapshot(previous); second.destroySelf(); close(owner); }
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = Arc_Quest.MOD_ID, batch = BATCH, timeoutTicks = 100)
    public static void movingBetweenBackpackAndNetworkDoesNotCountTwice(GameTestHelper h) {
        run(h, f -> {
            var previous = QuestRegistry.getDatapackSnapshot();
            try {
                var definition = quest("transfer", ObjectiveBuilder.collect(Items.DIAMOND, 100));
                var runtime = install(f.player, definition);
                f.player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 4));
                QuestEventManager.observeInventory(f.player);
                f.player.getInventory().setItem(0, ItemStack.EMPTY); f.put(new ItemStack(Items.DIAMOND), 4);
                QuestEventManager.observeInventory(f.player);
                f.net.getUnifiedStorage().extract(new ItemStackKey(new ItemStack(Items.DIAMOND)), 4, false, false);
                f.player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 4)); QuestEventManager.observeInventory(f.player);
                h.assertTrue(runtime.getObjectiveProgress("work", 0) == 0, "Transfer was counted as pickup");
            } finally { QuestRegistry.replaceDatapackSnapshot(previous); }
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = Arc_Quest.MOD_ID, batch = BATCH, timeoutTicks = 100)
    public static void possessionObjectiveUsesCombinedHoldingsAndDeclines(GameTestHelper h) {
        run(h, f -> {
            var previous = QuestRegistry.getDatapackSnapshot();
            try {
                var runtime = install(f.player, quest("possession", ObjectiveBuilder.collect(Items.DIAMOND, 100).collectMode(CollectMode.POSSESSION)));
                f.put(new ItemStack(Items.DIAMOND), 5); f.player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 2));
                QuestEventManager.refreshPossessionObjectives(f.player);
                h.assertTrue(runtime.getObjectiveProgress("work", 0) == 7, "Possession ignored network");
                f.net.getUnifiedStorage().extract(new ItemStackKey(new ItemStack(Items.DIAMOND)), 3, false, false);
                QuestEventManager.refreshPossessionObjectives(f.player);
                h.assertTrue(runtime.getObjectiveProgress("work", 0) == 4, "Possession did not decline");
            } finally { QuestRegistry.replaceDatapackSnapshot(previous); }
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = Arc_Quest.MOD_ID, batch = BATCH, timeoutTicks = 100)
    public static void questSubmissionConsumesNetworkAndCreditsOnlyPaidItems(GameTestHelper h) {
        run(h, f -> {
            var previous = QuestRegistry.getDatapackSnapshot();
            try {
                var definition = quest("offer", ObjectiveBuilder.offer(Items.DIAMOND, 10));
                var runtime = install(f.player, definition);
                f.put(new ItemStack(Items.DIAMOND), 5); f.player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 2));
                h.assertTrue(QuestOfferService.countOfferable(f.player, definition.getPhase("work").getObjectives().get(0)) == 7, "Offer preview incorrect");
                var result = QuestOfferService.submitOffer(f.player, definition.getId().toString(), "work", 0, 9);
                h.assertTrue(result.accepted() && runtime.getObjectiveProgress("work", 0) == 7
                        && f.amount(new ItemStack(Items.DIAMOND)) == 0, "Submission failed or overcredited");
            } finally { QuestRegistry.replaceDatapackSnapshot(previous); }
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = Arc_Quest.MOD_ID, batch = BATCH, timeoutTicks = 100)
    public static void dialogueInventoryConditionIncludesStorageButHandsDoNot(GameTestHelper h) {
        run(h, f -> {
            f.put(new ItemStack(Items.DIAMOND), 3);
            var context = new DialogueEvalContext(f.player, null, "", null, 0, 0, 0);
            h.assertTrue(DialogueCondition.HoldItem.inInventory("minecraft:diamond", 3).test(context), "Dialogue ignored storage");
            h.assertTrue(!new DialogueCondition.HoldItem("minecraft:diamond", 1).test(context), "Hands condition broadened");
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = Arc_Quest.MOD_ID, batch = BATCH, timeoutTicks = 100)
    public static void storageChangeInvalidatesCachedCountsAndClearPrimaryRemovesAccess(GameTestHelper h) {
        run(h, f -> {
            f.put(new ItemStack(Items.DIAMOND), 3);
            h.assertTrue(PlayerItemAccess.count(f.player, stack -> stack.is(Items.DIAMOND)) == 3, "Initial count");
            f.put(new ItemStack(Items.DIAMOND), 2);
            h.assertTrue(PlayerItemAccess.count(f.player, stack -> stack.is(Items.DIAMOND)) == 5, "Insert cache stale");
            DimensionsNet.clearPrimaryNetForPlayer(f.player);
            h.assertTrue(PlayerItemAccess.count(f.player, stack -> stack.is(Items.DIAMOND)) == 0, "No primary selected still reads");
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = Arc_Quest.MOD_ID, batch = BATCH, timeoutTicks = 100)
    public static void catalogueDiscoversStoredNbtVariantAndHonorsExplicitReset(GameTestHelper h) {
        run(h, f -> {
            var previous = QuestRegistry.getDatapackSnapshot();
            try {
                var subject = new CollectionSubjectSpec(
                        ResourceLocation.parse("arc_quest:item_stack"), DIAMOND, 1, "{quality:rare}",
                        new StackMatchSpec(StackMatchSpec.Mode.NBT_SUBSET, "{quality:rare}"), Map.of());
                var entry = CollectionEntryBuilder.create("arc_quest:storage/rare_specimen").category("gems")
                        .item(Items.DIAMOND).outcome("rare", "Rare")
                        .observation(new CollectionObservationRule("rare", 1, subject, "rare")).build();
                var definition = QuestBuilder.create("arc_quest:storage/catalogue").category(QuestCategory.ADVENTURE)
                        .mode(QuestMode.COLLECTION).collectionConfig(CollectionQuestConfigBuilder.create()
                                .category("gems", "Gems").entry(entry).build())
                        .phase(PhaseBuilder.create("work").collectionSheet(CollectionSheetBuilder.create()
                                .binding(EntryRequirementBuilder.create("specimen", entry.getEntryId()).discovered()))).build();
                install(f.player, definition); CollectionRecordService.rebuildIndex();
                f.put(new ItemStack(Items.DIAMOND), 1);
                QuestEventManager.observeInventory(f.player);
                var records = ArcQuestPlayerManager.get(f.player).getCollectionRecords();
                h.assertTrue(!records.hasOutcome(entry.getEntryId(), "rare"), "Wrong NBT discovered rare outcome");
                f.put(subject.resolveStack(), 1); QuestEventManager.observeInventory(f.player);
                h.assertTrue(records.hasOutcome(entry.getEntryId(), "rare"), "Stored rare variant not discovered");
                records.resetQuest(definition.getId().toString(), Set.of(entry.getEntryId()));
                QuestEventManager.observeInventory(f.player);
                h.assertTrue(!records.isDiscovered(entry.getEntryId()), "Stale storage refilled explicit reset");
                f.net.getUnifiedStorage().extract(new ItemStackKey(subject.resolveStack()), 1, false, false);
                QuestEventManager.observeInventory(f.player);
                f.put(subject.resolveStack(), 1); QuestEventManager.observeInventory(f.player);
                h.assertTrue(records.hasOutcome(entry.getEntryId(), "rare"), "Fresh stored variant failed to rediscover");
            } finally { QuestRegistry.replaceDatapackSnapshot(previous); CollectionRecordService.rebuildIndex(); }
        });
    }


    @GameTest(template = TEMPLATE, templateNamespace = Arc_Quest.MOD_ID, batch = BATCH, timeoutTicks = 100)
    public static void panelPreviewUsesAuthorizedStorageAndRejectsInvalidContexts(GameTestHelper h) {
        run(h, f -> {
            var previous = QuestRegistry.getDatapackSnapshot();
            try {
                var definition = quest("panel_preview", ObjectiveBuilder.offer(Items.DIAMOND, 10));
                install(f.player, definition);
                String id = definition.getId().toString();
                f.put(new ItemStack(Items.DIAMOND), 5);
                f.player.getInventory().setItem(40, new ItemStack(Items.DIAMOND, 2));
                h.assertTrue(QuestOfferService.previewOfferable(f.player, id, "work", 0) == 7, "Panel ignored network/offhand");
                h.assertTrue(QuestOfferService.previewOfferable(f.player, id, "inactive", 0) == 0
                        && QuestOfferService.previewOfferable(f.player, id, "work", -1) == 0
                        && QuestOfferService.previewOfferable(f.player, id, "work", 1) == 0
                        && QuestOfferService.previewOfferable(f.player, "unknown:quest", "work", 0) == 0,
                        "Panel exposed an invalid/inactive objective");
                f.put(new ItemStack(Items.DIAMOND), 3);
                h.assertTrue(QuestOfferService.previewOfferable(f.player, id, "work", 0) == 10, "Preview retained stale count");
                DimensionsNet.setPrimaryNetForPlayer(f.player, null);
                h.assertTrue(QuestOfferService.previewOfferable(f.player, id, "work", 0) == 2, "Preview retained inaccessible storage");
                h.assertTrue(f.amount(new ItemStack(Items.DIAMOND)) == 8, "Preview consumed network items");
            } finally { QuestRegistry.replaceDatapackSnapshot(previous); }
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = Arc_Quest.MOD_ID, batch = BATCH, timeoutTicks = 100)
    public static void panelPreviewAndSubmissionUseTheSameFrozenTag(GameTestHelper h) {
        run(h, f -> {
            var previous = QuestRegistry.getDatapackSnapshot();
            try {
                var tag = ResourceLocation.parse("minecraft:logs");
                var definition = quest("panel_frozen", ObjectiveBuilder.offerTag(tag, 3));
                var runtime = install(f.player, definition);
                runtime.freezeItemTag(tag, List.of(ResourceLocation.parse("minecraft:oak_log")));
                runtime.markItemTagSnapshotComplete();
                f.put(new ItemStack(Items.OAK_LOG), 5); f.put(new ItemStack(Items.BIRCH_LOG), 9);
                String id = definition.getId().toString();
                h.assertTrue(QuestOfferService.previewOfferable(f.player, id, "work", 0) == 5, "Preview used live tag instead of frozen members");
                var result = QuestOfferService.submitOffer(f.player, id, "work", 0, 3);
                h.assertTrue(result.accepted() && result.objectiveReached()
                        && f.amount(new ItemStack(Items.OAK_LOG)) == 2 && f.amount(new ItemStack(Items.BIRCH_LOG)) == 9,
                        "Preview and payment matched different items");
                h.assertTrue(QuestOfferService.previewOfferable(f.player, id, "work", 0) == 0,
                        "Completed objective still advertised payment");
            } finally { QuestRegistry.replaceDatapackSnapshot(previous); }
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = Arc_Quest.MOD_ID, batch = BATCH, timeoutTicks = 100)
    public static void deliverPanelPreviewMatchesPartialPaymentAfterStorageChanges(GameTestHelper h) {
        run(h, f -> {
            var previous = QuestRegistry.getDatapackSnapshot();
            try {
                var definition = quest("panel_deliver", ObjectiveBuilder.deliver(Items.DIAMOND, 10, ResourceLocation.parse("arc_quest:test_npc")));
                var runtime = install(f.player, definition);
                f.put(new ItemStack(Items.DIAMOND), 6);
                String id = definition.getId().toString();
                h.assertTrue(QuestOfferService.previewOfferable(f.player, id, "work", 0) == 6, "DELIVER preview ignored storage");
                f.net.getUnifiedStorage().extract(new ItemStackKey(new ItemStack(Items.DIAMOND)), 4, false, false);
                var result = QuestOfferService.submitOffer(f.player, id, "work", 0, 6);
                h.assertTrue(result.accepted() && !result.objectiveReached() && runtime.getObjectiveProgress("work", 0) == 2
                        && QuestOfferService.previewOfferable(f.player, id, "work", 0) == 0,
                        "Stale preview overcredited delivery");
            } finally { QuestRegistry.replaceDatapackSnapshot(previous); }
        });
    }
}
