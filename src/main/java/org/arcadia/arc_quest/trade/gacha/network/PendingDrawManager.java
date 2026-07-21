package org.arcadia.arc_quest.trade.gacha.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.core.state.ExpiringStateStore;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
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

    /**
     * 待确认的抽奖结果。
     * Key: player UUID
     * Value: 待确认的抽奖数据
     */
    private static final long PENDING_TTL_MILLIS = 30000L;
    private static final ExpiringStateStore<UUID, PendingDrawData> PENDING_DRAWS =
            CoreProcessors.get().createExpiringStateStore();

    /**
     * 暂存抽奖结果（不发放奖励）。
     * <p>
     * 使用原子写入避免同一玩家的待确认结果被并发覆盖。
     *
     * @param player         玩家
     * @param shopId         商店ID
     * @param drawnItem      抽中的物品
     * @param actualCount    本次抽奖权威数量
     * @param pityTriggered  是否触发保底
     * @param newPityCounter 新的保底计数
     * @return true = 成功暂存，false = 已有待确认数据（拒绝）
     */
    public static boolean storePendingDraw(
            ServerPlayer player,
            String shopId,
            GachaItem drawnItem,
            int actualCount,
            boolean pityTriggered,
            int newPityCounter
    ) {
        UUID playerId = player.getUUID();

        PendingDrawData data = new PendingDrawData(
                shopId,
                drawnItem,
                actualCount,
                pityTriggered,
                newPityCounter,
                System.currentTimeMillis()
        );
        if (!PENDING_DRAWS.putIfAbsent(playerId, data, data.timestamp + PENDING_TTL_MILLIS)) {
            Arc_Quest.LOGGER.warn(
                    "[PendingDraw] Player {} already has pending draw, rejecting new request",
                    player.getName().getString()
            );
            return false;
        }
        return true;
    }

    /**
     * 确认并发放奖励。
     * <p>
     * 【并发安全】使用 remove() 原子操作，确保同一玩家的待确认数据只能被消费一次。
     *
     * @param player 玩家
     * @return true = 成功发放，false = 无待确认数据或已过期
     */
    public static boolean confirmAndGrant(ServerPlayer player, String expectedShopId) {
        UUID playerId = player.getUUID();
        long now = System.currentTimeMillis();
        ExpiringStateStore.TakeResult<PendingDrawData> result = PENDING_DRAWS.take(playerId, now);
        PendingDrawData data = result.value();

        if (result.status() == ExpiringStateStore.TakeStatus.MISSING) {
            return false;
        }

        if (expectedShopId != null && !expectedShopId.isEmpty() && !expectedShopId.equals(data.shopId)) {
            Arc_Quest.LOGGER.warn(
                    "[PendingDraw] Shop mismatch on confirm for player {}: expected={}, actual={}",
                    player.getName().getString(), expectedShopId, data.shopId
            );
            return false;
        }

        long elapsed = now - data.timestamp;
        if (result.status() == ExpiringStateStore.TakeStatus.EXPIRED) {
            Arc_Quest.LOGGER.warn(
                    "[PendingDraw] Player {}'s pending draw expired ({}ms ago)",
                    player.getName().getString(), elapsed
            );
            return false;
        }

        cleanupExpired();
        grantAndRecord(player, data);
        return true;
    }

    public static boolean compensateAndGrant(ServerPlayer player) {
        UUID playerId = player.getUUID();
        long now = System.currentTimeMillis();
        ExpiringStateStore.TakeResult<PendingDrawData> result = PENDING_DRAWS.take(playerId, now);
        PendingDrawData data = result.value();
        if (result.status() == ExpiringStateStore.TakeStatus.MISSING) {
            return false;
        }

        long elapsed = now - data.timestamp;
        if (result.status() == ExpiringStateStore.TakeStatus.EXPIRED) {
            Arc_Quest.LOGGER.warn(
                    "[PendingDraw] Dropping expired pending draw during compensation for player {} ({}ms ago)",
                    player.getName().getString(), elapsed
            );
            return false;
        }

        Arc_Quest.LOGGER.warn(
                "[PendingDraw] Compensating unconfirmed draw reward for player {} in shop {}",
                player.getName().getString(), data.shopId
        );
        cleanupExpired();
        grantAndRecord(player, data);
        return true;
    }

    private static void grantAndRecord(ServerPlayer player, PendingDrawData data) {
        grantReward(player, data.drawnItem, data.actualCount);

        ArcQuestPlayer pdata = ArcQuestPlayerManager.get(player);
        if (pdata != null) {
            pdata.addGachaDrawHistory(
                    data.shopId,
                    data.drawnItem.getItemId(),
                    data.drawnItem.getRarity().getName(),
                    data.actualCount,
                    data.pityTriggered,
                    System.currentTimeMillis()
            );
        }

        Arc_Quest.LOGGER.info(
                "[PendingDraw] Granted reward to player {}: {} x{}",
                player.getName().getString(),
                data.drawnItem.getItemId(),
                data.actualCount
        );
    }

    /**
     * 取消待确认的抽奖（例如玩家关闭界面）。
     *
     * @param player 玩家
     */
    public static void cancelPendingDraw(ServerPlayer player) {
        PENDING_DRAWS.remove(player.getUUID());
    }

    /**
     * 清理过期的待确认数据。
     * <p>
     * 【性能优化】采用懒清理策略，不在Tick中定期调用，
     * 而是在 confirmAndGrant() 时概率触发清理。
     */
    public static void cleanupExpired() {
        PENDING_DRAWS.cleanupExpired(System.currentTimeMillis());
    }

    /**
     * 【新增】清理指定玩家的待确认数据（用于玩家登录时）。
     *
     * @param playerId 玩家UUID
     */
    public static void cleanupPlayer(UUID playerId) {
        PENDING_DRAWS.remove(playerId);
    }

    /**
     * 发放奖励给玩家。
     */
    private static void grantReward(ServerPlayer player, GachaItem item, int actualCount) {
        var reward = item.getReward();
        if (reward instanceof ItemTradeOffer itemReward) {
            ItemStack rewardStack = new ItemStack(itemReward.getItem(), actualCount);

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
