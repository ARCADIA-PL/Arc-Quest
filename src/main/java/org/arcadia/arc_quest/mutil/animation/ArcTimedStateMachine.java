package org.arcadia.arc_quest.mutil.animation;

public class ArcTimedStateMachine<S extends Enum<S>> {
    private S state;
    private long stateStartedAt;

    public ArcTimedStateMachine(S initialState, long nowMs) {
        this.state = initialState;
        this.stateStartedAt = nowMs;
    }

    public S getState() {
        return state;
    }

    public void setState(S state, long nowMs) {
        if (this.state == state) return;
        this.state = state;
        this.stateStartedAt = nowMs;
    }

    public long elapsed(long nowMs) {
        return nowMs - stateStartedAt;
    }

    public float progress(long nowMs, long durationMs) {
        if (durationMs <= 0L) return 1f;
        return ArcAnimClock.clamp01(elapsed(nowMs) / (float) durationMs);
    }

    public boolean elapsedAtLeast(long nowMs, long durationMs) {
        return elapsed(nowMs) >= durationMs;
    }
}
