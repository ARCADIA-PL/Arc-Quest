package org.arcadia.arc_quest.trade.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import org.arcadia.arc_quest.dialogue.api.CooldownType;
import org.arcadia.arc_quest.quest.api.ICondition;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 不可变的单个交易项定义。
 * <p>
 * 由 {@link org.arcadia.arc_quest.trade.builder.TradeEntryBuilder TradeEntryBuilder构建器} 构建。
 */
public final class TradeEntry {

    private final String entryId;
    private final TradeText displayNameText;
    @Nullable
    private final TradeText descriptionText;
    private final List<ITradeOffer> costs;
    private final List<ITradeOffer> rewards;
    @Nullable
    private final TradeCategory category;
    /**
     * 可见性条件：控制商品是否在 UI 中显示。
     * <p>
     * 客户端和服务端都会检查此条件，但仅用于渲染优化。
     */
    @Nullable
    private final ICondition visibleCondition;
    /**
     * 购买资格条件：控制玩家是否可以购买商品。
     * <p>
     * 仅在服务端执行交易时检查，是权威的业务逻辑判断。
     */
    @Nullable
    private final ICondition canBuyCondition;
    private final CooldownType cooldownType;
    private final long cooldownValue;
    private final int resetTimeTicks;
    private final int maxPurchases;
    @Nullable
    private final ResourceLocation rewardIcon;
    @Nullable
    private final ResourceLocation costIcon;
    private final int sortOrder;
    private final int themeColor;  // 商品主题色（ARGB），-1 表示使用商店默认

    /**
     * 限购恢复条件：返回 true 时重置购买计数。
     * 可以是冷却自动恢复，或自定义条件。
     */
    @Nullable
    private final Predicate<ServerPlayer> purchaseResetCondition;

    // 音效配置
    @Nullable
    private final SoundEvent purchaseSuccessSound;
    @Nullable
    private final SoundEvent purchaseFailSound;
    @Nullable
    private final SoundEvent cooldownSound; // 冷却中音效
    @Nullable
    private final SoundEvent limitReachedSound; // 限购已满音效
    @Nullable
    private final SoundEvent conditionFailSound; // 条件不满足音效

    public TradeEntry(String entryId,
                      TradeText displayNameText,
                      @Nullable TradeText descriptionText,
                      List<ITradeOffer> costs,
                      List<ITradeOffer> rewards,
                      @Nullable TradeCategory category,
                      @Nullable ICondition visibleCondition,
                      @Nullable ICondition canBuyCondition,
                      CooldownType cooldownType,
                      long cooldownValue,
                      int resetTimeTicks,
                      int maxPurchases,
                      @Nullable ResourceLocation rewardIcon,
                      @Nullable ResourceLocation costIcon,
                      int sortOrder,
                      int themeColor,
                      @Nullable Predicate<ServerPlayer> purchaseResetCondition,
                      @Nullable SoundEvent purchaseSuccessSound,
                      @Nullable SoundEvent purchaseFailSound,
                      @Nullable SoundEvent cooldownSound,
                      @Nullable SoundEvent limitReachedSound,
                      @Nullable SoundEvent conditionFailSound) {
        Objects.requireNonNull(entryId);
        Objects.requireNonNull(displayNameText);
        if (rewards.isEmpty()) {
            throw new IllegalArgumentException("TradeEntry '" + entryId + "' must have at least one reward");
        }
        this.entryId = entryId;
        this.displayNameText = displayNameText;
        this.descriptionText = descriptionText;
        this.costs = Collections.unmodifiableList(costs);
        this.rewards = Collections.unmodifiableList(rewards);
        this.category = category;
        this.visibleCondition = visibleCondition;
        this.canBuyCondition = canBuyCondition;
        this.cooldownType = cooldownType != null ? cooldownType : CooldownType.NONE;
        this.cooldownValue = cooldownValue;
        this.resetTimeTicks = resetTimeTicks;
        this.maxPurchases = maxPurchases;
        this.rewardIcon = rewardIcon;
        this.costIcon = costIcon;
        this.sortOrder = sortOrder;
        this.themeColor = themeColor;
        this.purchaseResetCondition = purchaseResetCondition;
        this.purchaseSuccessSound = purchaseSuccessSound;
        this.purchaseFailSound = purchaseFailSound;
        this.cooldownSound = cooldownSound;
        this.limitReachedSound = limitReachedSound;
        this.conditionFailSound = conditionFailSound;
    }

    public String getEntryId() {
        return entryId;
    }

    public Component getDisplayName() {
        return displayNameText.resolveFallback();
    }

    public Component getDisplayName(ServerPlayer player, TradeTextContext context) {
        return displayNameText.resolve(context);
    }

    public TradeText getDisplayNameText() {
        return displayNameText;
    }

    @Nullable
    public Component getDescription() {
        return descriptionText != null ? descriptionText.resolveFallback() : null;
    }

    @Nullable
    public Component getDescription(ServerPlayer player, TradeTextContext context) {
        return descriptionText != null ? descriptionText.resolve(context) : null;
    }

    @Nullable
    public TradeText getDescriptionText() {
        return descriptionText;
    }

    public List<ITradeOffer> getCosts() {
        return costs;
    }

    public List<ITradeOffer> getRewards() {
        return rewards;
    }

    @Nullable
    public TradeCategory getCategory() {
        return category;
    }

    /**
     * 获取可见性条件（控制商品是否显示）。
     *
     * @return 可见性条件，null 表示始终可见
     */
    @Nullable
    public ICondition getVisibleCondition() {
        return visibleCondition;
    }

    /**
     * 获取购买资格条件（控制商品是否可购买）。
     *
     * @return 购买资格条件，null 表示无额外限制
     */
    @Nullable
    public ICondition getCanBuyCondition() {
        return canBuyCondition;
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

    /**
     * 最大购买次数。-1 = 无限。
     */
    public int getMaxPurchases() {
        return maxPurchases;
    }

    @Nullable
    public ResourceLocation getRewardIcon() {
        return rewardIcon;
    }

    @Nullable
    public ResourceLocation getCostIcon() {
        return costIcon;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    /**
     * 获取商品主题色（ARGB）。-1 表示使用商店默认色。
     */
    public int getThemeColor() {
        return themeColor;
    }

    /**
     * 获取限购恢复条件。
     *
     * @return 恢复条件，null 表示不自动恢复
     */
    @Nullable
    public Predicate<ServerPlayer> getPurchaseResetCondition() {
        return purchaseResetCondition;
    }

    @Nullable
    public SoundEvent getPurchaseSuccessSound() {
        return purchaseSuccessSound;
    }

    @Nullable
    public SoundEvent getPurchaseFailSound() {
        return purchaseFailSound;
    }

    @Nullable
    public SoundEvent getCooldownSound() {
        return cooldownSound;
    }

    @Nullable
    public SoundEvent getLimitReachedSound() {
        return limitReachedSound;
    }

    @Nullable
    public SoundEvent getConditionFailSound() {
        return conditionFailSound;
    }

    /**
     * 是否有购买次数限制
     */
    public boolean hasLimit() {
        return maxPurchases > 0;
    }

    /**
     * 是否有冷却
     */
    public boolean hasCooldown() {
        return cooldownType != CooldownType.NONE;
    }

    @Override
    public String toString() {
        return "TradeEntry[" + entryId + ", costs=" + costs.size() + ", rewards=" + rewards.size() + "]";
    }
}
