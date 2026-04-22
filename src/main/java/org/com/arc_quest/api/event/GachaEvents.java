package org.com.arc_quest.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.trade.gacha.api.GachaItem;

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
             * 不可见（可见性条件不满足）
             */
            NOT_VISIBLE,
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
             * 【新增】无法支付成本
             */
            CANNOT_AFFORD,
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
    
    /**
     * 保底提前触发事件。
     * <p>
     * 当在保底计数未满时抽中保底目标物品/品质时触发。
     * 可用于播放特殊音效、显示特效、成就解锁等。
     */
    public static class PityEarlyTriggerEvent extends Event {
        private final ServerPlayer player;
        private final String shopId;
        private final GachaItem drawnItem;
        private final int currentPityCounter;  // 触发时的保底计数
        private final int pityThreshold;       // 保底阈值
        private final boolean willResetPity;   // 是否会重置保底进度
        
        public PityEarlyTriggerEvent(ServerPlayer player, String shopId,
                                    GachaItem drawnItem,
                                    int currentPityCounter,
                                    int pityThreshold,
                                    boolean willResetPity) {
            this.player = player;
            this.shopId = shopId;
            this.drawnItem = drawnItem;
            this.currentPityCounter = currentPityCounter;
            this.pityThreshold = pityThreshold;
            this.willResetPity = willResetPity;
        }
        
        public ServerPlayer getPlayer() { return player; }
        public String getShopId() { return shopId; }
        public GachaItem getDrawnItem() { return drawnItem; }
        public int getCurrentPityCounter() { return currentPityCounter; }
        public int getPityThreshold() { return pityThreshold; }
        public boolean willResetPity() { return willResetPity; }
        
        /**
         * 获取保底进度百分比（0-100）。
         */
        public int getPityProgressPercent() {
            if (pityThreshold <= 0) return 0;
            return Math.min(100, (currentPityCounter * 100) / pityThreshold);
        }
    }
    
    /**
     * 抽奖限购重置事件。
     * <p>
     * 当冷却过期或自定义条件满足导致限购重置时触发。
     */
    public static class DrawLimitResetEvent extends Event {
        
        /**
         * 重置原因枚举。
         */
        public enum ResetReason {
            /**
             * 冷却过期自动重置
             */
            COOLDOWN_EXPIRED,
            /**
             * 自定义条件满足重置
             */
            CUSTOM_CONDITION
        }
        
        private final ServerPlayer player;
        private final String shopId;
        private final IQuestCapability capability;
        private final ResetReason reason;
        private final int previousDrawCount;  // 重置前的抽奖次数
        
        public DrawLimitResetEvent(ServerPlayer player, String shopId,
                                  IQuestCapability capability,
                                  ResetReason reason,
                                  int previousDrawCount) {
            this.player = player;
            this.shopId = shopId;
            this.capability = capability;
            this.reason = reason;
            this.previousDrawCount = previousDrawCount;
        }
        
        public ServerPlayer getPlayer() { return player; }
        public String getShopId() { return shopId; }
        public IQuestCapability getCapability() { return capability; }
        public ResetReason getReason() { return reason; }
        public int getPreviousDrawCount() { return previousDrawCount; }
        
        /**
         * 检查是否因为冷却过期而重置。
         */
        public boolean isCooldownExpired() {
            return reason == ResetReason.COOLDOWN_EXPIRED;
        }
        
        /**
         * 检查是否因为自定义条件而重置。
         */
        public boolean isCustomCondition() {
            return reason == ResetReason.CUSTOM_CONDITION;
        }
    }
}
