package org.com.arc_quest.trade.gacha.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.dialogue.api.CooldownType;
import org.com.arc_quest.dialogue.runtime.ProgressKey;
import org.com.arc_quest.dialogue.runtime.UnifiedCooldownManager;
import org.com.arc_quest.dialogue.util.TimeSanitizer;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.slf4j.Logger;

/**
 * 抽奖运行时会话 —— 管理玩家的抽奖状态、抽奖次数和冷却。
 * <p>
 * 对标 {@link org.com.arc_quest.trade.runtime.TradeSession}，但针对抽奖系统的特殊性进行了优化。
 * <p>
 * 持久化数据存储在玩家 Capability 的 NBT 中（{@code "GachaData"} 子标签）。
 */
public final class GachaSession {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final ServerPlayer player;
    private final GachaShopDefinition shop;
    private final IQuestCapability capability;

    public GachaSession(ServerPlayer player, GachaShopDefinition shop, IQuestCapability capability) {
        this.player = player;
        this.shop = shop;
        this.capability = capability;
    }

    /**
     * 检查当前是否可以抽奖（综合判断）。
     * <p>
     * 遵循优先级：可见性 > 限购 > 冷却 > 条件
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
        if (GachaEntryStateResolver.isMaxDrawsReached(capability, shop.getShopId(), shop)) {
            return DrawFailReason.MAX_DRAWS_REACHED;
        }
        if (GachaEntryStateResolver.isOnCooldown(player, capability, shop.getShopId(), shop)) {
            return DrawFailReason.ON_COOLDOWN;
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
        
        LOGGER.info("[Gacha] Player {} draw count incremented for shop '{}'",
                player.getName().getString(), shop.getShopId());
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
     */
    public int getCooldownRemaining() {
        if (!shop.hasCooldown()) return 0;

        // 从 DialogueProgressStore 获取最后抽奖时间
        ProgressKey key = ProgressKey.ofTrade(shop.getShopId(), "draw");
        var storeEntry = capability.getDialogueProgress().getChoiceSelection(key);
        
        if (!storeEntry.exists() || storeEntry.realTime() == 0) return 0;
        long lastDrawTime = storeEntry.realTime();

        return switch (shop.getCooldownType()) {
            case NONE -> 0;
            case SECONDS -> {
                long nowRealTime = TimeSanitizer.getCurrentRealTime();
                long elapsed = (nowRealTime - lastDrawTime) / 1000;
                yield Math.max(0, (int) (shop.getCooldownValue() - elapsed));
            }
            case GAME_DAY -> {
                long nowGameTime = TimeSanitizer.getCurrentGameTime(player);
                long nowDayTime = TimeSanitizer.getCurrentDayTime(player);

                if (!storeEntry.exists() || storeEntry.dayTime() < 0) {
                    yield 0;
                }

                boolean onCooldown = UnifiedCooldownManager.isOnCooldown(
                        storeEntry, shop.getCooldownType(), (int) shop.getCooldownValue(),
                        shop.getResetTimeTicks(),
                        TimeSanitizer.getCurrentRealTime(), nowGameTime, nowDayTime
                );

                if (!onCooldown) {
                    yield 0;
                }

                long currentDayTick = nowDayTime % 24000;
                int remainingTicks = (int) (24000 - currentDayTick);
                yield Math.max(1, remainingTicks / 20);
            }
            case GAME_TICK -> {
                long nowGameTime = TimeSanitizer.getCurrentGameTime(player);
                long nowDayTime = TimeSanitizer.getCurrentDayTime(player);

                int remainingTicks = UnifiedCooldownManager
                        .getGameTickCooldownRemainingTicks(
                                storeEntry, shop.getResetTimeTicks(),
                                nowGameTime, nowDayTime
                        );
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
        var resetCondition = shop.getResetCondition();
        if (resetCondition == null) return;

        int currentCount = getDrawCount();
        if (currentCount == 0) return;

        // 1. 检查冷却自动恢复
        boolean shouldReset = GachaEntryStateResolver.shouldResetByCooldown(
                player, capability, shop.getShopId(), shop);

        // 2. 检查自定义条件恢复
        if (!shouldReset) {
            try {
                shouldReset = resetCondition.test(player, 
                    capability.getCompletedQuestLocations(), 
                    capability.getAllFlags(), 
                    capability.getAllVariables());
                if (shouldReset) {
                    LOGGER.info("[Gacha] Draw limit reset by custom condition for shop={}", shop.getShopId());
                }
            } catch (Exception e) {
                LOGGER.warn("[Gacha] Error evaluating draw reset condition for shop={}: {}",
                        shop.getShopId(), e.getMessage());
            }
        }

        // 3. 执行重置
        if (shouldReset && currentCount > 0) {
            GachaEntryStateResolver.resetDrawAndCooldown(capability, shop.getShopId());
            LOGGER.info("[Gacha] Draw count reset for player {} in shop {}",
                    player.getName().getString(), shop.getShopId());
        }
    }

    /**
     * 检查并重置过期的冷却。
     */
    private void checkAndResetCooldown() {
        // 冷却重置逻辑已整合到 checkAndResetDrawCount 中
        // 这里保留方法以便未来扩展
    }

    /**
     * 抽奖失败原因枚举。
     */
    public enum DrawFailReason {
        /**
         * 可以抽奖（无失败）
         */
        NONE,
        /**
         * 不可见
         */
        NOT_VISIBLE,
        /**
         * 已达抽奖次数上限
         */
        MAX_DRAWS_REACHED,
        /**
         * 冷却中
         */
        ON_COOLDOWN,
        /**
         * 条件不满足
         */
        CONDITION_NOT_MET
    }
}
