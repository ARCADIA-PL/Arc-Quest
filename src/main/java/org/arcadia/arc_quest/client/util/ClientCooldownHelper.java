package org.arcadia.arc_quest.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.core.time.CooldownRecord;
import org.arcadia.arc_quest.core.time.CooldownRecordSnapshot;
import org.arcadia.arc_quest.core.time.CooldownStatus;
import org.arcadia.arc_quest.core.time.TimeSnapshot;
import org.arcadia.arc_quest.dialogue.api.CooldownType;

/**
 * 客户端冷却文本计算工具类。
 * <p>
 * 冷却判断统一委托 {@link org.arcadia.arc_quest.core.time.CooldownProcessor}，通过 {@link org.arcadia.arc_quest.core.time.CooldownRecord}
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
        CooldownRecord record = CooldownRecordSnapshot.recorded(
                lastPurchaseTime, purchaseGameTime, purchaseDayTime);

        return switch (type) {
            case NONE -> "";

            case SECONDS -> {
                CooldownStatus status = evaluate(record, type, cooldownValue, resetTimeTicks,
                        new TimeSnapshot(CoreProcessors.get().time().realTimeMillis(), 0L, 0L));
                int remaining = status.remainingRealSecondsCeiling();
                yield remaining > 0 ? remaining + "s" : "";
            }

            case GAME_DAY -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null) yield "";
                CooldownStatus status = evaluate(
                        record, type, cooldownValue, resetTimeTicks, CoreProcessors.get().time().capture(mc.level));
                yield status.active()
                        ? Component.translatable("arc_quest.trade.cooldown.game_day").getString() : "";
            }

            case GAME_TICK -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null) yield "";
                CooldownStatus status = evaluate(
                        record, type, cooldownValue, resetTimeTicks, CoreProcessors.get().time().capture(mc.level));
                int remainingTicks = status.remainingGameTicks();
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
        CooldownRecord record = CooldownRecordSnapshot.recorded(
                lastPurchaseTime, purchaseGameTime, purchaseDayTime);

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        return evaluate(record, type, cooldownValue, resetTimeTicks,
                CoreProcessors.get().time().capture(mc.level)).active();
    }

    // ── 私有工具 ──────────────────────────────────────────

    private static CooldownStatus evaluate(CooldownRecord record,
                                           CooldownType type,
                                           long cooldownValue,
                                           int resetTimeTicks,
                                           TimeSnapshot now) {
        return CoreProcessors.get().cooldowns().evaluate(
                record, type.toCorePolicy(cooldownValue, resetTimeTicks), now);
    }
}
