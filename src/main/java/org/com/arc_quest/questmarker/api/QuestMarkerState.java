package org.com.arc_quest.questmarker.api;

import org.com.arc_quest.quest.api.QuestState;

/**
 * Marker 生命周期状态。
 * 与 QuestState 同步，额外保留 STANDBY / DISABLED 作为渲染级状态。
 */
public enum QuestMarkerState {
    LOCKED,
    AVAILABLE,
    ACTIVE,
    STANDBY,
    COMPLETED,
    FAILED,
    DISABLED;

    public static QuestMarkerState fromQuestState(QuestState state) {
        if (state == null) return ACTIVE;
        return switch (state) {
            case LOCKED -> LOCKED;
            case AVAILABLE -> AVAILABLE;
            case ACTIVE -> ACTIVE;
            case COMPLETED -> COMPLETED;
            case FAILED -> FAILED;
        };
    }

    /**
     * 无 quest 绑定 marker 的基础状态映射：激活中 / 待机。
     */
    public static QuestMarkerState standalone(boolean active) {
        return active ? ACTIVE : STANDBY;
    }

    public boolean isRenderable() {
        return this != DISABLED;
    }
}
