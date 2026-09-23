package org.arcadia.arc_quest.trade.gacha.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;
import org.arcadia.arc_quest.questplayer.capability.ArcQuestCapabilities;
import org.arcadia.arc_quest.questplayer.interaction.PlayerInteractionGuard;
import org.arcadia.arc_quest.trade.network.RejectCodeDictionary;
import org.arcadia.arc_quest.trade.offer.ItemTradeOffer;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.arcadia.arc_quest.trade.gacha.network.PendingDrawJournal.Stage.*;

/** 游戏主线程上的持久化关卡；跨 tick 只保留 UUID、会话代号、定义和数据副本。 */
final class PendingDrawPersistence {
    private static PendingDrawJournal journal;
    private static Path directory;
    private static CompletableFuture<List<PendingDrawJournal.Entry>> loading;
    private static boolean unavailable;
    private static boolean closing;
    private static final Map<UUID, Work> preparing = new HashMap<>();
    private static final Map<UUID, PendingDrawJournal.Entry> records = new HashMap<>();
    private static final Map<UUID, CompletableFuture<Void>> deliveryWrites = new HashMap<>();
    private static final Map<UUID, CompletableFuture<List<UUID>>> savedPlayers = new HashMap<>();
    private static final Map<UUID, CompletableFuture<Void>> resolutions = new HashMap<>();
    private static final java.util.Set<UUID> saveWarnings = new java.util.HashSet<>();
    private static final java.util.Set<UUID> saveAgain = new java.util.HashSet<>();

    private PendingDrawPersistence() { }

    static boolean admit(ServerPlayer player) {
        initialize(player.server);
        if (!unavailable && loading == null) {
            var tokens = records.values().stream().filter(entry -> entry.playerId().equals(player.getUUID()))
                    .map(PendingDrawJournal.Entry::transactionId).collect(java.util.stream.Collectors.toSet());
            player.getCapability(ArcQuestCapabilities.PLAYER_DATA).ifPresent(capability -> capability.retainDeliveryReceipts(tokens));
        }
        return !unavailable && loading == null && records.size() < 1024 && !hasUnresolved(player.getUUID());
    }

    static boolean hasUnresolved(UUID player) {
        return records.values().stream().anyMatch(entry -> entry.playerId().equals(player) && entry.stage() != DELIVERED);
    }

    static boolean hasUnverified(UUID player) {
        return records.values().stream().anyMatch(entry -> entry.playerId().equals(player));
    }

    static boolean isPreparing(UUID player, UUID token) {
        Work work = preparing.get(player);
        return work != null && work.commit.entry().transactionId().equals(token);
    }

    static boolean canDeliver(UUID playerId, UUID token) {
        var entry = records.get(token);
        return !unavailable && loading == null && entry != null && entry.playerId().equals(playerId)
                && entry.stage() == DELIVERING && !isPreparing(playerId, token) && !resolutions.containsKey(token);
    }

    static void requireRestorable(ServerPlayer player) {
        initialize(player.server);
        if (loading != null || unavailable || hasUnverified(player.getUUID())) {
            throw new IllegalStateException("Draw journal has unverified records; save or reconcile before restoring progress");
        }
    }

    static void prepare(ServerPlayer player, UUID token, GachaDrawService.PreparedDraw plan) {
        prepare(player, token, plan, plan.pending());
    }

    static void prepareExternal(ServerPlayer player, UUID token, PendingDrawManager.PendingDrawData data) {
        prepare(player, token, null, data);
    }

    private static void prepare(ServerPlayer player, UUID token, GachaDrawService.PreparedDraw plan,
                                PendingDrawManager.PendingDrawData data) {
        if (journal == null || unavailable || loading != null) throw new IllegalStateException("Draw journal unavailable");
        if (preparing.containsKey(player.getUUID())) throw new IllegalStateException("Duplicate draw preparation");
        CompoundTag payload = new CompoundTag();
        payload.putString("ItemId", data.drawnItem.getItemId());
        payload.putString("Rarity", data.drawnItem.getRarity().name());
        payload.putInt("Count", data.actualCount);
        payload.putBoolean("PityTriggered", data.pityTriggered);
        payload.putInt("NewPityCounter", data.newPityCounter);
        payload.putLong("CreatedAt", data.timestamp);
        payload.putBoolean("ExternallyPaid", plan == null);
        var reward = data.drawnItem.getReward();
        payload.putString("RewardType", reward.getType());
        payload.putString("RewardClass", reward.getClass().getName());
        if (reward instanceof ItemTradeOffer item) {
            payload.put("ItemSnapshot", item.createRewardStack(data.actualCount).save(new CompoundTag()));
        }
        if (plan != null) {
            var costs = plan.shop().getDrawCosts();
            if (costs.size() > 256) throw new IllegalArgumentException("Too many draw costs to journal");
            net.minecraft.nbt.ListTag costDescriptions = new net.minecraft.nbt.ListTag();
            for (var cost : costs) {
                CompoundTag summary = new CompoundTag();
                summary.putString("Type", cost.getType());
                summary.putString("Class", cost.getClass().getName());
                // 动态成本可随支付时状态改变，预览只作核查线索，不能据此自动退款。
                summary.putInt("PreviewAmount", cost.getDisplayAmount());
                String description = cost.describe().getString();
                summary.putString("PreviewDescription", description.substring(0, Math.min(description.length(), 2048)));
                costDescriptions.add(summary);
            }
            payload.put("CostPreviews", costDescriptions);
        }
        var entry = new PendingDrawJournal.Entry(token, player.getUUID(), data.shopId, PREPARED, payload);
        long epoch = PlayerSessionEpochManager.getOrCreate(player);
        Work work = new Work(new PendingDrawCommit(journal, entry), epoch, player.getId(),
                player.level().dimension().location().toString(), plan, data);
        preparing.put(player.getUUID(), work);
        records.put(token, entry);
    }

    static void tick(MinecraftServer server) {
        initialize(server);
        if (loading != null) {
            if (!loading.isDone()) return;
            try {
                for (var entry : loading.join()) records.put(entry.transactionId(), entry);
                if (!records.isEmpty()) ArcQuestLog.error(ArcQuestLog.Category.GACHA,
                        "Recovered {} draw journal records at {}; unresolved payments/deliveries require reconciliation",
                        records.size(), directory);
                loading = null;
                for (var entry : records.values()) {
                    if (entry.stage() == DELIVERED) saved(server, entry.playerId(), playerFile(server, entry.playerId()));
                }
            } catch (RuntimeException failure) {
                loading = null;
                disable("load", failure);
            }
        }
        for (Work work : List.copyOf(preparing.values())) {
            var entry = work.commit.entry();
            try {
                if (unavailable) throw new IllegalStateException("Draw persistence unavailable; payment suspended");
                boolean finished = work.commit.poll(() -> commitPayment(server, work));
                records.put(entry.transactionId(), work.commit.entry());
                if (finished) {
                    preparing.remove(entry.playerId(), work);
                    if (work.commit.canceled()) records.remove(entry.transactionId());
                    // 保留原扩展事件顺序：事件回调期间尚不能重入领取这一笔奖励。
                    try { notifyResult(server, work); }
                    finally { PendingDrawManager.finishPreparation(entry.playerId(), entry.transactionId()); }
                }
            } catch (RuntimeException failure) {
                preparing.remove(entry.playerId(), work);
                PendingDrawManager.failDraw(entry.playerId(), entry.transactionId());
                PendingDrawManager.finishPreparation(entry.playerId(), entry.transactionId());
                if (!(failure instanceof CompletionException) && !unavailable) {
                    deliveryWrites.put(entry.transactionId(), journal.advance(entry.transactionId(), entry.stage(), REVIEW));
                    records.put(entry.transactionId(), entry.at(REVIEW));
                } else disable("write", failure);
                ArcQuestLog.error(ArcQuestLog.Category.GACHA,
                        "Draw requires reconciliation: transaction={}, player={}, shop={}",
                        entry.transactionId(), entry.playerId(), entry.shopId(), failure);
                ServerPlayer player = server.getPlayerList().getPlayer(entry.playerId());
                if (player != null) {
                    try { GachaDrawService.sendFailure(player, entry.shopId(), RejectCodeDictionary.Code.TRANSACTION_FAILED); }
                    catch (RuntimeException notificationFailure) {
                        ArcQuestLog.warn(ArcQuestLog.Category.GACHA, "Failed to notify draw failure for {}", entry.playerId(), notificationFailure);
                    }
                }
            }
        }
        for (var entry : List.copyOf(deliveryWrites.entrySet())) {
            if (!entry.getValue().isDone()) continue;
            deliveryWrites.remove(entry.getKey());
            try { entry.getValue().join(); }
            catch (RuntimeException failure) { disable("delivery", failure); }
        }
        for (var entry : List.copyOf(savedPlayers.entrySet())) {
            if (!entry.getValue().isDone()) continue;
            savedPlayers.remove(entry.getKey());
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                for (UUID token : entry.getValue().join()) {
                    records.remove(token);
                    if (player != null) player.getCapability(ArcQuestCapabilities.PLAYER_DATA)
                            .ifPresent(capability -> capability.acknowledgeDeliveredDraw(token));
                }
                saveWarnings.remove(entry.getKey());
            } catch (CompletionException failure) {
                if (failure.getCause() instanceof PendingDrawJournal.PlayerSaveReadException) {
                    if (saveWarnings.add(entry.getKey())) ArcQuestLog.warn(ArcQuestLog.Category.GACHA,
                            "Cannot verify saved draw receipts for {}; retaining records until next player save", entry.getKey(), failure);
                } else disable("verify_player_save", failure);
            }
            if (saveAgain.remove(entry.getKey())) saved(server, entry.getKey(), playerFile(server, entry.getKey()));
        }
        for (var resolution : List.copyOf(resolutions.entrySet())) {
            if (!resolution.getValue().isDone()) continue;
            resolutions.remove(resolution.getKey());
            try {
                resolution.getValue().join();
                var entry = records.remove(resolution.getKey());
                if (entry != null) {
                    PendingDrawManager.clearReconciled(entry.playerId(), entry.transactionId());
                    ArcQuestLog.warn(ArcQuestLog.Category.GACHA, "Draw reconciliation persisted and archived: {}", entry.transactionId());
                }
            } catch (RuntimeException failure) { disable("reconcile", failure); }
        }
    }

    private static PendingDrawCommit.Decision commitPayment(MinecraftServer server, Work work) {
        var entry = work.commit.entry();
        ServerPlayer player = server.getPlayerList().getPlayer(entry.playerId());
        if (work.plan != null) {
            if (player == null || !PlayerSessionEpochManager.matches(player, work.epoch)
                    || player.getId() != work.entityId || !player.level().dimension().location().toString().equals(work.dimension)
                    || System.nanoTime() - work.createdNanos > java.util.concurrent.TimeUnit.SECONDS.toNanos(30)) {
                return PendingDrawCommit.Decision.CANCEL;
            }
            try (var scope = PlayerInteractionGuard.INSTANCE.enter(entry.playerId())) {
                if (scope == null) return PendingDrawCommit.Decision.WAIT;
                work.result = GachaDrawService.commitPrepared(player, entry.transactionId(), work.plan);
            }
            if (!work.result.succeeded()) return PendingDrawCommit.Decision.CANCEL;
        } else if (!PendingDrawManager.publishPrepared(entry.playerId(), entry.transactionId(), work.data)) {
            throw new IllegalStateException("Externally paid draw could not be published");
        }
        return PendingDrawCommit.Decision.PAID;
    }

    private static void notifyResult(MinecraftServer server, Work work) {
        var entry = work.commit.entry();
        ServerPlayer player = server.getPlayerList().getPlayer(entry.playerId());
        if (player != null && work.result != null && PlayerSessionEpochManager.matches(player, work.epoch)) {
            try (var scope = PlayerInteractionGuard.INSTANCE.enter(entry.playerId())) {
                if (scope == null) return;
                GachaDrawService.reportResolution(player, entry.shopId(), ArcQuestPlayerManager.getOrCreate(player), work.result);
            } catch (RuntimeException failure) {
                ArcQuestLog.error(ArcQuestLog.Category.GACHA, "Draw notification failed for {}", entry.playerId(), failure);
            }
        }
    }

    static void delivered(UUID playerId, UUID token) {
        var entry = records.get(token);
        if (entry == null || !entry.playerId().equals(playerId) || entry.stage() != DELIVERING) {
            throw new IllegalStateException("Missing durable draw delivery authorization");
        }
        deliveryWrites.put(token, journal.advance(token, DELIVERING, DELIVERED));
        records.put(token, entry.at(DELIVERED));
    }

    static List<PendingDrawJournal.Entry> inspect(MinecraftServer server) {
        initialize(server);
        if (loading != null) throw new IllegalStateException("抽卡日志仍在读取，请稍后重试");
        if (unavailable) throw new IllegalStateException("抽卡日志不可用，请检查服务器日志并修复存储后重启");
        return records.values().stream().sorted(java.util.Comparator.comparing(entry -> entry.transactionId().toString())).toList();
    }

    static void resolve(MinecraftServer server, UUID token, String operator, String note) {
        inspect(server);
        var entry = records.get(token);
        if (entry == null) throw new IllegalArgumentException("未找到待核查事务 " + token);
        if (isPreparing(entry.playerId(), token) || deliveryWrites.containsKey(token)
                || resolutions.containsKey(token) || savedPlayers.containsKey(entry.playerId())) {
            throw new IllegalStateException("事务仍在处理或核验中，请稍后重试");
        }
        try (var scope = PlayerInteractionGuard.INSTANCE.restore(entry.playerId())) {
            if (scope == null) throw new IllegalStateException("玩家仍有交互正在执行，请稍后重试");
            var resolution = journal.resolve(token, entry.stage(), operator, note, System.currentTimeMillis());
            PendingDrawManager.failDraw(entry.playerId(), token);
            resolutions.put(token, resolution);
        }
        ArcQuestLog.warn(ArcQuestLog.Category.GACHA,
                "Operator {} requested reconciliation of draw {} for {}: {}", operator, token, entry.playerId(), note);
    }

    static void saved(MinecraftServer server, UUID playerId, Path playerFile) {
        if (journal == null || loading != null || unavailable) return;
        if (savedPlayers.containsKey(playerId)) { saveAgain.add(playerId); return; }
        if (resolutions.keySet().stream().anyMatch(token -> records.get(token).playerId().equals(playerId))) return;
        if (!records.values().stream().anyMatch(entry -> entry.playerId().equals(playerId) && entry.stage() == DELIVERED)) return;
        savedPlayers.put(playerId, journal.acknowledgeSavedPlayer(playerId, playerFile));
    }

    static void shutdown() {
        closing = true;
        if (!unavailable) {
            for (Work work : preparing.values()) {
                if (work.plan != null) work.commit.cancelUnpaid();
            }
        }
        if (journal != null) {
            try { journal.shutdown(Duration.ofSeconds(5)); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); disable("shutdown", interrupted); }
            catch (java.util.concurrent.TimeoutException timeout) { disable("shutdown", timeout); }
            if (journal.isTerminated()) journal = null;
        }
        preparing.clear();
        records.clear();
        deliveryWrites.clear();
        savedPlayers.clear();
        resolutions.clear();
        saveWarnings.clear();
        saveAgain.clear();
        loading = null;
    }

    private static void initialize(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Draw journal coordination requires server thread");
        if (closing) {
            if (journal != null && !journal.isTerminated()) { unavailable = true; return; }
            journal = null;
            closing = false;
        }
        if (journal != null) return;
        directory = server.getWorldPath(LevelResource.ROOT).resolve("data/arc_quest/draw_journal").toAbsolutePath().normalize();
        journal = new PendingDrawJournal(directory, 1024);
        loading = journal.loaded();
        unavailable = false;
    }

    private static Path playerFile(MinecraftServer server, UUID playerId) {
        return server.getWorldPath(LevelResource.ROOT).resolve("playerdata").resolve(playerId + ".dat");
    }

    private static void disable(String stage, Throwable failure) {
        if (!unavailable) ArcQuestLog.error(ArcQuestLog.Category.GACHA, "Draw journal disabled at {}: {}", stage, directory, failure);
        unavailable = true;
    }

    private static final class Work {
        final PendingDrawCommit commit;
        final long epoch;
        final int entityId;
        final String dimension;
        final long createdNanos = System.nanoTime();
        final GachaDrawService.PreparedDraw plan;
        final PendingDrawManager.PendingDrawData data;
        GachaDrawService.DrawResolution result;

        Work(PendingDrawCommit commit, long epoch, int entityId, String dimension, GachaDrawService.PreparedDraw plan,
             PendingDrawManager.PendingDrawData data) {
            this.commit = commit;
            this.epoch = epoch;
            this.entityId = entityId;
            this.dimension = dimension;
            this.plan = plan;
            this.data = data;
        }
    }
}
