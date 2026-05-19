package org.arcadia.arc_quest.trade.gacha.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import org.arcadia.arc_quest.api.event.gacha.GachaEvents;
import org.arcadia.arc_quest.dialogue.api.CooldownType;
import org.arcadia.arc_quest.dialogue.runtime.UnifiedCooldownManager;
import org.arcadia.arc_quest.dialogue.util.TimeSanitizer;
import org.arcadia.arc_quest.quest.capability.GachaDataStore;
import org.arcadia.arc_quest.quest.player.ArcQuestPlayer;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.slf4j.Logger;

/**
 * 抽奖运行时会话 —— 管理玩家的抽奖状态、抽奖次数和冷却。
 * <p>
 * 对标 {@link org.arcadia.arc_quest.trade.runtime.TradeSession}，但针对抽奖系统的特殊性进行了优化。
 * <p>
 * 持久化数据存储在玩家 Capability 的 NBT 中（{@code "GachaData"} 子标签）。
 */
public final class GachaSession {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final ServerPlayer player;
    private final GachaShopDefinition shop;
    private final ArcQuestPlayer playerData;

    public GachaSession(ServerPlayer player, GachaShopDefinition shop, ArcQuestPlayer playerData) {
        this.player = player;
        this.shop = shop;
        this.capability = capability;
    }

    /**
     * 检查当前是否可以抽奖（综合判断）。
     * <p>
     * 遵循优先级：可见性 > 冷却 > 限购 > 条件
     *
     * @return true 如果可以抽奖
     */
    public boolean canDraw() {
        return GachaEntryStateResolver.canDraw(player, capability, shop.getShopId(), shop);
    }

    /**
     * 获取失败原因（用于细分错误提示）。
     *
     * @return 失败原因枚举
     */
    public DrawFailReason getFailReason() {
        if (!GachaEntryStateResolver.isVisible(player, capability, shop)) {
            return DrawFailReason.NOT_VISIBLE;
        }
        if (GachaEntryStateResolver.isOnCooldown(player, capability, shop.getShopId(), shop)) {
            return DrawFailReason.ON_COOLDOWN;
        }
        if (GachaEntryStateResolver.isMaxDrawsReached(capability, shop.getShopId(), shop)) {
            return DrawFailReason.MAX_DRAWS_REACHED;
        }
        if (!GachaEntryStateResolver.hasConditionMet(player, capability, shop)) {
            return DrawFailReason.CONDITION_NOT_MET;
        }
        return DrawFailReason.NONE;
    }

    /**
     * 检查并重置过期的抽奖次数和冷却。
     * <p>
     * 必须在 {@link #canDraw()} 之前调用，确保状态是最新的。
     */
    public void checkAndResetDraws() {
        if (shop.hasLimit()) {
            checkAndResetDrawCount();
        }
        if (shop.hasCooldown()) {
            checkAndResetCooldown();
        }
    }

    /**
     * 增加抽奖次数计数。
     * <p>
     * 在成功抽奖后调用。
     */
    public void incrementDrawCount() {
        capability.incrementGachaDrawCount(shop.getShopId());
    }

    /**
     * 获取当前抽奖次数。
     */
    public int getDrawCount() {
        return capability.getGachaDrawCount(shop.getShopId());
    }

    /**
     * 获取剩余可抽奖次数。
     *
     * @return -1 表示无限制
     */
    public int getRemainingDraws() {
        if (!shop.hasLimit()) return -1;
        return Math.max(0, shop.getMaxDraws() - getDrawCount());
    }

    /**
     * 获取冷却剩余秒数（用于客户端显示）。
     * <p>
     * 冷却时间戳从 {@link GachaDataStore} 读取，通过 {@link UnifiedCooldownManager}
     * 统一计算，不再维护独立的冷却判断逻辑。
     */
    public int getCooldownRemaining() {
        if (!shop.hasCooldown()) return 0;

        GachaDataStore.CooldownEntry entry = capability.getGachaDataStore().getDrawCooldown(shop.getShopId());
        if (!entry.exists()) return 0;

        long nowRealTime = TimeSanitizer.getCurrentRealTime();
        long nowGameTime = TimeSanitizer.getCurrentGameTime(player);
        long nowDayTime = TimeSanitizer.getCurrentDayTime(player);

        return switch (shop.getCooldownType()) {
            case NONE -> 0;
            case SECONDS -> {
                long elapsed = (nowRealTime - entry.realTime()) / 1000;
                yield Math.max(0, (int) (shop.getCooldownValue() - elapsed));
            }
            case GAME_DAY -> {
                if (entry.dayTime() < 0) yield 0;
                boolean onCooldown = UnifiedCooldownManager.isOnCooldown(
                        entry, shop.getCooldownType(), (int) shop.getCooldownValue(),
                        shop.getResetTimeTicks(), nowRealTime, nowGameTime, nowDayTime);
                if (!onCooldown) yield 0;
                long currentDayTick = nowDayTime % 24000;
                yield Math.max(1, (int) (24000 - currentDayTick) / 20);
            }
            case GAME_TICK -> {
                int remainingTicks = UnifiedCooldownManager.getGameTickCooldownRemainingTicks(
                        entry, shop.getResetTimeTicks(), nowGameTime, nowDayTime);
                yield Math.max(0, remainingTicks / 20);
            }
        };
    }

    /**
     * 获取格式化的冷却文本。
     */
    public String getCooldownText() {
        if (!shop.hasCooldown()) return "";

        int remaining = getCooldownRemaining();
        if (remaining <= 0) return "";

        if (shop.getCooldownType() == CooldownType.GAME_DAY) {
            return Component.translatable("arc_quest.trade.cooldown.game_day").getString();
        }

        int minutes = remaining / 60;
        int seconds = remaining % 60;
        if (minutes > 0) {
            return String.format("%d:%02d", minutes, seconds);
        }
        return String.format("%ds", seconds);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 检查并重置过期的抽奖次数。
     */
    private void checkAndResetDrawCount() {
        int currentCount = getDrawCount();
        if (currentCount == 0) return;

        // 1. 检查冷却自动恢复
        boolean shouldReset = GachaEntryStateResolver.shouldResetByCooldown(
                player, capability, shop.getShopId(), shop);

        // 2. 检查自定义条件恢复
        if (!shouldReset) {
            var resetCondition = shop.getResetCondition();
            if (resetCondition != null) {
                try {
                    shouldReset = resetCondition.test(player,
                            capability.getCompletedQuestLocations(),
                            capability.getAllFlags(),
                            capability.getAllVariables());
                } catch (Exception e) {
                    LOGGER.warn("[Gacha] Error evaluating draw reset condition for shop={}: {}",
                            shop.getShopId(), e.getMessage());
                }
            }
        }

        // 3. 执行重置
        if (shouldReset && currentCount > 0) {
            boolean isCooldownExpired = GachaEntryStateResolver.shouldResetByCooldown(
                    player, capability, shop.getShopId(), shop);
            String resetReason = isCooldownExpired ? "COOLDOWN_EXPIRED" : "CUSTOM_CONDITION";
            LOGGER.info("[Gacha-Reset] Triggering draw reset: shop={}, currentCount={}, reason={}",
                    shop.getShopId(), currentCount, resetReason);

            GachaEvents.DrawLimitResetEvent.ResetReason reason = isCooldownExpired
                    ? GachaEvents.DrawLimitResetEvent.ResetReason.COOLDOWN_EXPIRED
                    : GachaEvents.DrawLimitResetEvent.ResetReason.CUSTOM_CONDITION;

            var resetEvent = new GachaEvents.DrawLimitResetEvent(
                    player, shop.getShopId(), capability, reason, currentCount);
            MinecraftForge.EVENT_BUS.post(resetEvent);

            int pityBefore = capability.getGachaPityCounter(shop.getShopId());
            GachaEntryStateResolver.resetDrawAndCooldown(capability, shop.getShopId());

            LOGGER.info("[Gacha-Reset] Draw reset completed: shop={}, pityCounter before={}",
                    shop.getShopId(), pityBefore);
        }
    }

    /**
     * 检查并重置过期的冷却（保留方法以便未来扩展）。
     */
    private void checkAndResetCooldown() {
    }

    /**
     * 抽奖失败原因枚举。
     */
    public enum DrawFailReason {
        NONE,
        NOT_VISIBLE,
        MAX_DRAWS_REACHED,
        ON_COOLDOWN,
        CONDITION_NOT_MET,
        CANNOT_AFFORD
    }
}
