package org.arcadia.arc_quest.client.hud.quest.arcmutil.toast;

public class ArcQuestBranchToastViewModel {
    public static final float TIME_ENTER = 600f;
    public static final float TIME_EXIT = 400f;
    public static final float FLY_DIST = 10f;
    public static final int COLOR_ACCENT = 0xFFFFCC44;

    public final String questId;
    public final String phaseId;
    public long startTime;
    public boolean dismissing;
    public long dismissStartTime;
    public long lastUpdateTime;

    public ArcQuestBranchToastViewModel(String questId, String phaseId, long now) {
        this.questId = questId;
        this.phaseId = phaseId;
        this.startTime = now;
        this.lastUpdateTime = now;
    }

    public void dismiss(long now) {
        if (dismissing) return;
        dismissing = true;
        dismissStartTime = now;
    }

    public void tick(long now, boolean frozen) {
        if (frozen) {
            long dt = now - lastUpdateTime;
            startTime += dt;
            if (dismissing) dismissStartTime += dt;
        }
        lastUpdateTime = now;
    }

    public boolean isExpired(long now) {
        return dismissing && now - dismissStartTime >= TIME_EXIT;
    }

    public boolean sameTarget(String otherQuestId, String otherPhaseId) {
        if (otherQuestId == null) return false;
        if (!otherQuestId.equals(questId)) return false;
        if (phaseId == null) return otherPhaseId == null || otherPhaseId.isEmpty();
        return phaseId.equals(otherPhaseId);
    }
}
