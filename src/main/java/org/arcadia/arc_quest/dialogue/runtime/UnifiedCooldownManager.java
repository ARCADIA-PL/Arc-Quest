package org.arcadia.arc_quest.dialogue.runtime;

import com.mojang.logging.LogUtils;
import org.arcadia.arc_quest.dialogue.api.CooldownType;
import org.arcadia.arc_quest.dialogue.runtime.DialogueProgressStore.Entry;
import org.arcadia.arc_quest.dialogue.runtime.DialogueProgressStore.TimeSnapshot;
import org.slf4j.Logger;

/**
 * 统一冷却管理器（Unified Cooldown Manager）。
 * <p>
 * 负责整个项目中所有冷却时间的计算与校验逻辑，包括：
 * <ul>
 *   <li>对话系统：节点访问、选项选择、对话树访问</li>
 *   <li>交易系统：商品购买冷却</li>
 *   <li>抽奖系统：抽奖次数冷却</li>
 * </ul>
 * <p>
 * 所有接受冷却记录的方法均通过 {@link ICooldownRecord} 接口统一入参，
 * {@link Entry} 和 {@link org.arcadia.arc_quest.quest.capability.GachaDataStore.CooldownEntry}
 * 等具体 record 均实现该接口。
 */
public final class UnifiedCooldownManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private UnifiedCooldownManager() {
    }

    // ── Store 版本入口（对话系统专用）────────────────────

    /**
     * 统一冷却查询（核心入口）。
     * 单 Map 合并后简化：无论是节点、对话、选项，直接按 key 从 store 中查找。
     */
    public static boolean isOnCooldown(DialogueProgressStore store, ProgressKey key, CooldownType cooldownType,
                                       int cooldownValue, int resetTick, TimeSnapshot ts) {
        ICooldownRecord record = store.getEntry(key);
        return isOnCooldown(record, cooldownType, cooldownValue, resetTick,
                ts.realTime(), ts.gameTime(), ts.dayTime());
    }

    // ── ICooldownRecord 版本（通用，所有系统共用）────────

    /**
     * 通用冷却检测底层逻辑（接受任意 {@link ICooldownRecord} 实现）。
     * <p>
     * {@link Entry}、{@link org.arcadia.arc_quest.quest.capability.GachaDataStore.CooldownEntry}、
     * {@link org.arcadia.arc_quest.quest.capability.TradeDataStore.TradeCooldownEntry} 均可传入。
     */
    public static boolean isOnCooldown(ICooldownRecord record, CooldownType cooldownType,
                                       int cooldownValue, int resetTick,
                                       long nowRealTime, long nowGameTime, long nowDayTime) {
        if (!record.exists() || cooldownType == CooldownType.NONE) {
            return false;
        }

        return switch (cooldownType) {
            case NONE -> false;

            case SECONDS -> {
                long cooldownMs = cooldownValue * 1000L;
                yield (nowRealTime - record.realTime()) < cooldownMs;
            }

            case GAME_DAY -> {
                long lastGameTime = record.gameTime();
                long lastDayTime = record.dayTime();
                if (lastDayTime < 0 || lastGameTime < 0) yield false;

                long gameTimeElapsed = nowGameTime - lastGameTime;
                if (gameTimeElapsed >= 24000) yield false;

                long lastDay = lastDayTime / 24000L;
                long nowDay = nowDayTime / 24000L;
                if (lastDay != nowDay) yield false;

                yield nowDayTime >= lastDayTime || gameTimeElapsed <= 0;
            }

            case GAME_TICK -> {
                long lastRawDayTime = record.dayTime();
                long lastGameTime = record.gameTime();
                if (lastRawDayTime < 0 || lastGameTime < 0) yield false;

                long gameTimeElapsed = nowGameTime - lastGameTime;
                if (gameTimeElapsed >= 24000) yield false;
                if (nowDayTime < lastRawDayTime && gameTimeElapsed > 0) yield false;

                long recordedPeriod = Math.floorDiv(lastRawDayTime - resetTick, 24000);
                long currentPeriod = Math.floorDiv(nowDayTime - resetTick, 24000);
                yield recordedPeriod == currentPeriod;
            }
        };
    }

    // ── 剩余时间计算 ──────────────────────────────────────

    /**
     * 计算 GAME_TICK 冷却剩余时间（tick 数，用于客户端显示）。
     * 接受任意 {@link ICooldownRecord} 实现。
     */
    public static int getGameTickCooldownRemainingTicks(ICooldownRecord record, int resetTick,
                                                        long nowGameTime, long nowDayTime) {
        if (!record.exists() || record.dayTime() < 0 || record.gameTime() < 0) return 0;

        long gameTimeElapsed = nowGameTime - record.gameTime();
        if (gameTimeElapsed >= 24000) return 0;
        if (nowDayTime < record.dayTime() && gameTimeElapsed > 0) return 0;

        long recordedPeriod = Math.floorDiv(record.dayTime() - resetTick, 24000);
        long currentPeriod = Math.floorDiv(nowDayTime - resetTick, 24000);
        if (recordedPeriod != currentPeriod) return 0;

        long currentDayTick = ((nowDayTime % 24000) + 24000) % 24000;
        long resetTickNorm = ((long) resetTick % 24000 + 24000) % 24000;

        return (int) (currentDayTick >= resetTickNorm
                ? 24000 - currentDayTick + resetTickNorm
                : resetTickNorm - currentDayTick);
    }

    // ── 时间回退检测 ──────────────────────────────────────

    /**
     * 检测 GAME_TICK 类型冷却记录是否因游戏时间回退而失效，若失效则自动清除。
     * <p>
     * 时间回退场景：服务端执行 {@code /time set} 后 dayTime 减小，导致冷却记录中的
     * dayTime 大于当前 dayTime，此时该记录应视为无效并清除。
     * <p>
     * 供对话系统（{@link DialogueActionExecutor}）和交易系统共用。
     */
    public static boolean clearIfTimeRegressed(DialogueProgressStore store, ProgressKey key, long nowDayTime) {
        Entry entry = store.getChoiceSelection(key);
        if (entry.exists() && entry.dayTime() > nowDayTime) {
            store.clearCooldownRecord(key);
            return true;
        }
        return false;
    }
}
