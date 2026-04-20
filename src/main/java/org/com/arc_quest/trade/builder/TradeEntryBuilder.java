package org.com.arc_quest.trade.builder;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import org.com.arc_quest.dialogue.api.CooldownType;
import org.com.arc_quest.quest.api.ICondition;
import org.com.arc_quest.trade.api.ITradeOffer;
import org.com.arc_quest.trade.api.TradeCategory;
import org.com.arc_quest.trade.api.TradeEntry;
import org.com.arc_quest.trade.offer.CommandTradeOffer;
import org.com.arc_quest.trade.offer.EffectTradeOffer;
import org.com.arc_quest.trade.offer.FlagTradeOffer;
import org.com.arc_quest.trade.offer.ItemTradeOffer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 流式构建单个 {@link TradeEntry}。
 *
 * <pre>{@code
 * TradeEntryBuilder.create("buy_diamond_sword")
 *     .displayName("钻石剑")
 *     .costItem(Items.EMERALD, 10)
 *     .rewardItem(Items.DIAMOND_SWORD, 1)
 *     .maxPurchases(3)
 *     .cooldownGameDay()
 *     .build();
 * }</pre>
 */
public final class TradeEntryBuilder {

    private final String entryId;
    private final List<ITradeOffer> costs = new ArrayList<>();
    private final List<ITradeOffer> rewards = new ArrayList<>();
    private Component displayName;
    @Nullable private Component description;
    @Nullable private TradeCategory category;
    /**
     * 可见性条件：控制商品是否在 UI 中显示。
     */
    @Nullable private ICondition visibleCondition;
    /**
     * 购买资格条件：控制玩家是否可以购买商品。
     */
    @Nullable private ICondition canBuyCondition;
    private CooldownType cooldownType = CooldownType.NONE;
    private long cooldownValue = 0;
    private int resetTimeTicks = 0;
    private int maxPurchases = -1;
    @Nullable private ResourceLocation iconOverride;
    private int sortOrder = 0;
    private int themeColor = -1;  // -1 表示使用商店默认
    @Nullable private Predicate<ServerPlayer> purchaseResetCondition;

    private TradeEntryBuilder(String entryId) {
        this.entryId = Objects.requireNonNull(entryId);
    }

    public static TradeEntryBuilder create(String entryId) {
        return new TradeEntryBuilder(entryId);
    }

    // ════════════════════════════════════════
    //  基本属性
    // ════════════════════════════════════════

    public TradeEntryBuilder displayName(String literal) {
        this.displayName = Component.literal(literal);
        return this;
    }

    public TradeEntryBuilder displayName(Component name) {
        this.displayName = name;
        return this;
    }

    public TradeEntryBuilder description(String literal) {
        this.description = Component.literal(literal);
        return this;
    }

    public TradeEntryBuilder description(Component desc) {
        this.description = desc;
        return this;
    }

    public TradeEntryBuilder category(TradeCategory cat) {
        this.category = cat;
        return this;
    }

    public TradeEntryBuilder sortOrder(int order) {
        this.sortOrder = order;
        return this;
    }

    public TradeEntryBuilder icon(ResourceLocation texture) {
        this.iconOverride = texture;
        return this;
    }

    // ════════════════════════════════════════
    //  主题色配置（覆盖商店默认）
    // ════════════════════════════════════════

    /**
     * 设置商品主题色（ARGB 整数），覆盖商店默认色。
     */
    public TradeEntryBuilder themeColor(int color) {
        this.themeColor = color;
        return this;
    }

    /**
     * 从 ChatFormatting 设置商品主题色。
     */
    public TradeEntryBuilder themeColor(ChatFormatting formatting) {
        Integer rgb = formatting.getColor();
        if (rgb != null) {
            this.themeColor = 0xFF000000 | rgb;
        }
        return this;
    }

    public TradeEntryBuilder themeColorRGB(int r, int g, int b) {
        this.themeColor = (0xFF << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        return this;
    }

    // ════════════════════════════════════════
    //  成本（玩家支付）
    // ════════════════════════════════════════

    public TradeEntryBuilder cost(ITradeOffer offer) {
        this.costs.add(offer);
        return this;
    }

    public TradeEntryBuilder costItem(Item item, int count) {
        this.costs.add(ItemTradeOffer.cost(item, count));
        return this;
    }

    public TradeEntryBuilder costFlag(String flag) {
        this.costs.add(FlagTradeOffer.requireFlag(flag));
        return this;
    }

    // ════════════════════════════════════════
    //  奖励（玩家获得）
    // ════════════════════════════════════════

    public TradeEntryBuilder reward(ITradeOffer offer) {
        this.rewards.add(offer);
        return this;
    }

    public TradeEntryBuilder rewardItem(Item item, int count) {
        this.rewards.add(ItemTradeOffer.reward(item, count));
        return this;
    }

    public TradeEntryBuilder rewardEffect(MobEffect effect, int durationSeconds, int amplifier) {
        this.rewards.add(EffectTradeOffer.reward(effect, durationSeconds * 20, amplifier));
        return this;
    }

    public TradeEntryBuilder rewardEffect(MobEffect effect, int durationSeconds) {
        this.rewards.add(EffectTradeOffer.reward(effect, durationSeconds));
        return this;
    }

    public TradeEntryBuilder rewardFlag(String flag) {
        this.rewards.add(FlagTradeOffer.rewardFlag(flag));
        return this;
    }

    public TradeEntryBuilder rewardCommand(String command, String displayText) {
        this.rewards.add(CommandTradeOffer.reward(command, displayText));
        return this;
    }

    // ════════════════════════════════════════
    //  条件
    // ════════════════════════════════════════
     
    /**
     * 设置可见性条件（控制商品是否显示）。
     * <p>
     * 客户端和服务端都会检查此条件，但仅用于渲染优化。
     * 如果条件不满足，商品不会在 UI 中显示。
     *
     * @param cond 可见性条件
     */
    public TradeEntryBuilder visibleCondition(ICondition cond) {
        this.visibleCondition = cond;
        return this;
    }
    
    /**
     * 设置购买资格条件（控制商品是否可购买）。
     * <p>
     * 仅在服务端执行交易时检查，是权威的业务逻辑判断。
     * 即使商品可见，如果此条件不满足，也无法购买。
     *
     * @param cond 购买资格条件
     */
    public TradeEntryBuilder canBuyCondition(ICondition cond) {
        this.canBuyCondition = cond;
        return this;
    }
    
    /**
     * 同时设置可见性和购买资格条件（两者相同）。
     * <p>
     * 便捷方法，适用于可见性和购买资格使用相同条件的场景。
     *
     * @param cond 条件（同时用于可见性和购买资格）
     */
    public TradeEntryBuilder condition(ICondition cond) {
        this.visibleCondition = cond;
        this.canBuyCondition = cond;
        return this;
    }

    // ════════════════════════════════════════
    //  限购
    // ════════════════════════════════════════

    public TradeEntryBuilder maxPurchases(int max) {
        this.maxPurchases = max;
        return this;
    }

    public TradeEntryBuilder unlimited() {
        this.maxPurchases = -1;
        return this;
    }

    /**
     * 设置限购恢复条件（自定义 Predicate）。
     * <p>
     * 当条件返回 true 时，购买计数将被重置。
     * 例如：每天凌晨重置、完成任务后重置等。
     *
     * @param condition 恢复条件，传入 ServerPlayer 进行判断
     */
    public TradeEntryBuilder purchaseResetCondition(Predicate<ServerPlayer> condition) {
        this.purchaseResetCondition = condition;
        return this;
    }

    /**
     * 使用冷却机制自动恢复限购。
     * <p>
     * 这会自动设置一个基于冷却时间的恢复条件。
     */
    public TradeEntryBuilder purchaseResetByCooldown() {
        // 标记为需要基于冷却恢复，实际逻辑在 TradeSession 中处理
        this.purchaseResetCondition = player -> false; // 占位，实际由冷却系统处理
        return this;
    }

    // ════════════════════════════════════════
    //  冷却
    // ════════════════════════════════════════

    public TradeEntryBuilder cooldown(long seconds) {
        this.cooldownType = CooldownType.SECONDS;
        this.cooldownValue = Math.max(0, seconds);
        return this;
    }

    public TradeEntryBuilder cooldownGameDay() {
        this.cooldownType = CooldownType.GAME_DAY;
        this.cooldownValue = 1;
        return this;
    }

    public TradeEntryBuilder cooldownGameTick(int resetTick) {
        this.cooldownType = CooldownType.GAME_TICK;
        this.cooldownValue = 0;
        this.resetTimeTicks = Math.max(0, Math.min(resetTick, 23999));
        return this;
    }

    // ════════════════════════════════════════
    //  构建
    // ════════════════════════════════════════

    public TradeEntry build() {
        if (displayName == null) {
            displayName = Component.literal(entryId);
        }
        if (rewards.isEmpty()) {
            throw new IllegalStateException("TradeEntry '" + entryId + "' has no rewards defined");
        }
        return new TradeEntry(
                entryId, displayName, description,
                List.copyOf(costs), List.copyOf(rewards),
                category, visibleCondition, canBuyCondition,
                cooldownType, cooldownValue, resetTimeTicks,
                maxPurchases, iconOverride, sortOrder, themeColor,
                purchaseResetCondition
        );
    }
}
