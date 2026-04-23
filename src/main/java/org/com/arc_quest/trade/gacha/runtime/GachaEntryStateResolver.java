package org.com.arc_quest.trade.gacha.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.dialogue.runtime.UnifiedCooldownManager;
import org.com.arc_quest.dialogue.util.TimeSanitizer;
import org.com.arc_quest.quest.capability.GachaDataStore;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.slf4j.Logger;

/**
 * 抽奖状态解析器 —— 统一管理限购、冷却、条件检查逻辑。
 * <p>
 * 对标 {@link org.com.arc_quest.trade.runtime.TradeEntryStateResolver}，
 * 但针对抽奖系统的 Shop 级别管理进行了优化。
 * <p>
 * <b>冷却语义规则：</b>有限购时，冷却是限购的附属机制——限购未满时不检查冷却；
 * 无限购时，每次抽奖后均触发冷却计时。
 */
public final class GachaEntryStateResolver {

    private static final Logger LOGGER = LogUtils.getLogger();

    private GachaEntryStateResolver() {}

    /**
     * 综合判断是否可以抽奖。
     * <p>
     * 优先级：可见性 > 限购 > 冷却 > 条件
     *
     * @return true 如果可以抽奖
     */
    public static boolean canDraw(ServerPlayer player, IQuestCapability cap, String shopId, GachaShopDefinition shop) {
        if (!isVisible(player, cap, shop)) return false;
        if (isMaxDrawsReached(cap, shopId, shop)) return false;
        if (isOnCooldown(player, cap, shopId, shop)) return false;
        if (!hasConditionMet(player, cap, shop)) return false;
        return true;
    }

    /**
     * 检查奖池是否对玩家可见。
     */
    public static boolean isVisible(ServerPlayer player, IQuestCapability cap, GachaShopDefinition shop) {
        var visibleCondition = shop.getVisibleCondition();
        if (visibleCondition == null) return true;

        try {
            return visibleCondition.test(player,
                cap.getCompletedQuestLocations(),
                cap.getAllFlags(),
                cap.getAllVariables());
        } catch (Exception e) {
            LOGGER.warn("[Gacha] Error evaluating visible condition for shop={}: {}", shop.getShopId(), e.getMessage());
            return false;
        }
    }

    /**
     * 检查是否达到抽奖次数上限。
     */
    public static boolean isMaxDrawsReached(IQuestCapability cap, String shopId, GachaShopDefinition shop) {
        if (!shop.hasLimit()) return false;
        return cap.getGachaDrawCount(shopId) >= shop.getMaxDraws();
    }

    /**
     * 检查是否在冷却中。
     * <p>
     * 冷却时间戳存储于 {@link GachaDataStore}，不再依赖 {@code DialogueProgressStore}。
     */
    public static boolean isOnCooldown(ServerPlayer player, IQuestCapability cap, String shopId, GachaShopDefinition shop) {
        if (!shop.hasCooldown()) return false;

        if (shop.hasLimit() && cap.getGachaDrawCount(shopId) < shop.getMaxDraws()) {
            return false;
        }

        GachaDataStore.CooldownEntry entry = cap.getGachaDataStore().getDrawCooldown(shopId);
        if (!entry.exists()) return false;

        long nowRealTime = TimeSanitizer.getCurrentRealTime();
        long nowGameTime = TimeSanitizer.getCurrentGameTime(player);
        long nowDayTime  = TimeSanitizer.getCurrentDayTime(player);

        return isOnCooldown(entry, shop, nowRealTime, nowGameTime, nowDayTime);
    }

    /**
     * 检查前置条件是否满足。
     */
    public static boolean hasConditionMet(ServerPlayer player, IQuestCapability cap, GachaShopDefinition shop) {
        var drawCondition = shop.getDrawCondition();
        if (drawCondition == null) return true;

        try {
            return drawCondition.test(player,
                cap.getCompletedQuestLocations(),
                cap.getAllFlags(),
                cap.getAllVariables());
        } catch (Exception e) {
            LOGGER.warn("[Gacha] Error evaluating draw condition for shop={}: {}", shop.getShopId(), e.getMessage());
            return false;
        }
    }

    /**
     * 判断是否应该因为冷却过期而重置抽奖次数。
     */
    public static boolean shouldResetByCooldown(ServerPlayer player, IQuestCapability cap,
                                                 String shopId, GachaShopDefinition shop) {
        if (!shop.hasCooldown()) return false;

        if (shop.hasLimit()) {
            if (cap.getGachaDrawCount(shopId) < shop.getMaxDraws()) return false;
            if (!shop.shouldResetOnLimitReached()) return false;
        }

        GachaDataStore gachaStore = cap.getGachaDataStore();
        long nowDayTime = TimeSanitizer.getCurrentDayTime(player);

        // 时间回退检测：记录的 dayTime 大于当前 dayTime，说明 /time set 回退了时间
        GachaDataStore.CooldownEntry entry = gachaStore.getDrawCooldown(shopId);
        if (entry.exists() && entry.dayTime() > nowDayTime) {
            gachaStore.removeDrawCooldown(shopId);
            LOGGER.info("[Gacha-State] Cleared cooldown record due to time regression: shop={}", shopId);
            return true;
        }

        if (!entry.exists()) return false;

        long nowRealTime = TimeSanitizer.getCurrentRealTime();
        long nowGameTime = TimeSanitizer.getCurrentGameTime(player);

        return !isOnCooldown(entry, shop, nowRealTime, nowGameTime, nowDayTime);
    }

    /**
     * 重置抽奖次数和冷却记录。
     */
    public static void resetDrawAndCooldown(IQuestCapability cap, String shopId) {
        cap.resetGachaDrawCount(shopId);
        cap.getGachaDataStore().removeDrawCooldown(shopId);
    }

    /**
     * 判断是否应该记录冷却。
     */
    public static boolean shouldRecordCooldown(IQuestCapability cap, String shopId, GachaShopDefinition shop) {
        if (!shop.hasCooldown()) return false;

        if (shop.hasLimit()) {
            int newCount = cap.getGachaDrawCount(shopId) + 1;
            if (newCount < shop.getMaxDraws()) return false;
            if (!shop.shouldResetOnLimitReached()) return false;
        }

        return true;
    }

    /**
     * 记录冷却时间戳到 {@link GachaDataStore}。
     */
    public static void recordCooldown(ServerPlayer player, IQuestCapability cap, String shopId, GachaShopDefinition shop) {
        if (!shop.hasCooldown()) return;

        long nowRealTime = TimeSanitizer.getCurrentRealTime();
        long nowGameTime = TimeSanitizer.getCurrentGameTime(player);
        long nowDayTime  = TimeSanitizer.getCurrentDayTime(player);

        cap.getGachaDataStore().recordDrawCooldown(shopId, nowRealTime, nowGameTime, nowDayTime);
    }

    // ── 包内可见工具方法 ─────────────────────────────────

    /**
     * 基于 CooldownEntry 进行冷却判断（供 GachaSession 等同包类调用）。
     */
    static boolean isOnCooldownPublic(GachaDataStore.CooldownEntry entry, GachaShopDefinition shop,
                                       long nowRealTime, long nowGameTime, long nowDayTime) {
        return isOnCooldown(entry, shop, nowRealTime, nowGameTime, nowDayTime);
    }

    // ── 私有工具方法 ─────────────────────────────────────

    /**
     * 基于 CooldownEntry 进行冷却判断（复用 UnifiedCooldownManager 的逻辑）。
     */
    private static boolean isOnCooldown(GachaDataStore.CooldownEntry entry, GachaShopDefinition shop,
                                         long nowRealTime, long nowGameTime, long nowDayTime) {
        return switch (shop.getCooldownType()) {
            case NONE -> false;
            case SECONDS -> {
                long cooldownMs = shop.getCooldownValue() * 1000L;
                yield (nowRealTime - entry.realTime()) < cooldownMs;
            }
            case GAME_DAY -> {
                long lastGameTime = entry.gameTime();
                long lastDayTime  = entry.dayTime();
                if (lastDayTime < 0 || lastGameTime < 0) yield false;

                long gameTimeElapsed = nowGameTime - lastGameTime;
                if (gameTimeElapsed >= 24000) yield false;

                long lastDay = lastDayTime / 24000L;
                long nowDay  = nowDayTime  / 24000L;
                if (lastDay != nowDay) yield false;

                yield nowDayTime >= lastDayTime || gameTimeElapsed <= 0;
            }
            case GAME_TICK -> {
                long lastRawDayTime = entry.dayTime();
                long lastGameTime   = entry.gameTime();
                if (lastRawDayTime < 0 || lastGameTime < 0) yield false;

                long gameTimeElapsed = nowGameTime - lastGameTime;
                if (gameTimeElapsed >= 24000) yield false;
                if (nowDayTime < lastRawDayTime && gameTimeElapsed > 0) yield false;

                long recordedPeriod = Math.floorDiv(lastRawDayTime - shop.getResetTimeTicks(), 24000);
                long currentPeriod  = Math.floorDiv(nowDayTime     - shop.getResetTimeTicks(), 24000);
                yield recordedPeriod == currentPeriod;
            }
        };
    }
}
