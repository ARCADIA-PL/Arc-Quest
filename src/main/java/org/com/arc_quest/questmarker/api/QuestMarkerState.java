package org.com.arc_quest.questmarker.api;

import org.com.arc_quest.quest.api.QuestState;

/**
 * Marker 生命周期状态。
 * 与 QuestState 同步，额外保留 DISABLED 作为渲染级关闭态。
 */
public enum QuestMarkerState {
    LOCKED,
    AVAILABLE,
    ACTIVE,
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

    public boolean isRenderable() {
        return this != DISABLED;
    }
}