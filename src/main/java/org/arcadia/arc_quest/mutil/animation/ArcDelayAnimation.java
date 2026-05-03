package org.arcadia.arc_quest.mutil.animation;

import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;

public class ArcDelayAnimation implements ArcAnimation {
    private final long delayMs;
    private boolean started;
    private boolean finished;
    private long startedAtMs;

    public ArcDelayAnimation(long delayMs) {
        this.delayMs = Math.max(0L, delayMs);
    }

    @Override
    public void start(ArcGuiElement element, ArcGuiContext context) {
        if (started) return;
        started = true;
        startedAtMs = context.nowMs();
        if (delayMs == 0L) finished = true;
    }

    @Override
    public void update(ArcGuiElement element, ArcGuiContext context) {
        if (finished) return;
        if (!started) start(element, context);
        finished = context.nowMs() - startedAtMs >= delayMs;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void finish(ArcGuiElement element, ArcGuiContext context) {
        finished = true;
    }
}
