package org.com.arc_quest.trade.gacha;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;
import org.com.arc_quest.quest.capability.IQuestCapability;

import java.util.Map;

/**
 * 抽奖系统事件集合。
 */
public class GachaEvents {
    
    /**
     * 抽奖前事件（可取消）。
     * <p>
     * 允许插件在抽奖执行前进行干预（如检查权限、修改参数等）。
     */
    public static class PreDrawEvent extends Event {
        private final ServerPlayer player;
        private final String shopId;
        private final IQuestCapability capability;
        private int pityCounter;
        private boolean cancelled;
        
        public PreDrawEvent(ServerPlayer player, String shopId, 
                           IQuestCapability capability,
                           int pityCounter) {
            this.player = player;
            this.shopId = shopId;
            this.capability = capability;
            this.pityCounter = pityCounter;
            this.cancelled = false;
        }
        
        public ServerPlayer getPlayer() { return player; }
        public String getShopId() { return shopId; }
        public IQuestCapability getCapability() { return capability; }
        public int getPityCounter() { return pityCounter; }
        public void setPityCounter(int pityCounter) { this.pityCounter = pityCounter; }
        public boolean isCancelled() { return cancelled; }
        public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    }
    
    /**
     * 抽奖后事件。
     * <p>
     * 包含完整的抽奖结果数据，用于日志记录、成就解锁等。
     */
    public static class PostDrawEvent extends Event {
        private final ServerPlayer player;
        private final String shopId;
        private final GachaItem drawnItem;
        private final boolean pityTriggered;
        private final int newPityCounter;
        private final IQuestCapability capability;
        
        public PostDrawEvent(ServerPlayer player, String shopId,
                            GachaItem drawnItem,
                            boolean pityTriggered,
                            int newPityCounter,
                            IQuestCapability capability) {
            this.player = player;
            this.shopId = shopId;
            this.drawnItem = drawnItem;
            this.pityTriggered = pityTriggered;
            this.newPityCounter = newPityCounter;
            this.capability = capability;
        }
        
        public ServerPlayer getPlayer() { return player; }
        public String getShopId() { return shopId; }
        public GachaItem getDrawnItem() { return drawnItem; }
        public boolean isPityTriggered() { return pityTriggered; }
        public int getNewPityCounter() { return newPityCounter; }
        public IQuestCapability getCapability() { return capability; }
    }
    
    /**
     * 奖池刷新事件。
     * <p>
     * 当动态权重发生变化时触发（例如玩家完成任务后概率提升）。
     */
    public static class PoolRefreshEvent extends Event {
        private final ServerPlayer player;
        private final String shopId;
        private final IQuestCapability capability;
        private final Map<String, Integer> oldWeights;
        private final Map<String, Integer> newWeights;
        
        public PoolRefreshEvent(ServerPlayer player, String shopId,
                               IQuestCapability capability,
                               Map<String, Integer> oldWeights,
                               Map<String, Integer> newWeights) {
            this.player = player;
            this.shopId = shopId;
            this.capability = capability;
            this.oldWeights = oldWeights;
            this.newWeights = newWeights;
        }
        
        public ServerPlayer getPlayer() { return player; }
        public String getShopId() { return shopId; }
        public IQuestCapability getCapability() { return capability; }
        public Map<String, Integer> getOldWeights() { return oldWeights; }
        public Map<String, Integer> getNewWeights() { return newWeights; }
    }
    
    /**
     * 抽奖商店打开事件。
     */
    public static class OpenedEvent extends Event {
        private final ServerPlayer player;
        private final String shopId;
        private final IQuestCapability capability;
        
        public OpenedEvent(ServerPlayer player, String shopId, IQuestCapability capability) {
            this.player = player;
            this.shopId = shopId;
            this.capability = capability;
        }
        
        public ServerPlayer getPlayer() { return player; }
        public String getShopId() { return shopId; }
        public IQuestCapability getCapability() { return capability; }
    }
    
    /**
     * 抽奖执行中事件（用于通知 HUD 播放动画）。
     * <p>
     * 在服务端验证通过后、实际抽取前触发，客户端监听此事件开始播放抽奖动画。
     */
    public static class DrawingEvent extends Event {
        private final ServerPlayer player;
        private final String shopId;
        private final IQuestCapability capability;
        private final int currentPityCounter;
        
        public DrawingEvent(ServerPlayer player, String shopId,
                           IQuestCapability capability,
                           int currentPityCounter) {
            this.player = player;
            this.shopId = shopId;
            this.capability = capability;
            this.currentPityCounter = currentPityCounter;
        }
        
        public ServerPlayer getPlayer() { return player; }
        public String getShopId() { return shopId; }
        public IQuestCapability getCapability() { return capability; }
        public int getCurrentPityCounter() { return currentPityCounter; }
    }
    
    /**
     * 抽奖失败事件（对标 TradePurchaseFailedEvent）。
     * <p>
     * 当玩家尝试抽奖但因冷却、限购或条件不满足而失败时触发。
     */
    public static class DrawFailedEvent extends Event {
        
        /**
         * 抽奖失败原因枚举。
         */
        public enum FailReason {
            /**
             * 条件不满足（前置任务、等级等）
             */
            CONDITION_NOT_MET,
            /**
             * 冷却中
             */
            ON_COOLDOWN,
            /**
             * 已达抽奖次数上限
             */
            MAX_DRAWS_REACHED,
            /**
             * 其他未知原因
             */
            UNKNOWN
        }
        
        private final ServerPlayer player;
        private final String shopId;
        private final IQuestCapability capability;
        private final FailReason reason;
        
        public DrawFailedEvent(ServerPlayer player, String shopId, IQuestCapability capability, FailReason reason) {
            this.player = player;
            this.shopId = shopId;
            this.capability = capability;
            this.reason = reason;
        }
        
        public ServerPlayer getPlayer() { return player; }
        public String getShopId() { return shopId; }
        public IQuestCapability getCapability() { return capability; }
        public FailReason getReason() { return reason; }
        
        /**
         * 检查是否因为条件不满足而失败。
         */
        public boolean isConditionNotMet() {
            return reason == FailReason.CONDITION_NOT_MET;
        }
        
        /**
         * 检查是否因为冷却而失败。
         */
        public boolean isOnCooldown() {
            return reason == FailReason.ON_COOLDOWN;
        }
        
        /**
         * 检查是否因为达到抽奖次数上限而失败。
         */
        public boolean isMaxDrawsReached() {
            return reason == FailReason.MAX_DRAWS_REACHED;
        }
    }
}
