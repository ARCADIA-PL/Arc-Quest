package org.com.arc_quest.trade.network;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import net.minecraftforge.registries.ForgeRegistries;
import org.com.arc_quest.client.util.ClientCooldownHelper;
import org.com.arc_quest.client.util.GuiSoundManager;
import org.com.arc_quest.trade.api.TradeEntry;
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
        LOGGER.debug("[TradeCache] Cache cleared.");
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
}
