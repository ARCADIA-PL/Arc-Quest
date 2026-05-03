package org.arcadia.arc_quest.mutil.animation;

import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArcParallelAnimation implements ArcAnimation {
    private final List<ArcAnimation> animations = new ArrayList<>();
    private boolean finished = false;

    public ArcParallelAnimation(ArcAnimation... animations) {
        this.animations.addAll(Arrays.asList(animations));
    }

    public ArcParallelAnimation add(ArcAnimation animation) {
        this.animations.add(animation);
        return this;
    }

    @Override
    public void start(ArcGuiElement element, ArcGuiContext context) {
        if (animations.isEmpty()) {
            finished = true;
            return;
        }
        for (ArcAnimation animation : animations) {
            animation.start(element, context);
        }
    }

    @Override
    public void update(ArcGuiElement element, ArcGuiContext context) {
        if (finished) return;
        boolean allFinished = true;
        for (ArcAnimation animation : animations) {
            if (!animation.isFinished()) {
                animation.update(element, context);
            }
            allFinished &= animation.isFinished();
        }
        finished = allFinished;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void finish(ArcGuiElement element, ArcGuiContext context) {
        for (ArcAnimation animation : animations) {
            if (!animation.isFinished()) animation.finish(element, context);
        }
        finished = true;
    }
}
