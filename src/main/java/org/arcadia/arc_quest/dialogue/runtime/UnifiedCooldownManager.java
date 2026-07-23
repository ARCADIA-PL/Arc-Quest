package org.arcadia.arc_quest.dialogue.runtime;

import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.core.time.TimeSnapshot;
import org.arcadia.arc_quest.dialogue.api.CooldownType;
import org.arcadia.arc_quest.dialogue.runtime.DialogueProgressStore.Entry;

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
 * {@link Entry} 和 {@link org.arcadia.arc_quest.quest.data.GachaDataStore.CooldownEntry}
 * 等具体 record 均实现该接口。
 */
public final class UnifiedCooldownManager {

    private UnifiedCooldownManager() {
    }

    // ── Store 版本入口（对话系统专用）────────────────────

    /**
     * 统一冷却查询（核心入口）。
     * 单 Map 合并后简化：无论是节点、对话、选项，直接按 key 从 store 中查找。
     */
    public static boolean isOnCooldown(DialogueProgressStore store, ProgressKey key, CooldownType cooldownType,
                                       int cooldownValue, int resetTick, DialogueProgressStore.TimeSnapshot ts) {
        ICooldownRecord record = store.getEntry(key);
        return isOnCooldown(record, cooldownType, cooldownValue, resetTick,
                ts.realTime(), ts.gameTime(), ts.dayTime());
    }

    // ── ICooldownRecord 版本（通用，所有系统共用）────────

    /**
     * 通用冷却检测底层逻辑（接受任意 {@link ICooldownRecord} 实现）。
     * <p>
     * {@link Entry}、{@link org.arcadia.arc_quest.quest.data.GachaDataStore.CooldownEntry}、
     * {@link org.arcadia.arc_quest.quest.data.TradeDataStore.TradeCooldownEntry} 均可传入。
     */
    public static boolean isOnCooldown(ICooldownRecord record, CooldownType cooldownType,
                                       int cooldownValue, int resetTick,
                                       long nowRealTime, long nowGameTime, long nowDayTime) {
        return CoreProcessors.get().cooldowns().isOnCooldown(
                record, cooldownType.toCorePolicy(cooldownValue, resetTick),
                new TimeSnapshot(nowRealTime, nowGameTime, nowDayTime));
    }

    // ── 剩余时间计算 ──────────────────────────────────────

    /**
     * 计算 GAME_TICK 冷却剩余时间（tick 数，用于客户端显示）。
     * 接受任意 {@link ICooldownRecord} 实现。
     */
    public static int getGameTickCooldownRemainingTicks(ICooldownRecord record, int resetTick,
                                                        long nowGameTime, long nowDayTime) {
        return CoreProcessors.get().cooldowns().remainingGameTicks(
                record, resetTick, new TimeSnapshot(0L, nowGameTime, nowDayTime));
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
        return store.clearIfTimeRegressed(key, nowDayTime);
    }
}
