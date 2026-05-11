package org.arcadia.arc_quest.quest.api;

/**
 * 目标类型枚举。每种类型决定了在 ObjectiveTracker 中
 * 使用哪个 key-space 做 O(1) 查找。
 */
public enum ObjectiveType {

    /**
     * 空目标/占位目标，用于纯说明或手动确认推进场景。
     */
    NULL,

    /**
     * 击杀指定实体类型
     */
    KILL,

    /**
     * 收集/持有指定物品
     */
    COLLECT,

    /**
     * 与 NPC 对话（自定义 NPC ID）
     */
    TALK,

    /**
     * 右键交互指定方块/实体
     */
    INTERACT,

    /**
     * 抵达指定区域
     */
    REACH_LOCATION,

    /**
     * 提交（上交）指定物品给 NPC
     */
    DELIVER,

    /**
     * 制作指定物品
     */
    CRAFT,

    OFFER,

    /**
     * 纯代码自定义检测（回调驱动）
     */
    CUSTOM;

    /**
     * 该类型是否需要累计计数
     * （TALK / REACH_LOCATION / NULL 通常只需触发一次）
     */
    public boolean isCounting() {
        return this == KILL || this == COLLECT || this == DELIVER;
    }
}
