package org.arcadia.arc_quest.trade.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.dialogue.runtime.ICooldownRecord;
import org.arcadia.arc_quest.dialogue.runtime.UnifiedCooldownManager;
import org.arcadia.arc_quest.dialogue.util.TimeSanitizer;
import org.arcadia.arc_quest.capability.ArcQuestPlayer;
import org.arcadia.arc_quest.quest.capability.TradeDataStore;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.slf4j.Logger;

import java.util.Set;

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
 * {@link UnifiedCooldownManager}，不再经过 {@code ArcQuestPlayer} 的多层委托。
 */
public final class TradeEntryStateResolver {

    private static final Logger LOGGER = LogUtils.getLogger();

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

        long[] times = TimeSanitizer.getAllTimes(player);
        ICooldownRecord record = data.getTradeDataStore().getCooldown(shopId, entry.getEntryId());

        return UnifiedCooldownManager.isOnCooldown(record, entry.getCooldownType(),
                (int) entry.getCooldownValue(), entry.getResetTimeTicks(),
                times[0], times[1], times[2]);
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
        if (entry.getVisibleCondition() == null) return true;

        Set<ResourceLocation> completed = data.getCompletedQuestLocations();
        return entry.getVisibleCondition().test(player, completed, data.getAllFlags(), data.getAllVariables());
    }

    /**
     * 综合判断商品是否可购买（可见性 → 冷却 → 限购 → 购买资格）。
     */
    public static boolean canPurchase(ServerPlayer player, ArcQuestPlayer data, String shopId, TradeEntry entry) {
        if (!isVisible(player, data, entry)) return false;

        if (isOnCooldown(player, data, shopId, entry)) {
            LOGGER.debug("[Trade-State] Purchase blocked: on cooldown for {}", entry.getEntryId());
            return false;
        }

        if (isPurchaseLimitReached(data, shopId, entry)) {
            LOGGER.debug("[Trade-State] Purchase blocked: limit reached for {}", entry.getEntryId());
            return false;
        }

        if (entry.getCanBuyCondition() != null) {
            Set<ResourceLocation> completed = data.getCompletedQuestLocations();
            boolean canBuy = entry.getCanBuyCondition().test(player, completed, data.getAllFlags(), data.getAllVariables());
            if (!canBuy) {
                LOGGER.debug("[Trade-State] Purchase blocked: canBuyCondition not met for {}", entry.getEntryId());
                return false;
            }
        }
        return true;
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

        long[] times = TimeSanitizer.getAllTimes(player);
        ICooldownRecord record = data.getTradeDataStore().getCooldown(shopId, entry.getEntryId());

        boolean onCooldown = UnifiedCooldownManager.isOnCooldown(record, entry.getCooldownType(),
                (int) entry.getCooldownValue(), entry.getResetTimeTicks(),
                times[0], times[1], times[2]);

        if (!onCooldown) {
            LOGGER.info("[Trade-State] Cooldown expired, should reset: entry={}, hasLimit={}", entry.getEntryId(), entry.hasLimit());
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
            LOGGER.info("[Trade-State] Purchase limit reached, will record cooldown: entry={}, count={}/{}",
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
        LOGGER.info("[Trade-State] Purchase recorded: entry={}, newCount={}", entryId, newCount);
    }

    /**
     * 记录冷却时间戳。
     */
    public static void recordCooldown(ServerPlayer player, ArcQuestPlayer data, String shopId, String entryId) {
        long[] times = TimeSanitizer.getAllTimes(player);
        LOGGER.debug("[Trade-State] Recording cooldown: shop={}, entry={}", shopId, entryId);
        data.getTradeDataStore().recordCooldown(shopId, entryId, times[0], times[1], times[2]);
    }

    /**
     * 重置购买次数和冷却。
     */
    public static void resetPurchaseAndCooldown(ArcQuestPlayer data, String shopId, String entryId) {
        int oldCount = data.getTradeDataStore().getPurchaseCount(shopId, entryId);
        data.getTradeDataStore().resetEntry(shopId, entryId);
        LOGGER.info("[Trade-State] Reset purchase and cooldown: entry={}, oldCount={}", entryId, oldCount);
    }
}
