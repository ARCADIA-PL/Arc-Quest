package org.com.arc_quest.questmarker.api;

public enum QuestMarkerType {
    QUEST_MAIN,
    QUEST_SIDE,
    NPC_INTERACT,
    ENEMY_TARGET,
    LOCATION,
    CUSTOM,

    // 向后兼容别名
    QUEST,
    NPC,
    INTERACT,
    ENEMY
}
