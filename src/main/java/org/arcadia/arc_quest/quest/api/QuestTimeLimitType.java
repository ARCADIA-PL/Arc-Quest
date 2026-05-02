package org.arcadia.arc_quest.quest.api;

/**
 * 任务限时类型（章节级）。
 */
public enum QuestTimeLimitType {
    /**
     * 基于现实时间（秒）。
     */
    REAL_SECONDS,

    /**
     * 基于游戏日时间（dayTime，0~23999 循环）。
     */
    GAME_DAY_TIME
}
