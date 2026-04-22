package org.com.arc_quest.trade.gacha.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.dialogue.runtime.DialogueProgressStore;
import org.com.arc_quest.dialogue.runtime.ProgressKey;
import org.com.arc_quest.dialogue.runtime.UnifiedCooldownManager;
import org.com.arc_quest.dialogue.util.TimeSanitizer;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.slf4j.Logger;

/**
 * 抽奖状态解析器 —— 统一管理限购、冷却、条件检查逻辑。
 * <p>
 * 对标 {@link org.com.arc_quest.trade.runtime.TradeEntryStateResolver}，但针对抽奖系统的Shop级别管理进行了优化。
 */
public final class GachaEntryStateResolver {

    private static final Logger LOGGER = LogUtils.getLogger();

    private GachaEntryStateResolver() {
        // 工具类，禁止实例化
    }

    /**
     * 综合判断是否可以抽奖（细分错误原因）。
     * <p>
     * 优先级：可见性 > 限购 > 冷却 > 条件
     *
     * @return true 如果可以抽奖
     */
    public static boolean canDraw(ServerPlayer player, IQuestCapability cap, String shopId, GachaShopDefinition shop) {
        // 1. 检查可见性
        if (!isVisible(player, cap, shop)) {
            return false;
        }

        // 2. 检查限购
        if (isMaxDrawsReached(cap, shopId, shop)) {
            return false;
        }

        // 3. 检查冷却
        if (isOnCooldown(player, cap, shopId, shop)) {
            return false;
        }

        // 4. 检查前置条件
        if (!hasConditionMet(player, cap, shop)) {
            return false;
        }

        return true;
    }

    /**
     * 检查奖池是否对玩家可见。
     */
    public static boolean isVisible(ServerPlayer player, IQuestCapability cap, GachaShopDefinition shop) {
        var visibleCondition = shop.getVisibleCondition();
        if (visibleCondition == null) {
            return true; // 无条件则默认可见
        }

        try {
            return visibleCondition.test(player,
                cap.getCompletedQuestLocations(),
                cap.getAllFlags(),
                cap.getAllVariables());
        } catch (Exception e) {
            LOGGER.warn("[Gacha] Error evaluating visible condition for shop={}: {}",
                    shop.getShopId(), e.getMessage());
            return false;
        }
    }

    /**
     * 检查是否达到抽奖次数上限。
     */
    public static boolean isMaxDrawsReached(IQuestCapability cap, String shopId, GachaShopDefinition shop) {
        if (!shop.hasLimit()) {
            return false; // 无限制
        }

        int currentCount = cap.getGachaDrawCount(shopId);
        return currentCount >= shop.getMaxDraws();
    }

    /**
     * 检查是否在冷却中。
     * <p>
     * 对标 TradeEntryStateResolver.isOnCooldown()：
     * 只有达到限购后才检查冷却，未达到限购时直接返回false。
     */
    public static boolean isOnCooldown(ServerPlayer player, IQuestCapability cap, String shopId, GachaShopDefinition shop) {
        if (!shop.hasCooldown()) {
            return false; // 无冷却
        }

        // 【修复】对标商店系统：未达到限购时不检查冷却
        if (shop.hasLimit()) {
            int currentCount = cap.getGachaDrawCount(shopId);
            if (currentCount < shop.getMaxDraws()) {
                return false; // 未达到限购，不检查冷却
            }
        }

        ProgressKey key = ProgressKey.ofTrade(shopId, "draw");
        DialogueProgressStore.Entry storeEntry = cap.getDialogueProgress().getChoiceSelection(key);

        if (!storeEntry.exists()) {
            return false; // 从未抽过奖
        }

        long nowRealTime = TimeSanitizer.getCurrentRealTime();
        long nowGameTime = TimeSanitizer.getCurrentGameTime(player);
        long nowDayTime = TimeSanitizer.getCurrentDayTime(player);

        return UnifiedCooldownManager.isOnCooldown(
                storeEntry,
                shop.getCooldownType(),
                (int) shop.getCooldownValue(),
                shop.getResetTimeTicks(),
                nowRealTime,
                nowGameTime,
                nowDayTime
        );
    }

    /**
     * 检查前置条件是否满足。
     */
    public static boolean hasConditionMet(ServerPlayer player, IQuestCapability cap, GachaShopDefinition shop) {
        var drawCondition = shop.getDrawCondition();
        if (drawCondition == null) {
            return true; // 无条件则默认满足
        }

        try {
            return drawCondition.test(player,
                cap.getCompletedQuestLocations(),
                cap.getAllFlags(),
                cap.getAllVariables());
        } catch (Exception e) {
            LOGGER.warn("[Gacha] Error evaluating draw condition for shop={}: {}",
                    shop.getShopId(), e.getMessage());
            return false;
        }
    }

    /**
     * 判断是否应该因为冷却过期而重置抽奖次数。
     */
    public static boolean shouldResetByCooldown(ServerPlayer player, IQuestCapability cap,
                                                 String shopId, GachaShopDefinition shop) {
        if (!shop.hasCooldown() || !shop.hasLimit()) {
            return false;
        }

        ProgressKey key = ProgressKey.ofTrade(shopId, "draw");
        DialogueProgressStore.Entry storeEntry = cap.getDialogueProgress().getChoiceSelection(key);

        if (!storeEntry.exists()) {
            return false;
        }

        long nowRealTime = TimeSanitizer.getCurrentRealTime();
        long nowGameTime = TimeSanitizer.getCurrentGameTime(player);
        long nowDayTime = TimeSanitizer.getCurrentDayTime(player);

        // 如果不在冷却中，说明已过期，应该重置
        return !UnifiedCooldownManager.isOnCooldown(
                storeEntry,
                shop.getCooldownType(),
                (int) shop.getCooldownValue(),
                shop.getResetTimeTicks(),
                nowRealTime,
                nowGameTime,
                nowDayTime
        );
    }

    /**
     * 重置抽奖次数和冷却记录。
     */
    public static void resetDrawAndCooldown(IQuestCapability cap, String shopId) {
        cap.resetGachaDrawCount(shopId);

        ProgressKey key = ProgressKey.ofTrade(shopId, "draw");
        // 通过将时间戳设为 0 来清除冷却记录（表示从未抽过奖）
        cap.getDialogueProgress().recordChoiceSelection(key, 0, 0, 0);
    }

    /**
     * 判断是否应该记录冷却。
     */
    public static boolean shouldRecordCooldown(IQuestCapability cap, String shopId, GachaShopDefinition shop) {
        return shop.hasCooldown();
    }

    /**
     * 记录冷却时间戳。
     */
    public static void recordCooldown(ServerPlayer player, IQuestCapability cap, String shopId, GachaShopDefinition shop) {
        if (!shop.hasCooldown()) {
            return;
        }

        ProgressKey key = ProgressKey.ofTrade(shopId, "draw");

        long nowRealTime = TimeSanitizer.getCurrentRealTime();
        long nowGameTime = TimeSanitizer.getCurrentGameTime(player);
        long nowDayTime = TimeSanitizer.getCurrentDayTime(player);

        cap.getDialogueProgress().recordChoiceSelection(
                key,
                nowRealTime,
                nowGameTime,
                nowDayTime
        );
    }
}
