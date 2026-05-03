package org.arcadia.arc_quest.mutil.animation;

public class ArcVisibilityFilter {
    private final float fadeInSpeedAt60Fps;
    private final float fadeOutSpeedAt60Fps;
    private float opacity;

    public ArcVisibilityFilter() {
        this(0.18f, 0.18f);
    }

    public ArcVisibilityFilter(float fadeInSpeedAt60Fps, float fadeOutSpeedAt60Fps) {
        this.fadeInSpeedAt60Fps = fadeInSpeedAt60Fps;
        this.fadeOutSpeedAt60Fps = fadeOutSpeedAt60Fps;
    }

    public float update(boolean shouldShow, float dt) {
        opacity = ArcAnimClock.lerp(opacity, shouldShow ? 1f : 0f, shouldShow ? fadeInSpeedAt60Fps : fadeOutSpeedAt60Fps, dt);
        return opacity;
    }

    public float getOpacity() {
        return opacity;
    }

    public boolean isVisible(float threshold) {
        return opacity > threshold;
    }

    public void reset(float opacity) {
        this.opacity = ArcAnimClock.clamp01(opacity);
    }
}
