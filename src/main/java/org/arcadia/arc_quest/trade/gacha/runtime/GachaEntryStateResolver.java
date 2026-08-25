package org.arcadia.arc_quest.trade.gacha.runtime;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.core.execution.CoreDecision;
import org.arcadia.arc_quest.core.execution.CoreRule;
import org.arcadia.arc_quest.core.time.CooldownRecord;
import org.arcadia.arc_quest.quest.api.QuestConditionContext;
import org.arcadia.arc_quest.quest.data.GachaDataStore;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;

import java.util.List;

/**
 * 抽奖状态解析器 —— 统一管理限购、冷却、条件检查逻辑。
 * <p>
 * 对标 {@link org.arcadia.arc_quest.trade.runtime.TradeEntryStateResolver}，
 * 但针对抽奖系统的 Shop 级别管理进行了优化。
 * <p>
 * <b>冷却语义规则：</b>有限购时，冷却是限购的附属机制——限购未满时不检查冷却；
 * 无限购时，每次抽奖后均触发冷却计时。
 * <p>
 * 冷却判断统一委托 {@link org.arcadia.arc_quest.core.time.CooldownProcessor}，通过 {@link org.arcadia.arc_quest.core.time.CooldownRecord}
 * 接口接收 {@link GachaDataStore.CooldownEntry}，不再维护独立的冷却判断逻辑。
 */
public final class GachaEntryStateResolver {
    private GachaEntryStateResolver() {
    }

    /**
     * 综合判断是否可以抽奖。
     * <p>
     * 优先级：可见性 > 冷却 > 限购 > 条件
     */
    public static boolean canDraw(ServerPlayer player, ArcQuestPlayer data, String shopId, GachaShopDefinition shop) {
        return evaluateDraw(player, data, shopId, shop).allowed();
    }

    public static CoreDecision<DrawFailure> evaluateDraw(
            ServerPlayer player, ArcQuestPlayer data, String shopId, GachaShopDefinition shop) {
        DecisionContext context = DecisionContext.create(player, data, shopId, shop);
        return CoreProcessors.get().executions().decide(context, List.of(
                CoreRule.require(GachaEntryStateResolver::isVisible,
                        DrawFailure.NOT_VISIBLE),
                CoreRule.require(candidate -> !isOnCooldown(candidate.player(), candidate.data(),
                        candidate.shopId(), candidate.shop()), DrawFailure.ON_COOLDOWN),
                CoreRule.require(candidate -> !isMaxDrawsReached(candidate.data(), candidate.shopId(),
                        candidate.shop()), DrawFailure.MAX_DRAWS_REACHED),
                CoreRule.require(GachaEntryStateResolver::hasConditionMet,
                        DrawFailure.CONDITION_NOT_MET)
        ));
    }

    public enum DrawFailure {
        NOT_VISIBLE,
        ON_COOLDOWN,
        MAX_DRAWS_REACHED,
        CONDITION_NOT_MET
    }

    /**
     * 检查奖池是否对玩家可见。
     */
    public static boolean isVisible(ServerPlayer player, ArcQuestPlayer data, GachaShopDefinition shop) {
        return isVisible(DecisionContext.create(player, data, shop.getShopId(), shop));
    }

    private static boolean isVisible(DecisionContext context) {
        var visibleCondition = context.shop().getVisibleCondition();
        if (visibleCondition == null) return true;

        return CoreProcessors.get().conditions().evaluateSafely(visibleCondition,
                context.conditionContext(), false, null,
                "gacha visibility shop=" + context.shop().getShopId());
    }

    /**
     * 检查是否达到抽奖次数上限。
     */
    public static boolean isMaxDrawsReached(ArcQuestPlayer data, String shopId, GachaShopDefinition shop) {
        if (!shop.hasLimit()) return false;
        return data.getGachaDrawCount(shopId) >= shop.getMaxDraws();
    }

    /**
     * 检查是否在冷却中。
     * <p>
     * 冷却时间戳从 {@link GachaDataStore} 读取，通过 {@link org.arcadia.arc_quest.core.time.CooldownProcessor}
     * 统一计算，不再维护独立的冷却 switch 逻辑。
     */
    public static boolean isOnCooldown(ServerPlayer player, ArcQuestPlayer data, String shopId, GachaShopDefinition shop) {
        if (!shop.hasCooldown()) return false;

        if (shop.hasLimit() && data.getGachaDrawCount(shopId) < shop.getMaxDraws()) {
            return false;
        }

        CooldownRecord record = data.getGachaDataStore().getDrawCooldown(shopId);
        if (!record.exists()) return false;

        var now = CoreProcessors.get().time().capture(player);

        return CoreProcessors.get().cooldowns().isOnCooldown(
                record, shop.getCooldownType().toCorePolicy(
                        shop.getCooldownValue(), shop.getResetTimeTicks()),
                now);
    }

    /**
     * 检查前置条件是否满足。
     */
    public static boolean hasConditionMet(ServerPlayer player, ArcQuestPlayer data, GachaShopDefinition shop) {
        return hasConditionMet(DecisionContext.create(player, data, shop.getShopId(), shop));
    }

    private static boolean hasConditionMet(DecisionContext context) {
        var drawCondition = context.shop().getDrawCondition();
        if (drawCondition == null) return true;

        return CoreProcessors.get().conditions().evaluateSafely(drawCondition,
                context.conditionContext(), false, null,
                "gacha draw shop=" + context.shop().getShopId());
    }

    private record DecisionContext(ServerPlayer player, ArcQuestPlayer data, String shopId,
                                   GachaShopDefinition shop, QuestConditionContext conditionContext) {
        private static DecisionContext create(ServerPlayer player, ArcQuestPlayer data,
                                              String shopId, GachaShopDefinition shop) {
            return new DecisionContext(player, data, shopId, shop,
                    new QuestConditionContext(player, data.getCompletedQuestLocations(),
                            data.getAllFlags(), data.getAllVariables()));
        }
    }

    /**
     * 判断是否应该因为冷却过期而重置抽奖次数。
     */
    public static boolean shouldResetByCooldown(ServerPlayer player, ArcQuestPlayer data,
                                                String shopId, GachaShopDefinition shop) {
        if (!shop.hasCooldown()) return false;

        if (shop.hasLimit()) {
            if (data.getGachaDrawCount(shopId) < shop.getMaxDraws()) return false;
            if (!shop.shouldResetOnLimitReached()) return false;
        }

        GachaDataStore gachaStore = data.getGachaDataStore();
        var now = CoreProcessors.get().time().capture(player);

        CooldownRecord record = gachaStore.getDrawCooldown(shopId);
        if (record.exists() && record.dayTime() > now.dayTime()) {
            gachaStore.removeDrawCooldown(shopId);
            ArcQuestLog.info(ArcQuestLog.Category.GACHA, "Cleared cooldown record due to time regression: shop={}", shopId);
            return true;
        }

        if (!record.exists()) return false;

        return !CoreProcessors.get().cooldowns().isOnCooldown(
                record, shop.getCooldownType().toCorePolicy(
                        shop.getCooldownValue(), shop.getResetTimeTicks()),
                now);
    }

    /**
     * 重置抽奖次数和冷却记录。
     */
    public static void resetDrawAndCooldown(ArcQuestPlayer data, String shopId) {
        data.resetGachaDrawCount(shopId);
        data.getGachaDataStore().removeDrawCooldown(shopId);
    }

    /**
     * 判断是否应该记录冷却。
     */
    public static boolean shouldRecordCooldown(ArcQuestPlayer data, String shopId, GachaShopDefinition shop) {
        if (!shop.hasCooldown()) return false;

        if (shop.hasLimit()) {
            int newCount = data.getGachaDrawCount(shopId) + 1;
            if (newCount < shop.getMaxDraws()) return false;
            if (!shop.shouldResetOnLimitReached()) return false;
        }

        return true;
    }

    /**
     * 记录冷却时间戳到 {@link GachaDataStore}。
     */
    public static void recordCooldown(ServerPlayer player, ArcQuestPlayer data, String shopId, GachaShopDefinition shop) {
        if (!shop.hasCooldown()) return;

        var now = CoreProcessors.get().time().capture(player);
        data.getGachaDataStore().recordDrawCooldown(
                shopId, now.realTime(), now.gameTime(), now.dayTime());
    }
}
