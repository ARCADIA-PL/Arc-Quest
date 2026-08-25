package org.arcadia.arc_quest.trade.runtime;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.core.execution.CoreDecision;
import org.arcadia.arc_quest.core.execution.CoreRule;
import org.arcadia.arc_quest.core.time.CooldownRecord;
import org.arcadia.arc_quest.quest.api.QuestConditionContext;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.quest.data.TradeDataStore;
import org.arcadia.arc_quest.trade.api.TradeEntry;

import java.util.Set;
import java.util.List;

/**
 * 交易商品状态解析器 - 统一判断和设置商品的各种状态。
 * <p>
 * 核心原则（冷却-限购语义规则）：
 * <ul>
 *   <li>有限购 + 有冷却：冷却仅在限购满时生效</li>
 *   <li>无限购 + 有冷却：每次购买都触发冷却</li>
 *   <li>有限购 + 无冷却：仅检查限购</li>
 * </ul>
 * <p>
 * 状态数据统一通过 {@link TradeDataStore} 读写，冷却判断委托
 * {@link org.arcadia.arc_quest.core.time.CooldownProcessor}，不再经过 {@code ArcQuestPlayer} 的多层委托。
 */
public final class TradeEntryStateResolver {
    private TradeEntryStateResolver() {
    }

    /**
     * 检查商品是否在冷却中。
     */
    public static boolean isOnCooldown(ServerPlayer player, ArcQuestPlayer data, String shopId, TradeEntry entry) {
        if (!entry.hasCooldown()) return false;

        if (entry.hasLimit()) {
            int currentCount = data.getTradeDataStore().getPurchaseCount(shopId, entry.getEntryId());
            if (currentCount < entry.getMaxPurchases()) return false;
        }

        var now = CoreProcessors.get().time().capture(player);
        CooldownRecord record = data.getTradeDataStore().getCooldown(shopId, entry.getEntryId());

        return CoreProcessors.get().cooldowns().isOnCooldown(
                record, entry.getCooldownType().toCorePolicy(
                        entry.getCooldownValue(), entry.getResetTimeTicks()),
                now);
    }

    /**
     * 检查商品是否达到限购上限。
     */
    public static boolean isPurchaseLimitReached(ArcQuestPlayer data, String shopId, TradeEntry entry) {
        if (!entry.hasLimit()) return false;
        int currentCount = data.getTradeDataStore().getPurchaseCount(shopId, entry.getEntryId());
        return currentCount >= entry.getMaxPurchases();
    }

    /**
     * 检查商品是否对玩家可见（可见性条件）。
     */
    public static boolean isVisible(ServerPlayer player, ArcQuestPlayer data, TradeEntry entry) {
        return isVisible(DecisionContext.create(player, data, "", entry));
    }

    /**
     * 综合判断商品是否可购买（可见性 → 冷却 → 限购 → 购买资格）。
     */
    public static boolean canPurchase(ServerPlayer player, ArcQuestPlayer data, String shopId, TradeEntry entry) {
        return evaluatePurchase(player, data, shopId, entry).allowed();
    }

    public static CoreDecision<PurchaseFailure> evaluatePurchase(
            ServerPlayer player, ArcQuestPlayer data, String shopId, TradeEntry entry) {
        DecisionContext context = DecisionContext.create(player, data, shopId, entry);
        CoreDecision<PurchaseFailure> decision = CoreProcessors.get().executions().decide(context, List.of(
                CoreRule.require(TradeEntryStateResolver::isVisible, PurchaseFailure.NOT_VISIBLE),
                CoreRule.require(candidate -> !isOnCooldown(candidate.player(), candidate.data(),
                        candidate.shopId(), candidate.entry()), PurchaseFailure.ON_COOLDOWN),
                CoreRule.require(candidate -> !isPurchaseLimitReached(candidate.data(), candidate.shopId(),
                        candidate.entry()), PurchaseFailure.LIMIT_REACHED),
                CoreRule.require(TradeEntryStateResolver::hasPurchaseConditionMet,
                        PurchaseFailure.CONDITION_NOT_MET)
        ));
        if (!decision.allowed()) {
            ArcQuestLog.debug(ArcQuestLog.Category.TRADE, "Purchase blocked: entry={}, reason={}",
                    entry.getEntryId(), decision.failure());
        }
        return decision;
    }

    private static boolean isVisible(DecisionContext context) {
        if (context.entry().getVisibleCondition() == null) return true;
        return CoreProcessors.get().conditions().evaluateSafely(
                context.entry().getVisibleCondition(), context.conditionContext(), false, null,
                "trade visibility entry=" + context.entry().getEntryId());
    }

    private static boolean hasPurchaseConditionMet(DecisionContext context) {
        if (context.entry().getCanBuyCondition() == null) return true;
        return CoreProcessors.get().conditions().evaluateSafely(
                context.entry().getCanBuyCondition(), context.conditionContext(), false, null,
                "trade purchase entry=" + context.entry().getEntryId());
    }

    public enum PurchaseFailure {
        NOT_VISIBLE,
        ON_COOLDOWN,
        LIMIT_REACHED,
        CONDITION_NOT_MET
    }

    private record DecisionContext(ServerPlayer player, ArcQuestPlayer data, String shopId,
                                   TradeEntry entry, QuestConditionContext conditionContext) {
        private static DecisionContext create(ServerPlayer player, ArcQuestPlayer data,
                                              String shopId, TradeEntry entry) {
            Set<ResourceLocation> completed = data.getCompletedQuestLocations();
            QuestConditionContext conditionContext = new QuestConditionContext(
                    player, completed, data.getAllFlags(), data.getAllVariables());
            return new DecisionContext(player, data, shopId, entry, conditionContext);
        }
    }

    /**
     * 检查是否应该重置购买次数（基于冷却过期）。
     */
    public static boolean shouldResetByCooldown(ServerPlayer player, ArcQuestPlayer data, String shopId, TradeEntry entry) {
        if (!entry.hasCooldown()) return false;

        if (entry.hasLimit()) {
            int currentCount = data.getTradeDataStore().getPurchaseCount(shopId, entry.getEntryId());
            if (currentCount < entry.getMaxPurchases()) return false;
        }

        var now = CoreProcessors.get().time().capture(player);
        CooldownRecord record = data.getTradeDataStore().getCooldown(shopId, entry.getEntryId());

        boolean onCooldown = CoreProcessors.get().cooldowns().isOnCooldown(
                record, entry.getCooldownType().toCorePolicy(
                        entry.getCooldownValue(), entry.getResetTimeTicks()),
                now);

        if (!onCooldown) {
            ArcQuestLog.info(ArcQuestLog.Category.TRADE, "Cooldown expired, should reset: entry={}, hasLimit={}", entry.getEntryId(), entry.hasLimit());
        }
        return !onCooldown;
    }

    /**
     * 检查是否需要记录冷却时间。
     */
    public static boolean shouldRecordCooldown(ArcQuestPlayer data, String shopId, TradeEntry entry) {
        if (!entry.hasCooldown()) return false;
        if (!entry.hasLimit()) return true;

        int currentCount = data.getTradeDataStore().getPurchaseCount(shopId, entry.getEntryId());
        int newCount = currentCount + 1;
        boolean shouldRecord = newCount >= entry.getMaxPurchases();
        if (shouldRecord) {
            ArcQuestLog.info(ArcQuestLog.Category.TRADE, "Purchase limit reached, will record cooldown: entry={}, count={}/{}",
                    entry.getEntryId(), newCount, entry.getMaxPurchases());
        }
        return shouldRecord;
    }

    /**
     * 记录购买（增加购买次数）。
     */
    public static void recordPurchase(ArcQuestPlayer data, String shopId, String entryId) {
        data.getTradeDataStore().incrementPurchase(shopId, entryId);
        int newCount = data.getTradeDataStore().getPurchaseCount(shopId, entryId);
        ArcQuestLog.info(ArcQuestLog.Category.TRADE, "Purchase recorded: entry={}, newCount={}", entryId, newCount);
    }

    /**
     * 记录冷却时间戳。
     */
    public static void recordCooldown(ServerPlayer player, ArcQuestPlayer data, String shopId, String entryId) {
        var now = CoreProcessors.get().time().capture(player);
        ArcQuestLog.debug(ArcQuestLog.Category.TRADE, "Recording cooldown: shop={}, entry={}", shopId, entryId);
        data.getTradeDataStore().recordCooldown(
                shopId, entryId, now.realTime(), now.gameTime(), now.dayTime());
    }

    /**
     * 重置购买次数和冷却。
     */
    public static void resetPurchaseAndCooldown(ArcQuestPlayer data, String shopId, String entryId) {
        int oldCount = data.getTradeDataStore().getPurchaseCount(shopId, entryId);
        data.getTradeDataStore().resetEntry(shopId, entryId);
        ArcQuestLog.info(ArcQuestLog.Category.TRADE, "Reset purchase and cooldown: entry={}, oldCount={}", entryId, oldCount);
    }
}
