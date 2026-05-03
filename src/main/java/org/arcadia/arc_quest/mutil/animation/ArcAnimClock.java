package org.arcadia.arc_quest.mutil.animation;

public final class ArcAnimClock {
    private ArcAnimClock() {
    }

    public static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    public static float lerp(float current, float target, float speedAt60Fps, float dt) {
        float factor = 1.0f - (float) Math.pow(1.0 - speedAt60Fps, dt * 60.0f);
        return current + (target - current) * factor;
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * clamp01(t);
    }

    public static float easeOutCubic(float t) {
        float inv = 1.0f - clamp01(t);
        return 1.0f - (inv * inv * inv);
    }

    public static float easeInCubic(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    public static float easeInQuartic(float t) {
        t = clamp01(t);
        return t * t * t * t;
    }

    public static float easeOutQuintic(float t) {
        float inv = 1.0f - clamp01(t);
        float inv2 = inv * inv;
        return 1.0f - (inv2 * inv2 * inv);
    }

    public static float smoothStep(float t) {
        t = clamp01(t);
        return t * t * (3.0f - 2.0f * t);
    }
}
