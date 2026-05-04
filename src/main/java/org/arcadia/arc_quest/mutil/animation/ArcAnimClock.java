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

    public static float step(float current, float target, float speedPerSecond, float dt) {
        float amount = Math.max(0f, speedPerSecond) * Math.max(0f, dt);
        if (current < target) return Math.min(current + amount, target);
        if (current > target) return Math.max(current - amount, target);
        return current;
    }

    public static float easeOutBack(float t) {
        float f = clamp01(t) - 1.0f;
        return 1.0f + f * f * (2.70158f * f + 1.70158f);
    }

    public static float easeInSextic(float t) {
        t = clamp01(t);
        float t3 = t * t * t;
        return t3 * t3;
    }

    public static float easeOutElastic(float t) {
        t = clamp01(t);
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float c4 = (2f * 3.14159265f) / 3f;
        return (float) Math.pow(2, -10 * t) * (float) Math.sin((t * 10f - 0.75f) * c4) + 1f;
    }

    public static float smoothStep(float t) {
        t = clamp01(t);
        return t * t * (3.0f - 2.0f * t);
    }
}
