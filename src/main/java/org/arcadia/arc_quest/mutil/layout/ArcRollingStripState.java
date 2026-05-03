package org.arcadia.arc_quest.mutil.layout;

import org.arcadia.arc_quest.mutil.animation.ArcAnimClock;

public class ArcRollingStripState {
    private double scroll;
    private double target;
    private double velocity;
    private float smoothing = 0.18f;

    public double getScroll() {
        return scroll;
    }

    public ArcRollingStripState setScroll(double scroll) {
        this.scroll = scroll;
        return this;
    }

    public ArcRollingStripState setTarget(double target) {
        this.target = target;
        return this;
    }

    public ArcRollingStripState setVelocity(double velocity) {
        this.velocity = velocity;
        return this;
    }

    public ArcRollingStripState setSmoothing(float smoothing) {
        this.smoothing = smoothing;
        return this;
    }

    public void update(float dt) {
        if (velocity != 0) {
            scroll += velocity * dt;
            return;
        }
        scroll = ArcAnimClock.lerp((float) scroll, (float) target, smoothing, dt);
    }
}
