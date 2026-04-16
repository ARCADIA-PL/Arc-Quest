package org.com.arc_quest.quest.api;

/**
 * 任务生命周期状态机。
 *
 * LOCKED ──(解锁条件满足)──▸ AVAILABLE
 * AVAILABLE ──(玩家接取)──▸ ACTIVE
 * ACTIVE ──(全部阶段完成)──▸ COMPLETED
 * ACTIVE ──(失败条件触发)──▸ FAILED
 */
public enum QuestState {

    /** 条件未满足，玩家不可见/不可接 */
    LOCKED,

    /** 条件满足，可以接取 */
    AVAILABLE,

    /** 进行中 */
    ACTIVE,

    /** 已完成 */
    COMPLETED,

    /** 已失败 */
    FAILED;

    private static final QuestState[] VALUES = values();

    public static QuestState fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= VALUES.length) return LOCKED;
        return VALUES[ordinal];
    }

    /** 是否为终态（不可再转移） */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

    /** 是否仍然"活跃"——需要检测目标进度 */
    public boolean isTracking() {
        return this == ACTIVE;
    }
}