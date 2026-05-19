package org.arcadia.arc_quest.trade.gacha.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import org.arcadia.arc_quest.dialogue.api.CooldownType;
import org.arcadia.arc_quest.quest.api.ICondition;
import org.arcadia.arc_quest.quest.player.ArcQuestPlayer;
import org.arcadia.arc_quest.trade.api.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 抽奖商店定义。
 * <p>
 * 封装 TradeShopDefinition，添加奖池和保底系统。
 */
public class GachaShopDefinition {

    private final TradeShopDefinition shopDefinition;
    private final GachaPool gachaPool;
    private final List<ITradeOffer> drawCosts;
    private final PityConfig pityConfig;
    private final CooldownType cooldownType;      // 抽奖冷却类型
    private final long cooldownValue;             // 抽奖冷却值（对标 TradeEntry）
    private final int resetTimeTicks;             // 游戏刻重置时间点（0-24000）
    @Nullable
    private final ICondition drawCondition;       // 抽奖执行条件（类似 canBuy）
    private final int maxDraws;                   // 最大抽奖次数（-1 为无限）
    @Nullable
    private final ICondition resetCondition;      // 次数重置条件
    private final boolean resetOnLimitReached;    // 达到限购后是否通过冷却自动重置
    private final boolean resetPityOnEarlyTrigger; // 保底前提前抽中是否重置保底进度
    // === 抽奖失败音效配置（对标 TradeEntry）===
    @Nullable
    private final SoundEvent drawCooldownSound;      // 冷却中音效
    @Nullable
    private final SoundEvent drawLimitReachedSound;  // 达到限购音效
    @Nullable
    private final SoundEvent drawConditionFailSound; // 条件不满足音效
    @Nullable
    private final SoundEvent drawFailSound;          // 通用失败音效
    /**
     * 每个稀有度的默认主题色和抽中音效。
     * Key: Rarity.name(), Value: {themeColor, drawSuccessSoundId}
     */
    private final Map<String, RarityConfig> rarityConfigs = new HashMap<>();

    // === 稀有度级别配置（对标 TradeEntry 的音效系统）===
    @Deprecated
    public GachaShopDefinition(String shopId,
                               TradeText displayName,
                               @Nullable TradeText description,
                               List<TradeCategory> categories,
                               LinkedHashMap<String, TradeEntry> entries,
                               @Nullable ICondition openCondition,
                               boolean simpleMode,
                               int themeColor,
                               @Nullable SoundEvent openSound,
                               @Nullable SoundEvent closeSound,
                               GachaPool gachaPool,
                               List<ITradeOffer> drawCosts,
                               CooldownType cooldownType,
                               long cooldownValue,
                               int resetTimeTicks,
                               @Nullable ICondition drawCondition,
                               int maxDraws,
                               @Nullable ICondition resetCondition,
                               boolean resetOnLimitReached,
                               boolean resetPityOnEarlyTrigger,
                               PityConfig pityConfig,
                               @Nullable SoundEvent drawCooldownSound,
                               @Nullable SoundEvent drawLimitReachedSound,
                               @Nullable SoundEvent drawConditionFailSound,
                               @Nullable SoundEvent drawFailSound) {
        shopDefinition = new TradeShopDefinition(
                shopId, displayName, description, categories, entries,
                openCondition, simpleMode, themeColor, openSound, closeSound
        );
        this.gachaPool = gachaPool;
        this.drawCosts = drawCosts;
        this.cooldownType = cooldownType;
        this.cooldownValue = cooldownValue;
        this.resetTimeTicks = resetTimeTicks;
        this.drawCondition = drawCondition;
        this.maxDraws = maxDraws;
        this.resetCondition = resetCondition;
        this.resetOnLimitReached = resetOnLimitReached;
        this.resetPityOnEarlyTrigger = resetPityOnEarlyTrigger;
        this.pityConfig = pityConfig;
        this.drawCooldownSound = drawCooldownSound;
        this.drawLimitReachedSound = drawLimitReachedSound;
        this.drawConditionFailSound = drawConditionFailSound;
        this.drawFailSound = drawFailSound;
    }

    /**
     * 配置稀有度的默认主题色和抽中音效。
     * <p>
     * 对标 TradeEntry 的 themeColor 和 purchaseSuccessSound。
     *
     * @param rarity           稀有度
     * @param themeColor       主题色（ARGB）
     * @param drawSuccessSound 抽中音效（null 表示不播放）
     */
    public void setRarityConfig(GachaItem.Rarity rarity, int themeColor, @Nullable SoundEvent drawSuccessSound) {
        rarityConfigs.put(rarity.getName(), new RarityConfig(themeColor, drawSuccessSound));
    }

    // 代理方法 - 访问底层商店定义
    public TradeShopDefinition getShopDefinition() {
        return shopDefinition;
    }

    public String getShopId() {
        return shopDefinition.getShopId();
    }

    public Component getDisplayName() {
        return shopDefinition.getDisplayName();
    }

    public List<TradeCategory> getCategories() {
        return shopDefinition.getCategories();
    }

    public Collection<TradeEntry> getAllEntries() {
        return shopDefinition.getAllEntries();
    }

    @Nullable
    public TradeEntry getEntry(String entryId) {
        return shopDefinition.getEntry(entryId);
    }

    public boolean isSimpleMode() {
        return shopDefinition.isSimpleMode();
    }

    public int getThemeColor() {
        return shopDefinition.getThemeColor();
    }

    public SoundEvent getOpenSound() {
        return shopDefinition.getOpenSound();
    }

    public SoundEvent getCloseSound() {
        return shopDefinition.getCloseSound();
    }

    // 抽奖特有方法
    public GachaPool getGachaPool() {
        return gachaPool;
    }

    public List<ITradeOffer> getDrawCosts() {
        return drawCosts;
    }

    @Deprecated
    public ITradeOffer getDrawCost() {
        return drawCosts.isEmpty() ? null : drawCosts.get(0);
    }

    public CooldownType getCooldownType() {
        return cooldownType;
    }

    public long getCooldownValue() {
        return cooldownValue;
    }

    public int getResetTimeTicks() {
        return resetTimeTicks;
    }

    @Nullable
    public ICondition getDrawCondition() {
        return drawCondition;
    }

    public int getMaxDraws() {
        return maxDraws;
    }

    @Nullable
    public ICondition getResetCondition() {
        return resetCondition;
    }

    public boolean shouldResetOnLimitReached() {
        return resetOnLimitReached;
    }

    public boolean shouldResetPityOnEarlyTrigger() {
        return resetPityOnEarlyTrigger;
    }

    /**
     * 获取可见性条件（代理自 shopDefinition.getOpenCondition()）。
     */
    @Nullable
    public ICondition getVisibleCondition() {
        return shopDefinition.getOpenCondition();
    }

    public PityConfig getPityConfig() {
        return pityConfig;
    }

    /**
     * 获取稀有度配置。
     *
     * @param rarityName 稀有度名称
     * @return 配置，如果未配置则返回 null
     */
    @Nullable
    public RarityConfig getRarityConfig(String rarityName) {
        return rarityConfigs.get(rarityName);
    }

    /**
     * 获取抽奖项的有效主题色（优先使用项自定义，否则使用稀有度默认，最后使用商店默认）。
     *
     * @param item 抽奖项
     * @return 主题色 ARGB 值
     */
    public int getEffectiveThemeColor(GachaItem item) {
        // 1. 项自定义
        if (item.getThemeColor() != -1) {
            return item.getThemeColor();
        }

        // 2. 稀有度默认
        RarityConfig config = getRarityConfig(item.getRarity().getName());
        if (config != null && config.getThemeColor() != 0) {
            return config.getThemeColor();
        }

        // 3. 商店默认
        return getThemeColor();
    }

    /**
     * 获取抽奖项的有效抽中音效（优先使用项自定义，否则使用稀有度默认）。
     *
     * @param item 抽奖项
     * @return 音效事件，可能为 null
     */
    @Nullable
    public SoundEvent getEffectiveDrawSuccessSound(GachaItem item) {
        // 1. 项自定义
        if (item.getDrawSuccessSound() != null) {
            return item.getDrawSuccessSound();
        }

        // 2. 稀有度默认
        RarityConfig config = getRarityConfig(item.getRarity().getName());
        if (config != null) {
            return config.getDrawSuccessSound();
        }

        return null;
    }

    @Nullable
    public SoundEvent getDrawCooldownSound() {
        return drawCooldownSound;
    }

    @Nullable
    public SoundEvent getDrawLimitReachedSound() {
        return drawLimitReachedSound;
    }

    // === 失败音效 Getters（对标 TradeEntry）===

    @Nullable
    public SoundEvent getDrawConditionFailSound() {
        return drawConditionFailSound;
    }

    @Nullable
    public SoundEvent getDrawFailSound() {
        return drawFailSound;
    }

    /**
     * 获取当前总抽奖次数。
     */
    public int getTotalDraws(ArcQuestPlayer data) {
        return data.getGachaDrawCount(shopDefinition.getShopId());
    }

    /**
     * 增加抽奖次数计数。
     */
    public void incrementDrawCount(ArcQuestPlayer data, long nowRealTime, long nowGameTime, long nowDayTime) {
        data.incrementGachaDrawCount(shopDefinition.getShopId());
    }

    /**
     * 获取剩余可抽奖次数。
     *
     * @return 剩余次数，-1 表示无限
     */
    public int getRemainingDraws(ArcQuestPlayer data) {
        if (maxDraws < 0) return -1; // 无限
        int totalDraws = getTotalDraws(cap);
        return Math.max(0, maxDraws - totalDraws);
    }

    /**
     * 执行一次抽奖。
     *
     * @param cap       玩家能力数据
     * @param pityCount 当前保底计数
     * @return 抽奖结果
     */
    public DrawResult performDraw(ArcQuestPlayer data, int pityCount) {
        return performDraw(null, cap, pityCount);
    }

    /**
     * 执行一次抽奖（支持动态权重）。
     *
     * @param player    玩家实体（用于 ICondition）
     * @param cap       玩家能力数据
     * @param pityCount 当前保底计数
     * @return 抽奖结果
     */
    public DrawResult performDraw(ServerPlayer player,
                                  ArcQuestPlayer data,
                                  int pityCount) {
        GachaItem drawnItem;
        boolean isPityTriggered = false;

        // 检查是否触发保底
        if (pityConfig != null && pityCount >= pityConfig.getPityThreshold()) {
            // 优先使用指定物品 ID
            String guaranteedItemId = pityConfig.getGuaranteedItemId();
            if (guaranteedItemId != null) {
                drawnItem = gachaPool.getItemById(guaranteedItemId);
            } else {
                // 否则按稀有度抽取
                drawnItem = gachaPool.drawFromRarity(pityConfig.getGuaranteedRarity(), player, cap);
            }
            isPityTriggered = true;
        } else {
            drawnItem = gachaPool.draw(player, cap);
        }

        return new DrawResult(drawnItem, isPityTriggered);
    }

    /**
     * 检查是否有抽奖次数限制。
     *
     * @return true 如果设置了 maxDraws > 0
     */
    public boolean hasLimit() {
        return maxDraws > 0;
    }

    /**
     * 检查是否有冷却时间。
     *
     * @return true 如果 cooldownType != NONE
     */
    public boolean hasCooldown() {
        return cooldownType != CooldownType.NONE;
    }

    /**
     * 抽奖结果记录。
     */
    public record DrawResult(GachaItem item, boolean pityTriggered) {
    }

    /**
     * 稀有度配置（对标 TradeEntry 的主题色和音效）。
     */
    public static class RarityConfig {
        private final int themeColor;  // ARGB 颜色值
        @Nullable
        private final SoundEvent drawSuccessSound;

        public RarityConfig(int themeColor, @Nullable SoundEvent drawSuccessSound) {
            this.themeColor = themeColor;
            this.drawSuccessSound = drawSuccessSound;
        }

        public int getThemeColor() {
            return themeColor;
        }

        @Nullable
        public SoundEvent getDrawSuccessSound() {
            return drawSuccessSound;
        }
    }
}
