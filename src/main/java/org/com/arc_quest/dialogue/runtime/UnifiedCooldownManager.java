package org.com.arc_quest.dialogue.runtime;

import com.mojang.logging.LogUtils;
import org.com.arc_quest.dialogue.api.CooldownType;
import org.com.arc_quest.dialogue.runtime.DialogueProgressStore.Entry;
import org.com.arc_quest.dialogue.runtime.DialogueProgressStore.TimeSnapshot;
import org.slf4j.Logger;

/**
 * 统一冷却管理器（Unified Cooldown Manager）。
 * <p>
 * 负责整个项目中所有冷却时间的计算与校验逻辑，包括：
 * <ul>
 *   <li>对话系统：节点访问、选项选择、对话树访问</li>
 *   <li>交易系统：商品购买冷却</li>
 * </ul>
 * <p>
 * 遵循单一职责原则，从 DialogueProgressStore 拆分而来。
 */
public final class UnifiedCooldownManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private UnifiedCooldownManager() {
    }

    /**
     * 统一冷却查询（核心入口）。
     */
    public static boolean isOnCooldown(DialogueProgressStore store, ProgressKey key, CooldownType cooldownType,
                                       int cooldownValue, int resetTick, TimeSnapshot ts) {
        Entry entry;
        if (key.index() >= 0) {
            entry = store.getChoiceSelection(key);
        } else {
            entry = store.hasVisitedNode(key)
                    ? store.getNodeVisit(key)
                    : store.getDialogueVisit(key);
        }
        return isOnCooldown(entry, cooldownType, cooldownValue, resetTick,
                ts.realTime(), ts.gameTime(), ts.dayTime());
    }

    /**
     * 通用冷却检测底层逻辑。
     */
    public static boolean isOnCooldown(Entry entry, CooldownType cooldownType,
                                       int cooldownValue, int resetTick,
                                       long nowRealTime, long nowGameTime, long nowDayTime) {
        if (!entry.exists() || cooldownType == CooldownType.NONE) {
            return false;
        }

        return switch (cooldownType) {
            case NONE -> false;

            case SECONDS -> {
                long cooldownMs = cooldownValue * 1000L;
                yield (nowRealTime - entry.realTime()) < cooldownMs;
            }

            case GAME_DAY -> {
                long lastGameTime = entry.gameTime();
                long lastDayTime = entry.dayTime();

                if (lastDayTime < 0 || lastGameTime < 0) {
                    yield false;
                }

                long gameTimeElapsed = nowGameTime - lastGameTime;
                if (gameTimeElapsed >= 24000) {
                    yield false;
                }

                long lastDay = lastDayTime / 24000L;
                long nowDay = nowDayTime / 24000L;
                if (lastDay != nowDay) {
                    yield false;
                }

                yield nowDayTime >= lastDayTime || gameTimeElapsed <= 0;
            }

            case GAME_TICK -> {
                long lastRawDayTime = entry.dayTime();
                long lastGameTime = entry.gameTime();

                if (lastRawDayTime < 0 || lastGameTime < 0) {
                    yield false;
                }

                long gameTimeElapsed = nowGameTime - lastGameTime;
                if (gameTimeElapsed >= 24000) {
                    yield false;
                }

                if (nowDayTime < lastRawDayTime && gameTimeElapsed > 0) {
                    yield false;
                }

                long recordedPeriod = Math.floorDiv(lastRawDayTime - resetTick, 24000);
                long currentPeriod = Math.floorDiv(nowDayTime - resetTick, 24000);
                yield (recordedPeriod == currentPeriod);
            }
        };
    }

    /**
     * 计算 GAME_TICK 冷却剩余时间（tick 数，用于客户端显示）。
     */
    public static int getGameTickCooldownRemainingTicks(Entry entry, int resetTick,
                                                        long nowGameTime, long nowDayTime) {
        if (!entry.exists() || entry.dayTime() < 0 || entry.gameTime() < 0) return 0;

        long gameTimeElapsed = nowGameTime - entry.gameTime();
        if (gameTimeElapsed >= 24000) return 0;
        if (nowDayTime < entry.dayTime() && gameTimeElapsed > 0) return 0;

        long recordedPeriod = Math.floorDiv(entry.dayTime() - resetTick, 24000);
        long currentPeriod = Math.floorDiv(nowDayTime - resetTick, 24000);
        if (recordedPeriod != currentPeriod) return 0;

        long currentDayTick = ((nowDayTime % 24000) + 24000) % 24000;
        long resetTickNorm = ((long) resetTick % 24000 + 24000) % 24000;

        if (currentDayTick >= resetTickNorm) {
            return (int) (24000 - currentDayTick + resetTickNorm);
        } else {
            return (int) (resetTickNorm - currentDayTick);
        }
    }
}