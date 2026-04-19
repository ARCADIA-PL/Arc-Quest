package org.com.arc_quest.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.com.arc_quest.dialogue.api.CooldownType;
import org.com.arc_quest.dialogue.runtime.DialogueProgressStore;
import org.com.arc_quest.dialogue.runtime.UnifiedCooldownManager;
import org.com.arc_quest.dialogue.util.TimeSanitizer;

/**
 * 客户端冷却文本计算工具类。
 */
public final class ClientCooldownHelper {

    private ClientCooldownHelper() {
    }

    /**
     * 根据冷却原始数据实时计算格式化的冷却文本。
     * <p>
     * 此方法应在 GUI 的 render() 中每帧调用，确保冷却文本实时更新。
     *
     * @param lastPurchaseTime   最后购买时间戳（毫秒），0 表示未购买
     * @param purchaseGameTime   购买时的 gameTime
     * @param purchaseDayTime    购买时的 dayTime
     * @param cooldownType       冷却类型的 ordinal 值
     * @param cooldownValue      冷却值（秒或tick）
     * @param resetTimeTicks     重置刻（仅 GAME_TICK 有效）
     * @return 格式化的冷却文本，无冷却时返回空字符串
     */
    public static String getCooldownText(long lastPurchaseTime, long purchaseGameTime, long purchaseDayTime,
                                         int cooldownType, long cooldownValue, int resetTimeTicks) {
        if (lastPurchaseTime == 0 || cooldownType == CooldownType.NONE.ordinal()) {
            return "";
        }

        CooldownType type = CooldownType.values()[cooldownType];

        return switch (type) {
            case NONE -> "";

            case SECONDS -> {
                long nowRealTime = TimeSanitizer.getCurrentRealTime();
                long elapsed = (nowRealTime - lastPurchaseTime) / 1000;
                long remaining = Math.max(0, cooldownValue - elapsed);
                yield remaining > 0 ? remaining + "s" : "";
            }

            case GAME_DAY -> {
                if (!isOnCooldown(lastPurchaseTime, purchaseGameTime, purchaseDayTime,
                        cooldownType, cooldownValue, resetTimeTicks)) {
                    yield "";
                }
                yield Component.translatable("arc_quest.trade.cooldown.game_day").getString();
            }

            case GAME_TICK -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null) {
                    yield "";
                }

                long nowGameTime = TimeSanitizer.getCurrentGameTime(mc.level);
                long nowDayTime = TimeSanitizer.getCurrentDayTime(mc.level);

                DialogueProgressStore.Entry entry = new DialogueProgressStore.Entry(
                        lastPurchaseTime, purchaseGameTime, purchaseDayTime
                );

                int remainingTicks = UnifiedCooldownManager.getGameTickCooldownRemainingTicks(
                        entry, resetTimeTicks, nowGameTime, nowDayTime
                );

                if (remainingTicks <= 0) {
                    yield "";
                }

                if (remainingTicks < 60) {
                    yield remainingTicks + "t";
                } else if (remainingTicks < 1200) {
                    yield (remainingTicks / 20) + "s";
                } else {
                    long minutes = remainingTicks / 1200;
                    long seconds = (remainingTicks % 1200) / 20;
                    yield String.format("%dm%ds", minutes, seconds);
                }
            }
        };
    }

    /**
     * 检查是否在冷却中。
     *
     * @param lastPurchaseTime   最后购买时间戳
     * @param purchaseGameTime   购买时的 gameTime
     * @param purchaseDayTime    购买时的 dayTime
     * @param cooldownType       冷却类型 ordinal
     * @param cooldownValue      冷却值
     * @param resetTimeTicks     重置刻
     * @return true = 仍在冷却中
     */
    public static boolean isOnCooldown(long lastPurchaseTime, long purchaseGameTime, long purchaseDayTime,
                                       int cooldownType, long cooldownValue, int resetTimeTicks) {
        if (lastPurchaseTime == 0 || cooldownType == CooldownType.NONE.ordinal()) {
            return false;
        }

        CooldownType type = CooldownType.values()[cooldownType];

        return switch (type) {
            case NONE -> false;

            case SECONDS -> {
                long nowRealTime = TimeSanitizer.getCurrentRealTime();
                long elapsed = (nowRealTime - lastPurchaseTime) / 1000;
                yield elapsed < cooldownValue;
            }

            case GAME_DAY -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null) {
                    yield false;
                }

                long nowGameTime = TimeSanitizer.getCurrentGameTime(mc.level);
                long nowDayTime = TimeSanitizer.getCurrentDayTime(mc.level);

                DialogueProgressStore.Entry entry = new DialogueProgressStore.Entry(
                        lastPurchaseTime, purchaseGameTime, purchaseDayTime
                );

                yield UnifiedCooldownManager.isOnCooldown(
                        entry, type, (int) cooldownValue, 0,
                        TimeSanitizer.getCurrentRealTime(), nowGameTime, nowDayTime
                );
            }

            case GAME_TICK -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null) {
                    yield false;
                }

                long nowGameTime = TimeSanitizer.getCurrentGameTime(mc.level);
                long nowDayTime = TimeSanitizer.getCurrentDayTime(mc.level);

                DialogueProgressStore.Entry entry = new DialogueProgressStore.Entry(
                        lastPurchaseTime, purchaseGameTime, purchaseDayTime
                );

                yield UnifiedCooldownManager.isOnCooldown(
                        entry, type, (int) cooldownValue, resetTimeTicks,
                        TimeSanitizer.getCurrentRealTime(), nowGameTime, nowDayTime
                );
            }
        };
    }
}