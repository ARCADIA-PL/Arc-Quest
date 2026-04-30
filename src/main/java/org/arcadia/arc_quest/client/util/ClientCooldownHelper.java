package org.arcadia.arc_quest.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.dialogue.api.CooldownType;
import org.arcadia.arc_quest.dialogue.runtime.ICooldownRecord;
import org.arcadia.arc_quest.dialogue.runtime.UnifiedCooldownManager;
import org.arcadia.arc_quest.dialogue.util.TimeSanitizer;

/**
 * 客户端冷却文本计算工具类。
 * <p>
 * 冷却判断统一委托 {@link UnifiedCooldownManager}，通过 {@link ICooldownRecord}
 * 接口传入三时钟快照，不再构造临时 {@code DialogueProgressStore.Entry}。
 */
public final class ClientCooldownHelper {

    private ClientCooldownHelper() {
    }

    /**
     * 根据冷却原始数据实时计算格式化的冷却文本。
     * <p>
     * 此方法应在 GUI 的 render() 中每帧调用，确保冷却文本实时更新。
     *
     * @param lastPurchaseTime 最后购买时间戳（毫秒），0 表示未购买
     * @param purchaseGameTime 购买时的 gameTime
     * @param purchaseDayTime  购买时的 dayTime
     * @param cooldownType     冷却类型的 ordinal 值
     * @param cooldownValue    冷却值（秒或tick）
     * @param resetTimeTicks   重置刻（仅 GAME_TICK 有效）
     * @return 格式化的冷却文本，无冷却时返回空字符串
     */
    public static String getCooldownText(long lastPurchaseTime, long purchaseGameTime, long purchaseDayTime,
                                         int cooldownType, long cooldownValue, int resetTimeTicks) {
        if (lastPurchaseTime == 0 || cooldownType == CooldownType.NONE.ordinal()) {
            return "";
        }

        CooldownType type = CooldownType.values()[cooldownType];
        ICooldownRecord record = makeRecord(lastPurchaseTime, purchaseGameTime, purchaseDayTime);

        return switch (type) {
            case NONE -> "";

            case SECONDS -> {
                long nowRealTime = TimeSanitizer.getCurrentRealTime();
                long elapsed = (nowRealTime - lastPurchaseTime) / 1000;
                long remaining = Math.max(0, cooldownValue - elapsed);
                yield remaining > 0 ? remaining + "s" : "";
            }

            case GAME_DAY -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null) yield "";
                long nowGameTime = TimeSanitizer.getCurrentGameTime(mc.level);
                long nowDayTime = TimeSanitizer.getCurrentDayTime(mc.level);
                boolean onCooldown = UnifiedCooldownManager.isOnCooldown(
                        record, type, (int) cooldownValue, resetTimeTicks,
                        TimeSanitizer.getCurrentRealTime(), nowGameTime, nowDayTime);
                yield onCooldown ? Component.translatable("arc_quest.trade.cooldown.game_day").getString() : "";
            }

            case GAME_TICK -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null) yield "";
                long nowGameTime = TimeSanitizer.getCurrentGameTime(mc.level);
                long nowDayTime = TimeSanitizer.getCurrentDayTime(mc.level);
                int remainingTicks = UnifiedCooldownManager.getGameTickCooldownRemainingTicks(
                        record, resetTimeTicks, nowGameTime, nowDayTime);
                if (remainingTicks <= 0) yield "";
                if (remainingTicks < 60) yield remainingTicks + "t";
                if (remainingTicks < 1200) yield (remainingTicks / 20) + "s";
                long minutes = remainingTicks / 1200;
                long seconds = (remainingTicks % 1200) / 20;
                yield String.format("%dm%ds", minutes, seconds);
            }
        };
    }

    /**
     * 检查是否在冷却中。
     *
     * @param lastPurchaseTime 最后购买时间戳
     * @param purchaseGameTime 购买时的 gameTime
     * @param purchaseDayTime  购买时的 dayTime
     * @param cooldownType     冷却类型 ordinal
     * @param cooldownValue    冷却值
     * @param resetTimeTicks   重置刻
     * @return true = 仍在冷却中
     */
    public static boolean isOnCooldown(long lastPurchaseTime, long purchaseGameTime, long purchaseDayTime,
                                       int cooldownType, long cooldownValue, int resetTimeTicks) {
        if (lastPurchaseTime == 0 || cooldownType == CooldownType.NONE.ordinal()) {
            return false;
        }

        CooldownType type = CooldownType.values()[cooldownType];
        ICooldownRecord record = makeRecord(lastPurchaseTime, purchaseGameTime, purchaseDayTime);

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        long nowRealTime = TimeSanitizer.getCurrentRealTime();
        long nowGameTime = TimeSanitizer.getCurrentGameTime(mc.level);
        long nowDayTime = TimeSanitizer.getCurrentDayTime(mc.level);

        return UnifiedCooldownManager.isOnCooldown(record, type, (int) cooldownValue, resetTimeTicks,
                nowRealTime, nowGameTime, nowDayTime);
    }

    // ── 私有工具 ──────────────────────────────────────────

    private static ICooldownRecord makeRecord(long realTime, long gameTime, long dayTime) {
        return new ICooldownRecord() {
            @Override
            public long realTime() {
                return realTime;
            }

            @Override
            public long gameTime() {
                return gameTime;
            }

            @Override
            public long dayTime() {
                return dayTime;
            }

            @Override
            public boolean exists() {
                return realTime > 0;
            }
        };
    }
}
