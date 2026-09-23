package org.arcadia.arc_quest.trade.gacha.network;

import org.arcadia.arc_quest.questplayer.interaction.PlayerInteractionGuard;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.core.CoreProcessors;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.questplayer.capability.ArcQuestCapabilities;
import org.arcadia.arc_quest.trade.gacha.api.GachaItem;
import org.arcadia.arc_quest.trade.offer.ItemTradeOffer;

import java.util.UUID;

/**
 * 管理待确认的抽奖结果。
 * <p>
 * 当玩家点击抽奖时，服务端计算结果但暂不发放奖励，
 * 将结果暂存在此管理器中，等待客户端动画完成后发送确认包。
 */
public class PendingDrawManager {

    /** 动画确认超时后尝试补发；已付费结果不能因超时被删除。 */
    private static final long PENDING_TTL_MILLIS = 30000L;
    private static final PendingDrawQueue<PendingDrawData> PENDING_DRAWS = new PendingDrawQueue<>(1024);

    /**
     * 暂存抽奖结果（不发放奖励）。
     * <p>
     * 由服务端主线程占位，拒绝覆盖同一玩家的待确认结果。
     *
     * @param player         玩家
     * @param shopId         商店ID
     * @param drawnItem      抽中的物品
     * @param actualCount    本次抽奖权威数量
     * @param pityTriggered  是否触发保底
     * @param newPityCounter 新的保底计数
     * @return true = 已接收暂存，持久化完成后可确认；false = 队列占用或日志暂不可用
     */
    public static boolean storePendingDraw(
            ServerPlayer player,
            String shopId,
            GachaItem drawnItem,
            int actualCount,
            boolean pityTriggered,
            int newPityCounter
    ) {
        requireServerThread(player);
        if (PlayerInteractionGuard.INSTANCE.isRestoring(player.getUUID()) || !PendingDrawPersistence.admit(player)) return false;
        UUID token = PENDING_DRAWS.reserve(player.getUUID());
        if (token == null) return false;
        try {
            PendingDrawPersistence.prepareExternal(player, token,
                    prepareDraw(shopId, drawnItem, actualCount, pityTriggered, newPityCounter));
            return true;
        } finally {
            endDraw(player, token);
        }
    }

    static UUID beginDraw(ServerPlayer player) {
        requireServerThread(player);
        if (!PendingDrawPersistence.admit(player)) return null;
        return PENDING_DRAWS.reserve(player.getUUID());
    }

    static void endDraw(ServerPlayer player, UUID token) {
        if (!PendingDrawPersistence.isPreparing(player.getUUID(), token)) finishPreparation(player.getUUID(), token);
    }

    static void finishPreparation(UUID player, UUID token) { PENDING_DRAWS.releaseReservation(player, token); }

    static void failDraw(UUID player, UUID token) { PENDING_DRAWS.failReservation(player, token); }

    static void clearReconciled(UUID player, UUID token) {
        if (token.equals(PENDING_DRAWS.token(player))) PENDING_DRAWS.discard(player);
    }

    static void failDraw(ServerPlayer player, UUID token) {
        PENDING_DRAWS.failReservation(player.getUUID(), token);
    }

    static boolean storeReservedDraw(ServerPlayer player, UUID token, String shopId, GachaItem drawnItem,
                                     int actualCount, boolean pityTriggered, int newPityCounter) {
        return storeReservedDraw(player, token,
                prepareDraw(shopId, drawnItem, actualCount, pityTriggered, newPityCounter));
    }

    static PendingDrawData prepareDraw(String shopId, GachaItem drawnItem, int actualCount,
                                      boolean pityTriggered, int newPityCounter) {
        if (shopId == null || shopId.isBlank() || drawnItem == null || drawnItem.getReward() == null
                || drawnItem.getRarity() == null || actualCount <= 0 || newPityCounter < 0) {
            throw new IllegalArgumentException("Invalid pending draw result");
        }
        long now = CoreProcessors.get().time().realTimeMillis();
        return new PendingDrawData(shopId, drawnItem, actualCount, pityTriggered, newPityCounter, now);
    }

    static boolean storeReservedDraw(ServerPlayer player, UUID token, PendingDrawData data) {
        return publishPrepared(player.getUUID(), token, data);
    }

    static boolean publishPrepared(UUID player, UUID token, PendingDrawData data) {
        long now = data.timestamp;
        long dueAt = now > Long.MAX_VALUE - PENDING_TTL_MILLIS ? Long.MAX_VALUE : now + PENDING_TTL_MILLIS;
        return PENDING_DRAWS.publish(player, token, data.shopId, data, dueAt);
    }

    public static boolean confirmAndGrant(ServerPlayer player, String expectedShopId) {
        try (var interaction = PlayerInteractionGuard.INSTANCE.enter(player.getUUID())) {
            if (interaction == null) return false;
            requireServerThread(player);
            if (expectedShopId == null || expectedShopId.isBlank()) return false;
            return deliver(player, expectedShopId);
        }
    }

    public static boolean compensateAndGrant(ServerPlayer player) {
        try (var interaction = PlayerInteractionGuard.INSTANCE.enter(player.getUUID())) {
            if (interaction == null) return false;
            requireServerThread(player);
            return deliver(player, null);
        }
    }

    public static boolean hasPendingDraw(UUID playerId) {
        return PENDING_DRAWS.contains(playerId) || PendingDrawPersistence.hasUnresolved(playerId);
    }

    /** 恢复持有独占保护时结算旧奖励；普通确认入口在此期间被拒绝。 */
    public static boolean settleBeforeRestore(ServerPlayer player) {
        requireServerThread(player);
        if (!PlayerInteractionGuard.INSTANCE.isRestoring(player.getUUID())) {
            throw new IllegalStateException("Pending draw settlement requires an active restore scope");
        }
        boolean settled = deliver(player, null);
        PendingDrawPersistence.requireRestorable(player);
        return settled;
    }

    public static void tick(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Pending draws require the server thread");
        PendingDrawPersistence.tick(server);
        if (server.getTickCount() % 20 != 0) return;
        for (UUID playerId : PENDING_DRAWS.duePlayers(CoreProcessors.get().time().realTimeMillis())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) continue;
            try {
                compensateAndGrant(player);
            } catch (RuntimeException failure) {
                ArcQuestLog.error(ArcQuestLog.Category.GACHA,
                        "Pending draw delivery failed; automatic retries disabled for player {}", playerId, failure);
            }
        }
    }

    public static void shutdown() {
        if (PENDING_DRAWS.size() > 0) {
            ArcQuestLog.error(ArcQuestLog.Category.GACHA,
                    "Server stopped with {} unresolved draw records; inspect earlier payment/delivery errors",
                    PENDING_DRAWS.size());
        }
        PENDING_DRAWS.clear();
        PendingDrawPersistence.shutdown();
    }

    private static void requireServerThread(ServerPlayer player) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Pending draws require the server thread");
    }

    public static void onPlayerSaved(ServerPlayer player, java.nio.file.Path playerFile) {
        requireServerThread(player);
        PendingDrawPersistence.saved(player.server, player.getUUID(), playerFile);
    }

    /** 返回隔离的核查副本，不暴露日志写线程或运行时队列。 */
    public static java.util.List<net.minecraft.nbt.CompoundTag> inspectPendingDraws(MinecraftServer server) {
        return PendingDrawPersistence.inspect(server).stream().map(PendingDrawJournal.Entry::serialize).toList();
    }

    public static void resolvePendingDraw(MinecraftServer server, UUID transactionId, String operator, String note) {
        PendingDrawPersistence.resolve(server, transactionId, operator, note);
    }

    private static boolean deliver(ServerPlayer player, String expectedShop) {
        UUID token = PENDING_DRAWS.token(player.getUUID());
        if (!PendingDrawPersistence.canDeliver(player.getUUID(), token)) return false;
        return PENDING_DRAWS.deliver(player.getUUID(), expectedShop, data -> grantAndRecord(player, token, data));
    }

    private static void grantAndRecord(ServerPlayer player, UUID token, PendingDrawData data) {
        var capability = player.getCapability(ArcQuestCapabilities.PLAYER_DATA).resolve()
                .orElseThrow(() -> new IllegalStateException("Missing player capability for draw receipt"));
        ArcQuestPlayer pdata = ArcQuestPlayerManager.getOrCreate(player);
        grantReward(player, data.drawnItem, data.actualCount);
        pdata.addGachaDrawHistory(
                    data.shopId,
                    data.drawnItem.getItemId(),
                    data.drawnItem.getRarity().getName(),
                    data.actualCount,
                    data.pityTriggered,
                    CoreProcessors.get().time().realTimeMillis()
        );

        capability.recordDeliveredDraw(token);
        ArcQuestPlayerManager.persistSnapshot(player, pdata);
        PendingDrawPersistence.delivered(player.getUUID(), token);
        ArcQuestLog.info(ArcQuestLog.Category.GACHA,
                "Granted reward to player {}: {} x{}",
                player.getName().getString(),
                data.drawnItem.getItemId(),
                data.actualCount
        );
    }

    /**
     * 关闭界面时结算已经就绪的奖励；写入中的已付费结果继续保留。
     *
     * @param player 玩家
     */
    public static void cancelPendingDraw(ServerPlayer player) {
        requireServerThread(player);
        compensateAndGrant(player);
    }

    /**
     * 兼容旧清理入口：为在线玩家补发超时奖励，离线结果仍保留。
     */
    public static void cleanupExpired() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) tick(server);
    }

    /**
     * 保留旧清理入口；已付费/持久化记录必须经核查命令结算，不能静默丢弃。
     *
     * @param playerId 玩家UUID
     */
    public static void cleanupPlayer(UUID playerId) {
        if (!PendingDrawPersistence.hasUnverified(playerId)) PENDING_DRAWS.discard(playerId);
    }

    /**
     * 发放奖励给玩家。
     */
    private static void grantReward(ServerPlayer player, GachaItem item, int actualCount) {
        var reward = item.getReward();
        if (reward instanceof ItemTradeOffer itemReward) {
            ItemStack rewardStack = itemReward.createRewardStack(actualCount);

            if (!player.getInventory().add(rewardStack)) {
                player.drop(rewardStack, false);
            }

            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_PICKUP,
                    SoundSource.PLAYERS, 0.5F, 1.0F);
        } else if (reward != null) {
            reward.execute(player);
        }
    }

    /**
     * 待确认的抽奖数据。
     */
    public static class PendingDrawData {
        public final String shopId;
        public final GachaItem drawnItem;
        public final int actualCount;
        public final boolean pityTriggered;
        public final int newPityCounter;
        public final long timestamp;

        public PendingDrawData(String shopId, GachaItem drawnItem, int actualCount,
                               boolean pityTriggered, int newPityCounter, long timestamp) {
            this.shopId = shopId;
            this.drawnItem = drawnItem;
            this.actualCount = actualCount;
            this.pityTriggered = pityTriggered;
            this.newPityCounter = newPityCounter;
            this.timestamp = timestamp;
        }
    }
}
