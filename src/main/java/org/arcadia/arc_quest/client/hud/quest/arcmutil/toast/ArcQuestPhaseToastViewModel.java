package org.arcadia.arc_quest.client.hud.quest.arcmutil.toast;

public class ArcQuestPhaseToastViewModel {
    public static final float PHASE_ENTER = 500f;
    public static final float PHASE_HOLD = 2600f;
    public static final float PHASE_EXIT = 400f;
    public static final float PHASE_FLY = 10f;
    public static final float PHASE_DRIFT = 1f;

    public final String phaseName;
    public final int themeColor;
    public final Kind kind;
    public long startTime;
    public long lastUpdateTime;

    public ArcQuestPhaseToastViewModel(String phaseName, int themeColor, Kind kind, long now) {
        this.phaseName = phaseName;
        this.themeColor = themeColor;
        this.kind = kind == null ? Kind.ADDED : kind;
        this.startTime = now;
        this.lastUpdateTime = now;
    }

    public void tick(long now, boolean frozen) {
        long dt = now - lastUpdateTime;
        lastUpdateTime = now;
        if (frozen) startTime += dt;
    }

    public boolean isExpired(long now) {
        return now - startTime >= PHASE_ENTER + PHASE_HOLD + PHASE_EXIT;
    }

    public enum Kind {
        ADDED,
        SWITCHED,
        COMPLETED
    }
}
