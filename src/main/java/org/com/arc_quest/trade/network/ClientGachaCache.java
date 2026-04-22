package org.com.arc_quest.trade.network;

import com.mojang.logging.LogUtils;
import net.minecraft.sounds.SoundEvent;
import org.com.arc_quest.client.util.ClientCooldownHelper;
import org.com.arc_quest.client.util.GuiSoundManager;
import org.com.arc_quest.trade.gacha.registry.GachaRegistry;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端抽奖数据镜像缓存。
 * <p>
 * <b>线程模型</b>：仅在客户端主线程（Render Thread）访问，由 S2C 网络包更新。
 * 所有更新通过 {@code ctx.get().enqueueWork()} 确保在主线程执行，因此无需同步保护。
 * <p>
 * 负责统一管理抽奖相关的客户端状态、历史记录和音效触发，确保听觉反馈与服务端权威状态同步。
 */
public final class ClientGachaCache {

    public static final ClientGachaCache INSTANCE = new ClientGachaCache();
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 抽奖会话数据映射 (shopId -> GachaSessionData)
     */
    private final Map<String, GachaSessionData> gachaSessions = new HashMap<>();

    private ClientGachaCache() {
    }

    // ════════════════════════════════════════
    //  核心会话管理 API
    // ════════════════════════════════════════

    /**
     * 更新抽奖会话数据（基础版本）。
     */
    public void updateSession(String shopId, int pityCounter, int totalDraws) {
        gachaSessions.put(shopId, new GachaSessionData(pityCounter, totalDraws));
        LOGGER.debug("[GachaCache] Updated session for {}: pity={}, draws={}", 
                    shopId, pityCounter, totalDraws);
    }

    /**
     * 更新抽奖会话数据（含冷却信息，对标商店系统）。
     */
    public void updateSession(String shopId, int pityCounter, int totalDraws,
                              long lastDrawRealTime, long lastDrawGameTime, long lastDrawDayTime,
                              int cooldownType, long cooldownValue, int resetTimeTicks) {
        // 保留现有的 drawHistory 和最近抽奖结果字段，只更新其他字段
        GachaSessionData existingSession = gachaSessions.get(shopId);
        ArrayList<DrawRecord> existingHistory = existingSession != null 
            ? new ArrayList<>(existingSession.drawHistory) 
            : new ArrayList<>();
        
        // 保留最近抽奖结果字段
        String existingLastDrawnItemId = existingSession != null ? existingSession.lastDrawnItemId : null;
        String existingLastRarityName = existingSession != null ? existingSession.lastRarityName : null;
        int existingLastActualCount = existingSession != null ? existingSession.lastActualCount : 0;
        boolean existingLastPityTriggered = existingSession != null ? existingSession.lastPityTriggered : false;
        long existingLastDrawTime = existingSession != null ? existingSession.lastDrawTime : 0;
        String existingLastFailReason = existingSession != null ? existingSession.lastFailReason : null;
        
        GachaSessionData newSession = new GachaSessionData(
            pityCounter, totalDraws,
            lastDrawRealTime, lastDrawGameTime, lastDrawDayTime,
            cooldownType, cooldownValue, resetTimeTicks
        );
        
        // 恢复历史记录
        newSession.drawHistory.addAll(existingHistory);
        
        // 恢复最近抽奖结果字段
        newSession.lastDrawnItemId = existingLastDrawnItemId;
        newSession.lastRarityName = existingLastRarityName;
        newSession.lastActualCount = existingLastActualCount;
        newSession.lastPityTriggered = existingLastPityTriggered;
        newSession.lastDrawTime = existingLastDrawTime;
        newSession.lastFailReason = existingLastFailReason;
        
        gachaSessions.put(shopId, newSession);
        LOGGER.debug("[GachaCache] Updated session with cooldown for {}: pity={}, draws={}, cooldownType={}, historySize={}", 
                    shopId, pityCounter, totalDraws, cooldownType, newSession.drawHistory.size());
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
        
        // 【修复】成功抽奖后清除失败原因标记
        session.lastFailReason = null;
        
        // 添加到历史记录
        session.drawHistory.add(new DrawRecord(drawnItemId, rarityName, actualCount, 
                                               pityTriggered, session.lastDrawTime));
        
        // 限制历史记录大小（最多保留 50 条）
        if (session.drawHistory.size() > 50) {
            session.drawHistory.remove(0);
        }
        
        LOGGER.debug("[GachaCache] Recorded draw result: {} x{} ({}), historySize={}", 
                    drawnItemId, actualCount, rarityName, session.drawHistory.size());
    }

    /**
     * 记录抽奖失败（冷却/限购/条件不满足）。
     * <p>
     * 用于触发 UI 状态切换，让客户端从 WAITING_SERVER 返回 PREVIEW。
     *
     * @param shopId 商店 ID
     * @param failReason 失败原因字符串
     */
    public void recordDrawFailure(String shopId, String failReason) {
        GachaSessionData session = gachaSessions.get(shopId);
        if (session == null) {
            session = new GachaSessionData(0, 0);
            gachaSessions.put(shopId, session);
        }
        
        // 记录最近一次失败信息
        session.lastFailReason = failReason;
        session.lastDrawTime = System.currentTimeMillis();
        
        LOGGER.debug("[GachaCache] Recorded draw failure: {} - {}", shopId, failReason);
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
                case COOLDOWN -> getCooldownSound(shopId);
                case LIMIT_REACHED -> getLimitReachedSound(shopId);
                case CONDITION_FAIL -> getConditionFailSound(shopId);
                default -> getDrawFailSound(shopId);
            };
            GuiSoundManager.play(sound);
            LOGGER.debug("[GachaCache] Played draw fail sound for reason: {}", failReason);
            return;
        }
        
        // 成功场景：播放抽中音效
        if (itemId == null || rarityName == null) {
            LOGGER.warn("[GachaCache] Draw success but missing item/rarity info");
            return;
        }
        
        SoundEvent successSound = getDrawSuccessSound(shopId, itemId, rarityName);
        if (successSound != null) {
            GuiSoundManager.play(successSound);
            LOGGER.debug("[GachaCache] Played draw success sound for item: {} ({})", itemId, rarityName);
        }
    }

    /**
     * 获取抽奖会话数据。
     */
    @Nullable
    public GachaSessionData getSession(String shopId) {
        return gachaSessions.get(shopId);
    }

    /**
     * 关闭并清理指定商店的抽奖会话。
     */
    public void closeSession(String shopId) {
        gachaSessions.remove(shopId);
        LOGGER.debug("[GachaCache] Session closed for: {}", shopId);
    }

    /**
     * 清空所有缓存。
     */
    public void clear() {
        gachaSessions.clear();
        LOGGER.debug("[GachaCache] Cache cleared.");
    }

    // ════════════════════════════════════════
    //  冷却状态查询 API
    // ════════════════════════════════════════

    /**
     * 检查抽奖是否处于冷却中（对标商店系统 isOnCooldown）。
     */
    public boolean isOnCooldown(String shopId) {
        GachaSessionData data = gachaSessions.get(shopId);
        if (data == null) return false;
        
        // 如果没有冷却配置，直接返回 false
        if (data.getCooldownType() == 0) return false;
        
        // 委托给 ClientCooldownHelper 进行精确判断
        return ClientCooldownHelper.isOnCooldown(
                data.getLastDrawRealTime(),
                data.getLastDrawGameTime(),
                data.getLastDrawDayTime(),
                data.getCooldownType(),
                data.getCooldownValue(),
                data.getResetTimeTicks()
        );
    }

    /**
     * 获取抽奖冷却剩余文本（对标商店系统 getCooldownText）。
     */
    public String getCooldownText(String shopId) {
        GachaSessionData data = gachaSessions.get(shopId);
        if (data == null || data.getCooldownType() == 0) return "";
        
        return ClientCooldownHelper.getCooldownText(
                data.getLastDrawRealTime(),
                data.getLastDrawGameTime(),
                data.getLastDrawDayTime(),
                data.getCooldownType(),
                data.getCooldownValue(),
                data.getResetTimeTicks()
        );
    }

    // ════════════════════════════════════════
    //  HUD 支持 API - 保底与统计
    // ════════════════════════════════════════

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

    // ════════════════════════════════════════
    //  抽奖音效辅助方法
    // ════════════════════════════════════════

    /**
     * 获取抽中物品的音效（优先级：项自定义 > 稀有度默认）。
     * 
     * @param shopId 商店 ID
     * @param itemId 物品 ID
     * @param rarityName 稀有度名称
     * @return 音效事件，可能为 null
     */
    @Nullable
    private SoundEvent getDrawSuccessSound(String shopId, String itemId, String rarityName) {
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
    private SoundEvent getCooldownSound(String shopId) {
        var gachaShop = GachaRegistry.get(shopId);
        return gachaShop != null ? gachaShop.getDrawCooldownSound() : null;
    }

    /**
     * 获取抽奖限购音效。
     */
    @Nullable
    private SoundEvent getLimitReachedSound(String shopId) {
        var gachaShop = GachaRegistry.get(shopId);
        return gachaShop != null ? gachaShop.getDrawLimitReachedSound() : null;
    }

    /**
     * 获取抽奖条件失败音效。
     */
    @Nullable
    private SoundEvent getConditionFailSound(String shopId) {
        var gachaShop = GachaRegistry.get(shopId);
        return gachaShop != null ? gachaShop.getDrawConditionFailSound() : null;
    }

    /**
     * 获取抽奖通用失败音效。
     */
    @Nullable
    private SoundEvent getDrawFailSound(String shopId) {
        var gachaShop = GachaRegistry.get(shopId);
        return gachaShop != null ? gachaShop.getDrawFailSound() : null;
    }

    // ════════════════════════════════════════
    //  内部数据结构
    // ════════════════════════════════════════

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
     * 抽奖会话数据容器（对标 TradeSessionData）。
     */
    public static class GachaSessionData {
        private int pityCounter;
        private int totalDraws;
        
        // 冷却数据（对标 TradeSessionData）
        private long lastDrawRealTime;     // 真实时间戳
        private long lastDrawGameTime;     // 游戏时间
        private long lastDrawDayTime;      // 天数时间
        private int cooldownType;          // 冷却类型 (0=NONE, 1=SECONDS, 2=GAME_DAY, 3=GAME_TICK)
        private long cooldownValue;        // 冷却值
        private int resetTimeTicks;        // GAME_TICK 重置时间点
        
        // 最近一次抽奖结果
        @Nullable
        private String lastDrawnItemId;
        @Nullable
        private String lastRarityName;
        private int lastActualCount;
        private boolean lastPityTriggered;
        private long lastDrawTime;
        
        // 最近一次失败原因（"COOLDOWN", "MAX_DRAWS_REACHED", "CONDITION_NOT_MET"）
        @Nullable
        private String lastFailReason;
        
        // 历史记录（最多 50 条）
        private final ArrayList<DrawRecord> drawHistory = new ArrayList<>();
        
        public GachaSessionData(int pityCounter, int totalDraws) {
            this.pityCounter = pityCounter;
            this.totalDraws = totalDraws;
            this.lastDrawRealTime = 0;
            this.lastDrawGameTime = 0;
            this.lastDrawDayTime = 0;
            this.cooldownType = 0;
            this.cooldownValue = 0;
            this.resetTimeTicks = 0;
        }
        
        // 完整构造函数（用于网络包同步）
        public GachaSessionData(int pityCounter, int totalDraws,
                                long lastDrawRealTime, long lastDrawGameTime, long lastDrawDayTime,
                                int cooldownType, long cooldownValue, int resetTimeTicks) {
            this.pityCounter = pityCounter;
            this.totalDraws = totalDraws;
            this.lastDrawRealTime = lastDrawRealTime;
            this.lastDrawGameTime = lastDrawGameTime;
            this.lastDrawDayTime = lastDrawDayTime;
            this.cooldownType = cooldownType;
            this.cooldownValue = cooldownValue;
            this.resetTimeTicks = resetTimeTicks;
        }
        
        public int getPityCounter() { return pityCounter; }
        public int getTotalDraws() { return totalDraws; }
        
        // 冷却数据 Getters
        public long getLastDrawRealTime() { return lastDrawRealTime; }
        public long getLastDrawGameTime() { return lastDrawGameTime; }
        public long getLastDrawDayTime() { return lastDrawDayTime; }
        public int getCooldownType() { return cooldownType; }
        public long getCooldownValue() { return cooldownValue; }
        public int getResetTimeTicks() { return resetTimeTicks; }
        
        @Nullable
        public String getLastDrawnItemId() { return lastDrawnItemId; }
        @Nullable
        public String getLastRarityName() { return lastRarityName; }
        public int getLastActualCount() { return lastActualCount; }
        public boolean isLastPityTriggered() { return lastPityTriggered; }
        public long getLastDrawTime() { return lastDrawTime; }
        @Nullable
        public String getLastFailReason() { return lastFailReason; }
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
