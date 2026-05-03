package org.arcadia.arc_quest.mutil.animation;

import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;

import java.util.function.Consumer;
import java.util.function.Function;

public class ArcKeyframeAnimation implements ArcAnimation {
    protected final Consumer<Float> applier;
    protected final float fromValue;
    protected final float toValue;
    protected final long durationMs;
    protected final Function<Float, Float> easing;
    protected boolean started;
    protected boolean finished;
    protected long startedAtMs;

    public ArcKeyframeAnimation(Consumer<Float> applier, float fromValue, float toValue, long durationMs, Function<Float, Float> easing) {
        this.applier = applier;
        this.fromValue = fromValue;
        this.toValue = toValue;
        this.durationMs = Math.max(1L, durationMs);
        this.easing = easing != null ? easing : ArcAnimClock::clamp01;
    }

    @Override
    public void start(ArcGuiElement element, ArcGuiContext context) {
        if (started) return;
        started = true;
        startedAtMs = context.nowMs();
        applier.accept(fromValue);
    }

    @Override
    public void update(ArcGuiElement element, ArcGuiContext context) {
        if (finished) return;
        if (!started) start(element, context);
        float linear = ArcAnimClock.clamp01((context.nowMs() - startedAtMs) / (float) durationMs);
        float eased = ArcAnimClock.clamp01(easing.apply(linear));
        applier.accept(ArcAnimClock.lerp(fromValue, toValue, eased));
        if (linear >= 1f) {
            finished = true;
            applier.accept(toValue);
        }
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void finish(ArcGuiElement element, ArcGuiContext context) {
        finished = true;
        applier.accept(toValue);
    }
}
