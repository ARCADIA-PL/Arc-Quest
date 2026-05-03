package org.arcadia.arc_quest.mutil.animation;

public class ArcFeedbackState {
    private float progress;
    private boolean success;

    public void trigger(boolean success) {
        this.success = success;
        this.progress = 1f;
    }

    public void update(float deltaTime, float speed) {
        progress = ArcAnimClock.lerp(progress, 0f, Math.max(0.01f, speed), deltaTime);
        if (progress < 0.001f) progress = 0f;
    }

    public boolean isActive() {
        return progress > 0f;
    }

    public float getProgress() {
        return progress;
    }

    public boolean isSuccess() {
        return success;
    }
}
