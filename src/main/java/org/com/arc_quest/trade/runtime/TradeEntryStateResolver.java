package org.com.arc_quest.trade.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.dialogue.util.TimeSanitizer;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.trade.api.TradeEntry;
import org.slf4j.Logger;

/**
 * 交易商品状态解析器 - 统一判断和设置商品的各种状态
 * <p>
 * 核心原则：
 * <ul>
 *   <li>有限购 + 有冷却：冷却仅在限购满时生效</li>
 *   <li>无限购 + 有冷却：每次购买都触发冷却</li>
 *   <li>有限购 + 无冷却：仅检查限购</li>
 * </ul>
 */
public final class TradeEntryStateResolver {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private TradeEntryStateResolver() {}
    
    /**
     * 检查商品是否在冷却中
     * 
     * @param player 玩家对象（用于获取时间）
     * @param cap 玩家能力数据
     * @param shopId 商店ID
     * @param entry 商品定义
     * @return true 如果在冷却中
     */
    public static boolean isOnCooldown(ServerPlayer player, IQuestCapability cap, String shopId, TradeEntry entry) {
        if (!entry.hasCooldown()) {
            return false;
        }
        
        if (entry.hasLimit()) {
            int currentCount = cap.getTradePurchaseCount(shopId, entry.getEntryId());
            if (currentCount < entry.getMaxPurchases()) {
                return false;
            }
        }
        
        long[] times = TimeSanitizer.getAllTimes(player);
        long nowRealTime = times[0];
        long nowGameTime = times[1];
        long nowDayTime = times[2];
        
        boolean onCooldown = cap.isTradeOnCooldown(
                shopId, entry.getEntryId(),
                entry.getCooldownType(), (int) entry.getCooldownValue(),
                entry.getResetTimeTicks(),
                nowRealTime, nowGameTime, nowDayTime
        );
        
        LOGGER.debug("[Trade-State] Cooldown check: entry={}, hasLimit={}, count={}/{}, onCooldown={}",
                entry.getEntryId(), entry.hasLimit(), 
                cap.getTradePurchaseCount(shopId, entry.getEntryId()),
                entry.getMaxPurchases(), onCooldown);
        
        return onCooldown;
    }
    
    /**
     * 检查商品是否达到限购上限
     * 
     * @param cap 玩家能力数据
     * @param shopId 商店ID
     * @param entry 商品定义
     * @return true 如果已达到限购上限
     */
    public static boolean isPurchaseLimitReached(IQuestCapability cap, String shopId, TradeEntry entry) {
        if (!entry.hasLimit()) {
            return false;
        }
        
        int currentCount = cap.getTradePurchaseCount(shopId, entry.getEntryId());
        return currentCount >= entry.getMaxPurchases();
    }
    
    /**
     * 检查商品是否可购买（综合判断）
     * 
     * @param player 玩家对象（用于获取时间）
     * @param cap 玩家能力数据
     * @param shopId 商店ID
     * @param entry 商品定义
     * @return true 如果可以购买
     */
    public static boolean canPurchase(ServerPlayer player, IQuestCapability cap, String shopId, TradeEntry entry) {
        if (isPurchaseLimitReached(cap, shopId, entry)) {
            LOGGER.debug("[Trade-State] Purchase blocked: limit reached for {}", entry.getEntryId());
            return false;
        }
        
        if (isOnCooldown(player, cap, shopId, entry)) {
            LOGGER.debug("[Trade-State] Purchase blocked: on cooldown for {}", entry.getEntryId());
            return false;
        }
        
        return true;
    }
    
    /**
     * 检查是否应该重置购买次数（基于冷却过期）
     * 
     * @param player 玩家对象（用于获取时间）
     * @param cap 玩家能力数据
     * @param shopId 商店ID
     * @param entry 商品定义
     * @return true 如果应该重置
     */
    public static boolean shouldResetByCooldown(ServerPlayer player, IQuestCapability cap, String shopId, TradeEntry entry) {
        if (!entry.hasCooldown()) {
            return false;
        }
        
        if (entry.hasLimit()) {
            int currentCount = cap.getTradePurchaseCount(shopId, entry.getEntryId());
            if (currentCount < entry.getMaxPurchases()) {
                return false;
            }
        }
        
        long[] times = TimeSanitizer.getAllTimes(player);
        long nowRealTime = times[0];
        long nowGameTime = times[1];
        long nowDayTime = times[2];
        
        boolean onCooldown = cap.isTradeOnCooldown(
                shopId, entry.getEntryId(),
                entry.getCooldownType(), (int) entry.getCooldownValue(),
                entry.getResetTimeTicks(),
                nowRealTime, nowGameTime, nowDayTime
        );
        
        boolean shouldReset = !onCooldown;
        
        if (shouldReset) {
            LOGGER.info("[Trade-State] Cooldown expired, should reset: entry={}, hasLimit={}",
                    entry.getEntryId(), entry.hasLimit());
        }
        
        return shouldReset;
    }
    
    /**
     * 检查是否需要记录冷却时间
     * 
     * @param cap 玩家能力数据
     * @param shopId 商店ID
     * @param entry 商品定义
     * @return true 如果购买后应该记录冷却
     */
    public static boolean shouldRecordCooldown(IQuestCapability cap, String shopId, TradeEntry entry) {
        if (!entry.hasCooldown()) {
            return false;
        }
        
        if (!entry.hasLimit()) {
            return true;
        }
        
        int currentCount = cap.getTradePurchaseCount(shopId, entry.getEntryId());
        int newCount = currentCount + 1;
        boolean shouldRecord = newCount >= entry.getMaxPurchases();
        
        if (shouldRecord) {
            LOGGER.info("[Trade-State] Purchase limit reached, will record cooldown: entry={}, count={}/{}",
                    entry.getEntryId(), newCount, entry.getMaxPurchases());
        }
        
        return shouldRecord;
    }
    
    /**
     * 记录购买（增加购买次数）
     * 
     * @param cap 玩家能力数据
     * @param shopId 商店ID
     * @param entryId 商品ID
     */
    public static void recordPurchase(IQuestCapability cap, String shopId, String entryId) {
        cap.incrementTradePurchase(shopId, entryId);
        int newCount = cap.getTradePurchaseCount(shopId, entryId);
        LOGGER.info("[Trade-State] Purchase recorded: entry={}, newCount={}", entryId, newCount);
    }
    
    /**
     * 记录冷却时间
     * 
     * @param player 玩家对象
     * @param cap 玩家能力数据
     * @param shopId 商店ID
     * @param entryId 商品ID
     */
    public static void recordCooldown(ServerPlayer player, IQuestCapability cap, String shopId, String entryId) {
        long gameTime = player.level().getGameTime();
        long dayTime = TimeSanitizer.sanitizeDayTime(player.level());
        
        LOGGER.info("[Trade-State] Recording cooldown: shop={}, entry={}, gameTime={}, dayTime={}",
                shopId, entryId, gameTime, dayTime);
        
        cap.recordTradePurchaseTime(shopId, entryId, gameTime, dayTime);
    }
    
    /**
     * 重置购买次数和冷却
     * 
     * @param cap 玩家能力数据
     * @param shopId 商店ID
     * @param entryId 商品ID
     */
    public static void resetPurchaseAndCooldown(IQuestCapability cap, String shopId, String entryId) {
        int oldCount = cap.getTradePurchaseCount(shopId, entryId);
        cap.resetTradePurchaseCount(shopId, entryId);
        LOGGER.info("[Trade-State] Reset purchase and cooldown: entry={}, oldCount={}", entryId, oldCount);
    }
}
