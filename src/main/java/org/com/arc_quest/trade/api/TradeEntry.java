package org.com.arc_quest.trade.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.dialogue.api.CooldownType;
import org.com.arc_quest.quest.api.ICondition;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 不可变的单个交易项定义。
 * <p>
 * 由 {@link org.com.arc_quest.trade.builder.TradeEntryBuilder} 构建。
 */
public final class TradeEntry {

    private final String entryId;
    private final Component displayName;
    @Nullable
    private final Component description;
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
    private final ResourceLocation iconOverride;
    private final int sortOrder;
    private final int themeColor;  // 商品主题色（ARGB），-1 表示使用商店默认
    
    /**
     * 限购恢复条件：返回 true 时重置购买计数。
     * 可以是冷却自动恢复，或自定义条件。
     */
    @Nullable
    private final Predicate<ServerPlayer> purchaseResetCondition;

    public TradeEntry(String entryId,
                      Component displayName,
                      @Nullable Component description,
                      List<ITradeOffer> costs,
                      List<ITradeOffer> rewards,
                      @Nullable TradeCategory category,
                      @Nullable ICondition visibleCondition,
                      @Nullable ICondition canBuyCondition,
                      CooldownType cooldownType,
                      long cooldownValue,
                      int resetTimeTicks,
                      int maxPurchases,
                      @Nullable ResourceLocation iconOverride,
                      int sortOrder,
                      int themeColor,
                      @Nullable Predicate<ServerPlayer> purchaseResetCondition) {
        Objects.requireNonNull(entryId);
        Objects.requireNonNull(displayName);
        if (rewards.isEmpty()) {
            throw new IllegalArgumentException("TradeEntry '" + entryId + "' must have at least one reward");
        }
        this.entryId = entryId;
        this.displayName = displayName;
        this.description = description;
        this.costs = Collections.unmodifiableList(costs);
        this.rewards = Collections.unmodifiableList(rewards);
        this.category = category;
        this.visibleCondition = visibleCondition;
        this.canBuyCondition = canBuyCondition;
        this.cooldownType = cooldownType != null ? cooldownType : CooldownType.NONE;
        this.cooldownValue = cooldownValue;
        this.resetTimeTicks = resetTimeTicks;
        this.maxPurchases = maxPurchases;
        this.iconOverride = iconOverride;
        this.sortOrder = sortOrder;
        this.themeColor = themeColor;
        this.purchaseResetCondition = purchaseResetCondition;
    }

    public String getEntryId() { return entryId; }
    public Component getDisplayName() { return displayName; }
    @Nullable public Component getDescription() { return description; }
    public List<ITradeOffer> getCosts() { return costs; }
    public List<ITradeOffer> getRewards() { return rewards; }
    @Nullable public TradeCategory getCategory() { return category; }
    
    /**
     * 获取可见性条件（控制商品是否显示）。
     * @return 可见性条件，null 表示始终可见
     */
    @Nullable public ICondition getVisibleCondition() { return visibleCondition; }
    
    /**
     * 获取购买资格条件（控制商品是否可购买）。
     * @return 购买资格条件，null 表示无额外限制
     */
    @Nullable public ICondition getCanBuyCondition() { return canBuyCondition; }
    public CooldownType getCooldownType() { return cooldownType; }
    public long getCooldownValue() { return cooldownValue; }
    public int getResetTimeTicks() { return resetTimeTicks; }

    /**
     * 最大购买次数。-1 = 无限。
     */
    public int getMaxPurchases() { return maxPurchases; }

    @Nullable public ResourceLocation getIconOverride() { return iconOverride; }
    public int getSortOrder() { return sortOrder; }

    /**
     * 获取商品主题色（ARGB）。-1 表示使用商店默认色。
     */
    public int getThemeColor() { return themeColor; }

    /**
     * 获取限购恢复条件。
     * @return 恢复条件，null 表示不自动恢复
     */
    @Nullable
    public Predicate<ServerPlayer> getPurchaseResetCondition() { 
        return purchaseResetCondition; 
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
