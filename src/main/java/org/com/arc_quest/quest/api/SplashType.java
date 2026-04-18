package org.com.arc_quest.quest.api;

/**
 * 立绘显示时机类型。
 * <p>
 * 每种类型对应不同的触发时机和渲染场景。
 */
public enum SplashType {
    /**
     * 任务详情界面内显示的立绘（静态展示）
     */
    QUEST_DETAIL,

    /**
     * 获得新任务时弹出的立绘（动画）
     */
    QUEST_ACQUIRED,

    /**
     * 任务篇章（Phase）开始时弹出的立绘（动画）
     */
    PHASE_START,

    /**
     * 任务篇章（Phase）结束时弹出的立绘（动画）
     */
    PHASE_COMPLETE,

    /**
     * 任务完全完成时弹出的立绘（动画）
     */
    QUEST_COMPLETED,

    /**
     * 对话开始时的立绘（动画）
     */
    DIALOGUE_START,

    /**
     * 对话结束时的立绘（动画）
     */
    DIALOGUE_END,

    // ═══════════════════════════════════════════
    //  未来扩展区域（按需添加）
    // ═══════════════════════════════════════════
    /**
     * 任务失败时的立绘
     */
    QUEST_FAILED,

    /** 分支选择时的立绘 */
    // BRANCH_CHOICE,

    /** 奖励领取时的立绘 */
    // REWARD_CLAIMED,
}
