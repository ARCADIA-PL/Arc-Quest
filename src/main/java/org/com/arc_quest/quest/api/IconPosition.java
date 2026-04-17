package org.com.arc_quest.quest.api;

/**
 * 图标显示位置类型（高可扩展）。
 * <p>
 * 每种位置对应UI中的不同渲染点。
 */
public enum IconPosition {
    /** 任务列表中的缩略图标 */
    QUEST_LIST,

    /** 任务标题旁的装饰图标 */
    QUEST_TITLE,

    /** 任务详情面板内的主图标 */
    QUEST_DETAIL_PANEL,

    /** 阶段名称旁的图标 */
    PHASE_LABEL,

    /** HUD追踪栏的图标 */
    HUD_TRACKER,

    /** 对话界面的NPC头像 */
    DIALOGUE_NPC_AVATAR,

    // ═══════════════════════════════════════════
    //  未来扩展区域
    // ═══════════════════════════════════════════
    /** 地图标记图标 */
    // MAP_MARKER,

    /** 成就弹窗图标 */
    // ACHIEVEMENT_POPUP,
}
