package org.com.arc_quest.trade.network;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import net.minecraftforge.registries.ForgeRegistries;
import org.com.arc_quest.client.util.ClientCooldownHelper;
import org.com.arc_quest.client.util.GuiSoundManager;
import org.com.arc_quest.trade.api.TradeEntry;
import org.com.arc_quest.trade.gacha.GachaItem;
import org.com.arc_quest.trade.registry.GachaRegistry;
import org.com.arc_quest.trade.registry.TradeRegistry;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端交易数据镜像缓存。
 * <p>
 * <b>线程模型</b>：仅在客户端主线程（Render Thread）访问，由 S2C 网络包更新。
 * 所有更新通过 {@code ctx.get().enqueueWork()} 确保在主线程执行，因此无需同步保护。
 * <p>
 * 负责统一管理交易相关的客户端状态和音效触发，确保听觉反馈与服务端权威状态同步。
 */
public final class ClientTradeCache {

    public static final ClientTradeCache INSTANCE = new ClientTradeCache();
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 当前活跃的交易会话映射 (shopId -> SessionData)
     */
    private final Map<String, TradeSessionData> activeSessions = new HashMap<>();
    
    /**
     * 抽奖会话数据映射 (shopId -> GachaSessionData)
     */
    private final Map<String, GachaSessionData> gachaSessions = new HashMap<>();

    private ClientTradeCache() {
    }

    /**
     * 播放商店打开音效。
     *
     * @param shopId 商店 ID
     * @param openSoundId 打开音效的资源位置字符串
     */
    public void playOpenSound(String shopId, String openSoundId) {
        if (openSoundId == null || openSoundId.isEmpty()) {
            LOGGER.debug("[TradeCache] No open sound configured for shop: {}", shopId);
            return;
        }

        try {
            ResourceLocation rl = ResourceLocation.tryParse(openSoundId);
            if (rl != null) {
                SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(rl);
                if (sound != null) {
                    GuiSoundManager.play(sound);
                    LOGGER.debug("[TradeCache] Played open sound for shop: {}", shopId);
                } else {
                    LOGGER.warn("[TradeCache] Open sound not found: {}", openSoundId);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[TradeCache] Failed to play open sound: {}", openSoundId, e);
        }
    }

    /**
     * 播放商店关闭音效。
     *
     * @param shopId 商店 ID
     * @param closeSoundId 关闭音效的资源位置字符串
     */
    public void playCloseSound(String shopId, String closeSoundId) {
        if (closeSoundId == null || closeSoundId.isEmpty()) {
            LOGGER.debug("[TradeCache] No close sound configured for shop: {}", shopId);
            return;
        }

        try {
            ResourceLocation rl = ResourceLocation.tryParse(closeSoundId);
            if (rl != null) {
                SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(rl);
                if (sound != null) {
                    GuiSoundManager.play(sound);
                    LOGGER.debug("[TradeCache] Played close sound for shop: {}", shopId);
                } else {
                    LOGGER.warn("[TradeCache] Close sound not found: {}", closeSoundId);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[TradeCache] Failed to play close sound: {}", closeSoundId, e);
        }
    }

    /**
     * 处理购买结果（由网络包调用）。
     *
     * @param shopId      商店 ID
     * @param entryId     商品 ID
     * @param success     是否成功
     * @param failReason  失败原因（仅当 success 为 false 时有效）
     */
    public void handlePurchaseResult(String shopId, String entryId, boolean success, S2COpenTradePacket.FailReason failReason) {
        if (entryId == null || entryId.isEmpty()) return;

        var shopDef = TradeRegistry.get(shopId);
        if (shopDef == null) return;

        TradeEntry entry = shopDef.getEntry(entryId);
        if (entry == null) return;

        if (success) {
            GuiSoundManager.play(entry.getPurchaseSuccessSound());
        } else {
            SoundEvent sound = switch (failReason != null ? failReason : S2COpenTradePacket.FailReason.GENERIC) {
                case COOLDOWN -> entry.getCooldownSound();
                case LIMIT_REACHED -> entry.getLimitReachedSound();
                case CONDITION_FAIL -> entry.getConditionFailSound();
                default -> entry.getPurchaseFailSound();
            };
            GuiSoundManager.play(sound);
        }
        LOGGER.debug("[TradeCache] Played selectSound for {} result: {}", success ? "success" : failReason, entryId);
    }

    /**
     * 更新会话数据（由网络包调用）。
     */
    public void updateSession(String shopId, int[] purchaseCounts, int[] maxPurchases,
                              long[] lastPurchaseTimes, long[] purchaseGameTimes, long[] purchaseDayTimes,
                              int[] cooldownTypes, long[] cooldownValues, int[] resetTimeTicks,
                              boolean[] visibility, boolean[] canBuyConditions) {
        TradeSessionData data = new TradeSessionData(shopId);
        data.purchaseCounts = purchaseCounts;
        data.maxPurchases = maxPurchases;
        data.lastPurchaseTimes = lastPurchaseTimes;
        data.purchaseGameTimes = purchaseGameTimes;
        data.purchaseDayTimes = purchaseDayTimes;
        data.cooldownTypes = cooldownTypes;
        data.cooldownValues = cooldownValues;
        data.resetTimeTicks = resetTimeTicks;
        data.visibility = visibility;
        data.canBuyConditions = canBuyConditions;
        activeSessions.put(shopId, data);
    }

    /**
     * 检查指定商品是否处于冷却中。
     */
    public boolean isOnCooldown(String shopId, int entryIndex) {
        TradeSessionData data = activeSessions.get(shopId);
        if (data == null || entryIndex < 0 || entryIndex >= data.lastPurchaseTimes.length) return false;
        
        // 委托给 ClientCooldownHelper 进行精确判断
        return ClientCooldownHelper.isOnCooldown(
                data.lastPurchaseTimes[entryIndex],
                data.purchaseGameTimes[entryIndex],
                data.purchaseDayTimes[entryIndex],
                data.cooldownTypes[entryIndex],
                data.cooldownValues[entryIndex],
                data.resetTimeTicks[entryIndex]
        );
    }

    /**
     * 获取指定商品的冷却剩余文本。
     */
    public String getCooldownText(String shopId, int entryIndex) {
        TradeSessionData data = activeSessions.get(shopId);
        if (data == null || entryIndex < 0 || entryIndex >= data.lastPurchaseTimes.length) return "";
        
        return ClientCooldownHelper.getCooldownText(
                data.lastPurchaseTimes[entryIndex],
                data.purchaseGameTimes[entryIndex],
                data.purchaseDayTimes[entryIndex],
                data.cooldownTypes[entryIndex],
                data.cooldownValues[entryIndex],
                data.resetTimeTicks[entryIndex]
        );
    }

    /**
     * 获取指定商品的已购数量。
     */
    public int getPurchaseCount(String shopId, int entryIndex) {
        TradeSessionData data = activeSessions.get(shopId);
        if (data == null || entryIndex < 0 || entryIndex >= data.purchaseCounts.length) return 0;
        return data.purchaseCounts[entryIndex];
    }

    /**
     * 获取指定商品的最大限购次数。
     */
    public int getMaxPurchases(String shopId, int entryIndex) {
        TradeSessionData data = activeSessions.get(shopId);
        if (data == null || entryIndex < 0 || entryIndex >= data.maxPurchases.length) return -1;
        return data.maxPurchases[entryIndex];
    }

    /**
     * 获取指定商品的剩余可购买次数。
     */
    public int getRemainingPurchases(String shopId, int entryIndex) {
        int max = getMaxPurchases(shopId, entryIndex);
        if (max < 0) return -1; // 无限
        return Math.max(0, max - getPurchaseCount(shopId, entryIndex));
    }

    /**
     * 检查指定商品是否对当前玩家可见。
     */
    public boolean isVisible(String shopId, int entryIndex) {
        TradeSessionData data = activeSessions.get(shopId);
        if (data == null || entryIndex < 0 || entryIndex >= data.visibility.length) return true;
        return data.visibility[entryIndex];
    }

    /**
     * 检查指定商品是否满足购买资格条件。
     */
    public boolean canBuy(String shopId, int entryIndex) {
        TradeSessionData data = activeSessions.get(shopId);
        if (data == null || entryIndex < 0 || entryIndex >= data.canBuyConditions.length) return false;
        return data.canBuyConditions[entryIndex];
    }

    /**
     * 获取指定商店的完整交易项列表定义。
     * <p>
     * 这是一个便捷方法，结合了缓存中的 ShopID 和全局注册表。
     */
    @Nullable
    public List<TradeEntry> getShopEntries(String shopId) {
        var shopDef = TradeRegistry.get(shopId);
        return shopDef != null ? new ArrayList<>(shopDef.getAllEntries()) : null;
    }

        /**
     * 获取商品在全量列表中的索引。
     */
    public int getGlobalIndex(String shopId, String entryId) {
        var entries = getShopEntries(shopId);
        if (entries != null) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).getEntryId().equals(entryId)) return i;
            }
        }
        return -1;
    }

    /**
     * 综合判断是否可以购买（非冷却、非限购、条件满足）。
     */
    public boolean canPurchase(String shopId, int entryIndex) {
        return !isOnCooldown(shopId, entryIndex) 
            && getRemainingPurchases(shopId, entryIndex) != 0 
            && canBuy(shopId, entryIndex);
    }

    /**
     * 获取指定索引的交易项定义。
     */
    @Nullable
    public TradeEntry getEntry(String shopId, int entryIndex) {
        var entries = getShopEntries(shopId);
        if (entries != null && entryIndex >= 0 && entryIndex < entries.size()) {
            return entries.get(entryIndex);
        }
        return null;
    }

    /**
     * 关闭并清理指定商店的会话数据。
     */
    public void closeSession(String shopId) {
        activeSessions.remove(shopId);
        LOGGER.debug("[TradeCache] Session closed for: {}", shopId);
    }

    /**
     * 关闭除指定商店外的所有会话（防止内存泄漏）。
     *
     * @param keepShopId 要保留的商店 ID
     */
    public void closeAllExcept(String keepShopId) {
        activeSessions.keySet().removeIf(shopId -> !shopId.equals(keepShopId));
        LOGGER.debug("[TradeCache] Closed all sessions except: {}", keepShopId);
    }

    @Nullable
    public TradeSessionData getSession(String shopId) {
        return activeSessions.get(shopId);
    }

    public void clear() {
        activeSessions.clear();
        gachaSessions.clear();
        LOGGER.debug("[TradeCache] Cache cleared.");
    }
    
    // ════════════════════════════════════════
    //  抽奖系统 API
    // ════════════════════════════════════════
    
    /**
     * 更新抽奖会话数据。
     */
    public void updateGachaSession(String shopId, int pityCounter, int totalDraws) {
        gachaSessions.put(shopId, new GachaSessionData(pityCounter, totalDraws));
        LOGGER.debug("[TradeCache] Updated gacha session for {}: pity={}, draws={}", 
                    shopId, pityCounter, totalDraws);
    }
    
    /**
     * 记录抽奖结果（由网络包调用）。
     * 
     * @param shopId 商店 ID
     * @param drawnItemId 抽中的物品 ID
     * @param rarityName 稀有度名称
     * @param actualCount 实际数量
     * @param pityTriggered 是否触发保底
     * @param newPityCounter 新的保底计数
     */
    public void recordDrawResult(String shopId, String drawnItemId, String rarityName,
                                  int actualCount, boolean pityTriggered, int newPityCounter) {
        GachaSessionData session = gachaSessions.get(shopId);
        if (session == null) {
            session = new GachaSessionData(newPityCounter, 1);
            gachaSessions.put(shopId, session);
        } else {
            // 更新保底计数和总次数
            session.pityCounter = newPityCounter;
            session.totalDraws++;
        }
        
        // 记录最近一次抽奖结果
        session.lastDrawnItemId = drawnItemId;
        session.lastRarityName = rarityName;
        session.lastActualCount = actualCount;
        session.lastPityTriggered = pityTriggered;
        session.lastDrawTime = System.currentTimeMillis();
        
        // 添加到历史记录
        session.drawHistory.add(new DrawRecord(drawnItemId, rarityName, actualCount, 
                                               pityTriggered, session.lastDrawTime));
        
        // 限制历史记录大小（最多保留 50 条）
        if (session.drawHistory.size() > 50) {
            session.drawHistory.remove(0);
        }
        
        LOGGER.debug("[TradeCache] Recorded draw result: {} x{} ({})", 
                    drawnItemId, actualCount, rarityName);
    }
    
    /**
     * 处理抽奖结果音效（由网络包调用）。
     * <p>
     * 对标商店系统的 handlePurchaseResult，支持成功和失败场景。
     *
     * @param shopId      商店 ID
     * @param itemId      抽中的物品 ID（成功时有效）
     * @param rarityName  稀有度名称（成功时有效）
     * @param success     是否成功
     * @param failReason  失败原因（仅当 success 为 false 时有效）
     */
    public void handleDrawResult(String shopId, @Nullable String itemId, @Nullable String rarityName,
                                 boolean success, @Nullable FailReason failReason) {
        if (!success) {
            // 失败场景：播放对应的失败音效
            SoundEvent sound = switch (failReason != null ? failReason : FailReason.GENERIC) {
                case COOLDOWN -> getGachaCooldownSound(shopId);
                case LIMIT_REACHED -> getGachaLimitReachedSound(shopId);
                case CONDITION_FAIL -> getGachaConditionFailSound(shopId);
                default -> getGachaDrawFailSound(shopId);
            };
            GuiSoundManager.play(sound);
            LOGGER.debug("[TradeCache] Played draw fail sound for reason: {}", failReason);
            return;
        }
        
        // 成功场景：播放抽中音效
        if (itemId == null || rarityName == null) {
            LOGGER.warn("[TradeCache] Draw success but missing item/rarity info");
            return;
        }
        
        SoundEvent successSound = getGachaDrawSuccessSound(shopId, itemId, rarityName);
        if (successSound != null) {
            GuiSoundManager.play(successSound);
            LOGGER.debug("[TradeCache] Played draw success sound for item: {} ({})", itemId, rarityName);
        }
    }
    
    /**
     * 获取抽奖会话数据。
     */
    @Nullable
    public GachaSessionData getGachaSession(String shopId) {
        return gachaSessions.get(shopId);
    }
    
    /**
     * 关闭并清理指定商店的抽奖会话。
     */
    public void closeGachaSession(String shopId) {
        gachaSessions.remove(shopId);
        LOGGER.debug("[TradeCache] Gacha session closed for: {}", shopId);
    }
    
    // ════════════════════════════════════════════
    //  HUD 支持 API - 抽奖系统
    // ════════════════════════════════════════════
    
    /**
     * 获取保底进度（用于 HUD 显示）。
     * 
     * @param shopId 商店 ID
     * @return 当前保底计数，如果会话不存在则返回 -1
     */
    public int getPityProgress(String shopId) {
        var session = gachaSessions.get(shopId);
        return session != null ? session.getPityCounter() : -1;
    }
    
    /**
     * 获取总抽奖次数。
     * 
     * @param shopId 商店 ID
     * @return 总抽奖次数，如果会话不存在则返回 0
     */
    public int getTotalDraws(String shopId) {
        var session = gachaSessions.get(shopId);
        return session != null ? session.getTotalDraws() : 0;
    }
    
    /**
     * 获取保底剩余次数（用于 HUD 进度条）。
     * 
     * @param shopId 商店 ID
     * @param pityThreshold 保底阈值（从 GachaShopDefinition 获取）
     * @return 剩余次数，如果会话不存在则返回 -1
     */
    public int getPityRemaining(String shopId, int pityThreshold) {
        int current = getPityProgress(shopId);
        if (current < 0) return -1;
        return Math.max(0, pityThreshold - current);
    }
    
    /**
     * 获取保底进度百分比（0-100）。
     * 
     * @param shopId 商店 ID
     * @param pityThreshold 保底阈值
     * @return 进度百分比，如果数据无效则返回 0
     */
    public int getPityProgressPercent(String shopId, int pityThreshold) {
        if (pityThreshold <= 0) return 0;
        int current = getPityProgress(shopId);
        if (current < 0) return 0;
        return Math.min(100, (current * 100) / pityThreshold);
    }
    
    /**
     * 检查是否已触发保底。
     * 
     * @param shopId 商店 ID
     * @param pityThreshold 保底阈值
     * @return true 如果已达到或超过保底阈值
     */
    public boolean isPityTriggered(String shopId, int pityThreshold) {
        int current = getPityProgress(shopId);
        return current >= pityThreshold;
    }
    
    /**
     * 获取最近一次抽奖结果。
     * 
     * @param shopId 商店 ID
     * @return 抽奖记录，如果没有则返回 null
     */
    @Nullable
    public DrawRecord getLastDrawResult(String shopId) {
        var session = gachaSessions.get(shopId);
        return session != null && !session.drawHistory.isEmpty() 
            ? session.drawHistory.get(session.drawHistory.size() - 1) 
            : null;
    }
    
    /**
     * 获取抽奖历史记录（不可变视图）。
     * 
     * @param shopId 商店 ID
     * @return 历史记录列表
     */
    public List<DrawRecord> getDrawHistory(String shopId) {
        var session = gachaSessions.get(shopId);
        return session != null 
            ? Collections.unmodifiableList(session.drawHistory) 
            : Collections.emptyList();
    }
    
    /**
     * 获取指定稀有度的抽取次数统计。
     * 
     * @param shopId 商店 ID
     * @param rarityName 稀有度名称
     * @return 该稀有度的抽取次数
     */
    public int getRarityDrawCount(String shopId, String rarityName) {
        var session = gachaSessions.get(shopId);
        if (session == null) return 0;
        
        return (int) session.drawHistory.stream()
            .filter(record -> rarityName.equals(record.rarityName()))
            .count();
    }
    
    /**
     * 获取平均每次抽奖获得的物品数量。
     * 
     * @param shopId 商店 ID
     * @return 平均数量，如果没有记录则返回 0
     */
    public double getAverageItemCount(String shopId) {
        var session = gachaSessions.get(shopId);
        if (session == null || session.drawHistory.isEmpty()) return 0.0;
        
        return session.drawHistory.stream()
            .mapToInt(DrawRecord::actualCount)
            .average()
            .orElse(0.0);
    }
    
    /**
     * 获取保底触发次数统计。
     * 
     * @param shopId 商店 ID
     * @return 保底触发次数
     */
    public int getPityTriggerCount(String shopId) {
        var session = gachaSessions.get(shopId);
        if (session == null) return 0;
        
        return (int) session.drawHistory.stream()
            .filter(DrawRecord::pityTriggered)
            .count();
    }
    
    /**
     * 获取剩余可抽奖次数（对标商店系统的 getRemainingPurchases）。
     * 
     * @param shopId 商店 ID
     * @param maxDraws 最大抽奖次数（从 GachaShopDefinition 获取，-1 表示无限）
     * @return 剩余次数，-1 表示无限，-2 表示无数据
     */
    public int getRemainingDraws(String shopId, int maxDraws) {
        if (maxDraws < 0) return -1; // 无限
        
        var session = gachaSessions.get(shopId);
        if (session == null) return -2; // 无数据
        
        int totalDraws = session.getTotalDraws();
        return Math.max(0, maxDraws - totalDraws);
    }
    
    /**
     * 检查是否已达到抽奖次数上限（对标商店系统的 isPurchaseLimitReached）。
     * 
     * @param shopId 商店 ID
     * @param maxDraws 最大抽奖次数
     * @return true 如果已达到上限
     */
    public boolean isDrawLimitReached(String shopId, int maxDraws) {
        if (maxDraws < 0) return false; // 无限
        
        var session = gachaSessions.get(shopId);
        if (session == null) return false;
        
        return session.getTotalDraws() >= maxDraws;
    }
    
    /**
     * 获取抽奖次数进度百分比（0-100）。
     * 
     * @param shopId 商店 ID
     * @param maxDraws 最大抽奖次数
     * @return 进度百分比，如果无限或无数据则返回 0
     */
    public int getDrawProgressPercent(String shopId, int maxDraws) {
        if (maxDraws <= 0) return 0; // 无限或无效
        
        var session = gachaSessions.get(shopId);
        if (session == null) return 0;
        
        int totalDraws = session.getTotalDraws();
        return Math.min(100, (totalDraws * 100) / maxDraws);
    }
    
    /**
     * 获取各稀有度的分布统计（用于饼图/柱状图）。
     * 
     * @param shopId 商店 ID
     * @return Map<稀有度名称, 抽取次数>
     */
    public Map<String, Integer> getRarityDistribution(String shopId) {
        var session = gachaSessions.get(shopId);
        if (session == null || session.drawHistory.isEmpty()) {
            return Collections.emptyMap();
        }
        
        Map<String, Integer> distribution = new HashMap<>();
        for (DrawRecord record : session.drawHistory) {
            distribution.merge(record.rarityName(), 1, Integer::sum);
        }
        
        return Collections.unmodifiableMap(distribution);
    }
    
    /**
     * 获取距离下次保底的预计抽奖次数。
     * 
     * @param shopId 商店 ID
     * @param pityThreshold 保底阈值
     * @return 预计次数，如果数据无效则返回 -1
     */
    public int getEstimatedDrawsToPity(String shopId, int pityThreshold) {
        return getPityRemaining(shopId, pityThreshold);
    }
    // ════════════════════════════════════════════
    //  抽奖音效辅助方法
    // ════════════════════════════════════════════
    
    /**
     * 获取抽中物品的音效（优先级：项自定义 > 稀有度默认）。
     * 
     * @param shopId 商店 ID
     * @param itemId 物品 ID
     * @param rarityName 稀有度名称
     * @return 音效事件，可能为 null
     */
    @Nullable
    private SoundEvent getGachaDrawSuccessSound(String shopId, String itemId, String rarityName) {
        var gachaShop = GachaRegistry.get(shopId);
        if (gachaShop == null) return null;
        
        // 查找对应的 GachaItem
        var pool = gachaShop.getGachaPool();
        for (var item : pool.getItems()) {
            if (item.getItemId().equals(itemId)) {
                // 使用 GachaShopDefinition 的优先级链获取音效
                return gachaShop.getEffectiveDrawSuccessSound(item);
            }
        }
        
        // 如果找不到具体物品，尝试使用稀有度默认音效
        var rarityConfig = gachaShop.getRarityConfig(rarityName);
        if (rarityConfig != null) {
            return rarityConfig.getDrawSuccessSound();
        }
        
        return null;
    }
    
    /**
     * 获取抽奖冷却音效。
     */
    @Nullable
    private SoundEvent getGachaCooldownSound(String shopId) {
        var gachaShop = GachaRegistry.get(shopId);
        return gachaShop != null ? gachaShop.getDrawCooldownSound() : null;
    }
    
    /**
     * 获取抽奖限购音效。
     */
    @Nullable
    private SoundEvent getGachaLimitReachedSound(String shopId) {
        var gachaShop = GachaRegistry.get(shopId);
        return gachaShop != null ? gachaShop.getDrawLimitReachedSound() : null;
    }
    
    /**
     * 获取抽奖条件失败音效。
     */
    @Nullable
    private SoundEvent getGachaConditionFailSound(String shopId) {
        var gachaShop = GachaRegistry.get(shopId);
        return gachaShop != null ? gachaShop.getDrawConditionFailSound() : null;
    }
    
    /**
     * 获取抽奖通用失败音效。
     */
    @Nullable
    private SoundEvent getGachaDrawFailSound(String shopId) {
        var gachaShop = GachaRegistry.get(shopId);
        return gachaShop != null ? gachaShop.getDrawFailSound() : null;
    }
    
    /**
     * 抽奖失败原因枚举（对标 S2COpenTradePacket.FailReason）。
     */
    public enum FailReason {
        COOLDOWN,           // 冷却中
        LIMIT_REACHED,      // 达到限购
        CONDITION_FAIL,     // 条件不满足
        GENERIC             // 通用失败
    }

    /**
     * 交易会话数据容器
     */
    public static class TradeSessionData {
        private final String shopId;
        private int[] purchaseCounts;
        private int[] maxPurchases;
        private long[] lastPurchaseTimes;
        private long[] purchaseGameTimes;
        private long[] purchaseDayTimes;
        private int[] cooldownTypes;
        private long[] cooldownValues;
        private int[] resetTimeTicks;
        private boolean[] visibility;
        private boolean[] canBuyConditions;
        
        public TradeSessionData(String shopId) {
            this.shopId = shopId;
        }

        public String getShopId() { return shopId; }
        public int[] getPurchaseCounts() { return purchaseCounts; }
        public int[] getMaxPurchases() { return maxPurchases; }
        public long[] getLastPurchaseTimes() { return lastPurchaseTimes; }
        public long[] getPurchaseGameTimes() { return purchaseGameTimes; }
        public long[] getPurchaseDayTimes() { return purchaseDayTimes; }
        public int[] getCooldownTypes() { return cooldownTypes; }
        public long[] getCooldownValues() { return cooldownValues; }
        public int[] getResetTimeTicks() { return resetTimeTicks; }
        public boolean[] getVisibility() { return visibility; }
        public boolean[] getCanBuyConditions() { return canBuyConditions; }
    }
    
    /**
     * 抽奖会话数据容器
     */
    public static class GachaSessionData {
        private int pityCounter;
        private int totalDraws;
        
        // 最近一次抽奖结果
        @Nullable
        private String lastDrawnItemId;
        @Nullable
        private String lastRarityName;
        private int lastActualCount;
        private boolean lastPityTriggered;
        private long lastDrawTime;
        
        // 历史记录（最多 50 条）
        private final ArrayList<DrawRecord> drawHistory = new ArrayList<>();
        
        public GachaSessionData(int pityCounter, int totalDraws) {
            this.pityCounter = pityCounter;
            this.totalDraws = totalDraws;
        }
        
        public int getPityCounter() { return pityCounter; }
        public int getTotalDraws() { return totalDraws; }
        @Nullable
        public String getLastDrawnItemId() { return lastDrawnItemId; }
        @Nullable
        public String getLastRarityName() { return lastRarityName; }
        public int getLastActualCount() { return lastActualCount; }
        public boolean isLastPityTriggered() { return lastPityTriggered; }
        public long getLastDrawTime() { return lastDrawTime; }
        public List<DrawRecord> getDrawHistory() { 
            return Collections.unmodifiableList(drawHistory); 
        }
    }
    
    /**
     * 单次抽奖记录。
     */
    public record DrawRecord(
        String itemId,
        String rarityName,
        int actualCount,
        boolean pityTriggered,
        long drawTime
    ) {}
}
