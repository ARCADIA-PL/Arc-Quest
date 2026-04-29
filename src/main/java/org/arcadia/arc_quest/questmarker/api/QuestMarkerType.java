package org.arcadia.arc_quest.questmarker.api;

/**
 * Marker 语义类型。
 * 直接对齐 Quest 模块的主/支线、阶段、目标追踪语义。
 */
public enum QuestMarkerType {
    QUEST_MAIN,
    QUEST_SIDE,
    QUEST_PHASE,
    QUEST_OBJECTIVE,

    NPC_INTERACT,
    ENEMY_TARGET,
    LOCATION,
    CUSTOM,

    // 兼容旧值
    QUEST,
    NPC,
    INTERACT,
    ENEMY;

    public boolean isQuestLinkedType() {
        return this == QUEST_MAIN || this == QUEST_SIDE || this == QUEST_PHASE || this == QUEST_OBJECTIVE || this == QUEST;
    }

    public QuestMarkerType canonical() {
        return switch (this) {
            case QUEST -> QUEST_OBJECTIVE;
            case NPC -> NPC_INTERACT;
            case INTERACT -> NPC_INTERACT;
            case ENEMY -> ENEMY_TARGET;
            default -> this;
        };
    }
}
