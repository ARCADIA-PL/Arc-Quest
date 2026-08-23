package org.arcadia.arc_quest.questmarker.internal;

public final class MarkerIds {

    private static final String AUTO_PREFIX = "aq:auto:";
    private static final String TRACKING_PREFIX = "aq:tracking:";
    private static final String DIALOGUE_PREFIX = "aq:dlg:";
    private static final String TRIGGER_PREFIX = "aq:trigger:";
    private static final String LEGACY_LOCATION_PREFIX = "quest:";

    private MarkerIds() {
    }

    public static String autoQuestPrefix(String questId) {
        return AUTO_PREFIX + questId + ":";
    }

    public static String autoQuest(String questId, String markerSpecId) {
        return autoQuestPrefix(questId) + markerSpecId;
    }

    public static String autoPhase(String questId, String phaseId, String markerSpecId) {
        return autoQuestPrefix(questId) + phaseId + ":phase:" + markerSpecId;
    }

    public static String autoObjective(String questId, String phaseId, int objectiveIndex, String markerSpecId) {
        return autoQuestPrefix(questId) + phaseId + ":obj" + objectiveIndex + ":" + markerSpecId;
    }

    public static String trackingPhase(String questId, String phaseId, String markerSpecId) {
        return TRACKING_PREFIX + questId + ":" + phaseId + ":" + markerSpecId;
    }

    public static String triggeredQuestPrefix(String questId) {
        return TRIGGER_PREFIX + "quest:" + questId + ":";
    }

    public static String legacyLocation(String questId, String phaseId, int objectiveIndex) {
        return LEGACY_LOCATION_PREFIX + questId + ":" + phaseId + ":" + objectiveIndex;
    }

    public static boolean isAuto(String markerId) {
        return markerId != null && markerId.startsWith(AUTO_PREFIX);
    }

    public static boolean isTracking(String markerId) {
        return markerId != null && markerId.startsWith(TRACKING_PREFIX);
    }

    public static boolean isDialogue(String markerId) {
        return markerId != null && (markerId.startsWith(DIALOGUE_PREFIX)
                || markerId.startsWith(TRIGGER_PREFIX + "dialogue:"));
    }

    public static boolean isTriggered(String markerId) {
        return markerId != null && markerId.startsWith(TRIGGER_PREFIX);
    }

    public static boolean isLegacyLocation(String markerId) {
        return markerId != null && markerId.startsWith(LEGACY_LOCATION_PREFIX);
    }

    public static boolean isDerived(String markerId) {
        return isAuto(markerId) || isTracking(markerId) || isDialogue(markerId)
                || isTriggered(markerId) || isLegacyLocation(markerId);
    }
}
