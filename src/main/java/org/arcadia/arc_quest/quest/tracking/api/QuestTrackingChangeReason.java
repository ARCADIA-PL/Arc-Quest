package org.arcadia.arc_quest.quest.tracking.api;

public enum QuestTrackingChangeReason {
    UNKNOWN,
    USER_TRACK,
    USER_UNTRACK,
    AUTO_ON_ACCEPT,
    AUTO_ON_TERMINATED,
    AUTO_ON_RESET,
    PLAYER_LOADED,
    PLAYER_RESPAWNED,
    DIMENSION_CHANGED,
    REGISTRY_RELOADED,
    RECONCILE,
    FULL_SYNC
}
