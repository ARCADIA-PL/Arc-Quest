package org.arcadia.arc_quest.trade.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.dialogue.api.CooldownType;
import org.arcadia.arc_quest.dialogue.runtime.ICooldownRecord;
import org.arcadia.arc_quest.dialogue.runtime.UnifiedCooldownManager;
import org.arcadia.arc_quest.dialogue.util.TimeSanitizer;
import org.arcadia.arc_quest.core.condition.ConditionGuard;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.trade.api.CostShortfallLine;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.network.RejectCodeDictionary;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 交易运行时会话 —— 管理玩家的交易状态、购买历史和冷却。
 * <p>
 * 持久化数据存储在玩家 Capability 的 NBT 中（{@code "TradeData"} 子标签）。
 */
public final class TradeSession {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final ServerPlayer player;
    private final TradeShopDefinition shop;

    public TradeSession(ServerPlayer player, TradeShopDefinition shop) {
        this.player = player;
        this.shop = shop;
    }

    /**
     * 尝试执行一次交易。
     *
     * @param entryId 交易项 ID
     * @return 交易结果
     */
    public TradeResult executeTrade(String entryId) {
        TradeEntry entry = shop.getEntry(entryId);
        if (entry == null) {
            return TradeResult.fail(RejectCodeDictionary.errorKey(RejectCodeDictionary.Domain.TRADE, RejectCodeDictionary.Code.UNKNOWN));
        }

        ArcQuestPlayer data = getData();

        // 第一步：尝试重置过期冷却（必须在 canPurchase 之前）
        if (entry.hasLimit() || entry.hasCooldown()) {
            checkAndResetPurchases(entryId, entry);
        }

        // 第二步：综合判断
        if (!TradeEntryStateResolver.canPurchase(player, data, shop.getShopId(), entry)) {
            // 细分错误原因（统一优先级：cooldown > limit > condition > afford）
            if (!TradeEntryStateResolver.isVisible(player, data, entry)) {
                return TradeResult.fail(RejectCodeDictionary.errorKey(RejectCodeDictionary.Domain.TRADE, RejectCodeDictionary.Code.SESSION_NOT_VISIBLE));
            }
            if (TradeEntryStateResolver.isOnCooldown(player, data, shop.getShopId(), entry)) {
                return TradeResult.fail(RejectCodeDictionary.errorKey(RejectCodeDictionary.Domain.TRADE, RejectCodeDictionary.Code.SESSION_ON_COOLDOWN));
            }
            if (TradeEntryStateResolver.isPurchaseLimitReached(data, shop.getShopId(), entry)) {
                return TradeResult.fail(RejectCodeDictionary.errorKey(RejectCodeDictionary.Domain.TRADE, RejectCodeDictionary.Code.SESSION_MAX_DRAWS_REACHED));
            }
            // 默认：购买资格条件不满足
            return TradeResult.fail(RejectCodeDictionary.errorKey(RejectCodeDictionary.Domain.TRADE, RejectCodeDictionary.Code.SESSION_CONDITION_NOT_MET));
        }

        for (ITradeOffer cost : entry.getCosts()) {
            if (!cost.canAfford(player)) {
                LOGGER.warn("[Trade]  Cannot afford cost: entry={}, cost={}", entryId, cost);
                return TradeResult.fail(
                        RejectCodeDictionary.errorKey(RejectCodeDictionary.Domain.TRADE, RejectCodeDictionary.Code.CANNOT_AFFORD),
                        collectShortfallLines(entry.getCosts())
                );
            }
        }

        TradeTransactionCoordinator.TransactionResult transaction = new TradeTransactionCoordinator().execute(
                player, entry.getCosts(), entry.getRewards());
        if (!transaction.succeeded()) {
            LOGGER.error("[Trade] Transaction failed: player={}, shop={}, entry={}, fullyReversible={}, rollbackSucceeded={}",
                    player.getName().getString(), shop.getShopId(), entryId,
                    transaction.fullyReversible(), transaction.rollbackSucceeded(), transaction.failure());
            return TradeResult.fail(RejectCodeDictionary.errorKey(
                    RejectCodeDictionary.Domain.TRADE, RejectCodeDictionary.Code.TRANSACTION_FAILED));
        }

        boolean shouldRecordCooldown = TradeEntryStateResolver.shouldRecordCooldown(getData(), shop.getShopId(), entry);

        TradeEntryStateResolver.recordPurchase(getData(), shop.getShopId(), entryId);

        if (shouldRecordCooldown) {
            TradeEntryStateResolver.recordCooldown(player, getData(), shop.getShopId(), entryId);
        }

        LOGGER.info("[Trade] Player {} purchased '{}' from shop '{}'",
                player.getName().getString(), entryId, shop.getShopId());

        return TradeResult.success();
    }

    /**
     * 检查交易项是否对当前玩家可见（可见性条件满足）
     */
    public boolean isEntryVisible(TradeEntry entry) {
        ArcQuestPlayer data = getData();
        return TradeEntryStateResolver.isVisible(player, data, entry);
    }

    /**
     * 检查交易项是否可购买（综合判断）
     */
    public boolean canPurchase(TradeEntry entry) {
        ArcQuestPlayer data = getData();
        return TradeEntryStateResolver.canPurchase(player, data, shop.getShopId(), entry);
    }

    public int getPurchaseCount(String entryId) {
        ArcQuestPlayer data = getData();
        return data.getTradeDataStore().getPurchaseCount(shop.getShopId(), entryId);
    }

    public int getRemainingPurchases(TradeEntry entry) {
        if (!entry.hasLimit()) return -1;
        return Math.max(0, entry.getMaxPurchases() - getPurchaseCount(entry.getEntryId()));
    }

    /**
     * 获取冷却剩余秒数（用于客户端显示）。
     * <p>
     * 冷却时间戳从 TradeDataStore 读取，通过 UnifiedCooldownManager 统一计算。
     */
    public int getCooldownRemaining(String entryId, TradeEntry entry) {
        if (!entry.hasCooldown()) return 0;

        ArcQuestPlayer data = getData();
        ICooldownRecord record = data.getTradeDataStore().getCooldown(shop.getShopId(), entryId);
        if (!record.exists()) return 0;

        long nowRealTime = TimeSanitizer.getCurrentRealTime();
        long nowGameTime = TimeSanitizer.getCurrentGameTime(player);
        long nowDayTime = TimeSanitizer.getCurrentDayTime(player);

        return switch (entry.getCooldownType()) {
            case NONE -> 0;
            case SECONDS -> {
                long elapsed = (nowRealTime - record.realTime()) / 1000;
                yield Math.max(0, (int) (entry.getCooldownValue() - elapsed));
            }
            case GAME_DAY -> {
                if (record.dayTime() < 0) yield 0;
                boolean onCooldown = UnifiedCooldownManager.isOnCooldown(
                        record, entry.getCooldownType(), (int) entry.getCooldownValue(),
                        entry.getResetTimeTicks(), nowRealTime, nowGameTime, nowDayTime);
                if (!onCooldown) yield 0;
                long currentDayTick = nowDayTime % 24000;
                yield Math.max(1, (int) (24000 - currentDayTick) / 20);
            }
            case GAME_TICK -> {
                int remainingTicks = UnifiedCooldownManager.getGameTickCooldownRemainingTicks(
                        record, entry.getResetTimeTicks(), nowGameTime, nowDayTime);
                yield Math.max(0, remainingTicks / 20);
            }
        };
    }


    /**
     * 获取格式化的冷却文本（适配三种冷却类型）
     */
    public String getCooldownText(String entryId, TradeEntry entry) {
        if (!entry.hasCooldown()) return "";

        int remaining = getCooldownRemaining(entryId, entry);
        if (remaining <= 0) return "";

        // GAME_DAY 类型特殊处理（原来显示翻译文本）
        if (entry.getCooldownType() == CooldownType.GAME_DAY) {
            return Component.translatable("arc_quest.trade.cooldown.game_day").getString();
        }

        // 统一的时间格式化
        if (remaining < 60) return remaining + "s";
        long minutes = remaining / 60;
        long seconds = remaining % 60;
        return String.format("%dm%ds", minutes, seconds);
    }

    /**
     * 检查并执行限购恢复。
     * <p>
     * 支持两种恢复机制：
     * <ol>
     *   <li>冷却自动恢复：基于 CooldownType 判断是否过期（仅当限购满时）</li>
     *   <li>自定义条件恢复：使用 Predicate<ServerPlayer> 判断</li>
     * </ol>
     */
    private void checkAndResetPurchases(String entryId, TradeEntry entry) {
        if (!entry.hasLimit()) return;

        var resetCondition = entry.getPurchaseResetCondition();
        if (resetCondition == null) return;

        ArcQuestPlayer data = getData();
        int currentCount = data.getTradeDataStore().getPurchaseCount(shop.getShopId(), entryId);
        if (currentCount == 0) return;

        boolean shouldReset = TradeEntryStateResolver.shouldResetByCooldown(player, data, shop.getShopId(), entry);

        if (!shouldReset) {
            shouldReset = ConditionGuard.evaluate(
                    () -> resetCondition.test(player),
                    false, LOGGER, "trade reset entry=" + entryId);
            if (shouldReset) {
                LOGGER.info("[Trade] Purchase limit reset by custom condition for entry={}", entryId);
            }
        }

        if (shouldReset && currentCount > 0) {
            TradeEntryStateResolver.resetPurchaseAndCooldown(data, shop.getShopId(), entryId);
        }
    }

    private ArcQuestPlayer getData() {
        return ArcQuestPlayerManager.get(player);
    }

    public TradeShopDefinition getShop() {
        return shop;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    private List<CostShortfallLine> collectShortfallLines(List<ITradeOffer> costs) {
        List<CostShortfallLine> lines = new ArrayList<>();
        for (ITradeOffer cost : costs) {
            lines.addAll(cost.buildShortfallLines(player));
        }
        return lines;
    }

    public record TradeResult(boolean succeeded, String errorKey, List<CostShortfallLine> shortfallLines) {
        public static TradeResult success() {
            return new TradeResult(true, null, List.of());
        }

        public static TradeResult fail(String errorKey) {
            return new TradeResult(false, errorKey, List.of());
        }

        public static TradeResult fail(String errorKey, List<CostShortfallLine> shortfallLines) {
            return new TradeResult(false, errorKey, shortfallLines != null ? List.copyOf(shortfallLines) : List.of());
        }
    }
}
