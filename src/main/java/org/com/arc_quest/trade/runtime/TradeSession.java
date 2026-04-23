package org.com.arc_quest.trade.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.dialogue.runtime.ICooldownRecord;
import org.com.arc_quest.dialogue.api.CooldownType;
import org.com.arc_quest.dialogue.runtime.UnifiedCooldownManager;
import org.com.arc_quest.dialogue.util.TimeSanitizer;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.trade.api.ITradeOffer;
import org.com.arc_quest.trade.api.TradeEntry;
import org.com.arc_quest.trade.api.TradeShopDefinition;
import org.slf4j.Logger;

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
            return TradeResult.fail("arc_quest.trade.error.not_found");
        }

        IQuestCapability cap = getCap();
        
        // 第一步：尝试重置过期冷却（必须在 canPurchase 之前）
        if (entry.hasLimit() || entry.hasCooldown()) {
            checkAndResetPurchases(entryId, entry);
        }
        
        // 第二步：综合判断
        if (!TradeEntryStateResolver.canPurchase(player, cap, shop.getShopId(), entry)) {
            // 细分错误原因
            if (!TradeEntryStateResolver.isVisible(player, cap, entry)) {
                return TradeResult.fail("arc_quest.trade.error.not_visible");
            }
            if (TradeEntryStateResolver.isPurchaseLimitReached(cap, shop.getShopId(), entry)) {
                return TradeResult.fail("arc_quest.trade.error.max_purchases");
            }
            if (TradeEntryStateResolver.isOnCooldown(player, cap, shop.getShopId(), entry)) {
                return TradeResult.fail("arc_quest.trade.error.on_cooldown");
            }
            // 默认：购买资格条件不满足
            return TradeResult.fail("arc_quest.trade.error.condition_not_met");
        }

        for (ITradeOffer cost : entry.getCosts()) {
            if (!cost.canAfford(player)) {
                LOGGER.warn("[Trade]  Cannot afford cost: entry={}, cost={}", entryId, cost);
                return TradeResult.fail("arc_quest.trade.error.cannot_afford");
            }
        }

        for (ITradeOffer cost : entry.getCosts()) {
            cost.execute(player);
        }

        for (ITradeOffer reward : entry.getRewards()) {
            reward.execute(player);
        }

        boolean shouldRecordCooldown = TradeEntryStateResolver.shouldRecordCooldown(getCap(), shop.getShopId(), entry);
        
        TradeEntryStateResolver.recordPurchase(getCap(), shop.getShopId(), entryId);

        if (shouldRecordCooldown) {
            TradeEntryStateResolver.recordCooldown(player, getCap(), shop.getShopId(), entryId);
        }

        LOGGER.info("[Trade] Player {} purchased '{}' from shop '{}'",
                player.getName().getString(), entryId, shop.getShopId());

        return TradeResult.success();
    }

    /**
     * 检查交易项是否对当前玩家可见（可见性条件满足）
     */
    public boolean isEntryVisible(TradeEntry entry) {
        IQuestCapability cap = getCap();
        return TradeEntryStateResolver.isVisible(player, cap, entry);
    }

    /**
     * 检查交易项是否可购买（综合判断）
     */
    public boolean canPurchase(TradeEntry entry) {
        IQuestCapability cap = getCap();
        return TradeEntryStateResolver.canPurchase(player, cap, shop.getShopId(), entry);
    }

    public int getPurchaseCount(String entryId) {
        IQuestCapability cap = getCap();
        return cap.getTradeDataStore().getPurchaseCount(shop.getShopId(), entryId);
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

        IQuestCapability cap = getCap();
        ICooldownRecord record = cap.getTradeDataStore().getCooldown(shop.getShopId(), entryId);
        if (!record.exists()) return 0;

        long nowRealTime = TimeSanitizer.getCurrentRealTime();
        long nowGameTime = TimeSanitizer.getCurrentGameTime(player);
        long nowDayTime  = TimeSanitizer.getCurrentDayTime(player);

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
        
        IQuestCapability cap = getCap();
        int currentCount = cap.getTradeDataStore().getPurchaseCount(shop.getShopId(), entryId);
        if (currentCount == 0) return;
        
        boolean shouldReset = TradeEntryStateResolver.shouldResetByCooldown(player, cap, shop.getShopId(), entry);

        if (!shouldReset) {
            try {
                shouldReset = resetCondition.test(player);
                if (shouldReset) {
                    LOGGER.info("[Trade] Purchase limit reset by custom condition for entry={}", entryId);
                }
            } catch (Exception e) {
                LOGGER.warn("[Trade] Error evaluating purchase reset condition for entry={}: {}", 
                        entryId, e.getMessage());
            }
        }
        
        if (shouldReset && currentCount > 0) {
            TradeEntryStateResolver.resetPurchaseAndCooldown(cap, shop.getShopId(), entryId);
        }
    }

    private IQuestCapability getCap() {
        return QuestCapabilityProvider.getOrNull(player);
    }

    public TradeShopDefinition getShop() { return shop; }
    public ServerPlayer getPlayer() { return player; }

    public record TradeResult(boolean succeeded, String errorKey) {
        public static TradeResult success() {
            return new TradeResult(true, null);
        }
        
        public static TradeResult fail(String errorKey) {
            return new TradeResult(false, errorKey);
        }
    }
}
