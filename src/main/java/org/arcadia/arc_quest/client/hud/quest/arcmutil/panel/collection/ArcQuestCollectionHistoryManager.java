package org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.collection;

public final class ArcQuestCollectionHistoryManager {
    private static boolean active;
    private static String questId;

    private ArcQuestCollectionHistoryManager() {
    }

    public static void trigger(String qid) {
        questId = qid;
        active = true;
    }

    public static boolean isActive() {
        return active;
    }

    public static String questId() {
        return questId;
    }

    public static void close() {
        active = false;
    }

    public static boolean keyPressed(int keyCode) {
        if (!active) return false;
        if (keyCode == 256 || keyCode == 69) {
            close();
            return true;
        }
        return false;
    }
}
