package org.com.arc_quest.trade.gacha.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.trade.gacha.api.GachaItem;
import org.com.arc_quest.trade.offer.ItemTradeOffer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final Map<UUID, PendingDrawData> PENDING_DRAWS = new ConcurrentHashMap<>();
    
    /**
     * 暂存抽奖结果（不发放奖励）。
     * <p>
     * 使用原子写入避免同一玩家的待确认结果被并发覆盖。
     * 
     * @param player 玩家
     * @param shopId 商店ID
     * @param drawnItem 抽中的物品
     * @param actualCount 本次抽奖权威数量
     * @param pityTriggered 是否触发保底
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
        if (PENDING_DRAWS.putIfAbsent(playerId, data) != null) {
            Arc_quest.LOGGER.warn(
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
    public static boolean confirmAndGrant(ServerPlayer player) {
        UUID playerId = player.getUUID();
        
        // 【原子操作】remove() 保证只有一个线程能拿到数据
        PendingDrawData data = PENDING_DRAWS.remove(playerId);
        
        if (data == null) {
            // 可能是重复确认或已过期被清理
            return false;
        }
        
        // 检查是否过期（30秒超时）
        long elapsed = System.currentTimeMillis() - data.timestamp;
        if (elapsed > 30000) {
            Arc_quest.LOGGER.warn(
                "[PendingDraw] Player {}'s pending draw expired ({}ms ago)",
                player.getName().getString(), elapsed
            );
            return false;
        }
        
        // 【性能优化】10%概率触发全量清理，避免内存泄漏
        if (Math.random() < 0.1) {
            cleanupExpired();
        }
        
        // 真正发放奖励
        grantReward(player, data.drawnItem, data.actualCount);
        
        // 【新增】记录抽奖历史到服务端能力（持久化）
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        if (cap != null) {
            cap.addGachaDrawHistory(
                data.shopId,
                data.drawnItem.getItemId(),
                data.drawnItem.getRarity().getName(),
                data.actualCount,
                data.pityTriggered,
                System.currentTimeMillis()
            );
        }
        
        Arc_quest.LOGGER.info(
            "[PendingDraw] Granted reward to player {}: {} x{}",
            player.getName().getString(),
            data.drawnItem.getItemId(),
            data.actualCount
        );
        return true;
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
        long now = System.currentTimeMillis();
        PENDING_DRAWS.entrySet().removeIf(entry -> 
            now - entry.getValue().timestamp > 30000
        );
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
