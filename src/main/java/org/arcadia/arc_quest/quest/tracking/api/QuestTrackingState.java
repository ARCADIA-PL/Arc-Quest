package org.arcadia.arc_quest.quest.tracking.api;

public enum QuestTrackingState {
    EMPTY,
    UNTRACKED_MANUAL,
    TRACKING_MANUAL,
    TRACKING_AUTOMATIC;

    public boolean isTracking() {
        return this == TRACKING_MANUAL || this == TRACKING_AUTOMATIC;
    }
}
