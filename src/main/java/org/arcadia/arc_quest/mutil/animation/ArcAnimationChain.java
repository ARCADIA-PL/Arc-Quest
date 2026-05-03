package org.arcadia.arc_quest.mutil.animation;

import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArcAnimationChain implements ArcAnimation {
    private final List<ArcAnimation> animations = new ArrayList<>();
    private int index = 0;
    private boolean finished = false;

    public ArcAnimationChain(ArcAnimation... animations) {
        this.animations.addAll(Arrays.asList(animations));
    }

    public ArcAnimationChain add(ArcAnimation animation) {
        this.animations.add(animation);
        return this;
    }

    @Override
    public void start(ArcGuiElement element, ArcGuiContext context) {
        if (animations.isEmpty()) {
            finished = true;
            return;
        }
        animations.get(index).start(element, context);
    }

    @Override
    public void update(ArcGuiElement element, ArcGuiContext context) {
        if (finished) return;
        if (animations.isEmpty()) {
            finished = true;
            return;
        }

        ArcAnimation current = animations.get(index);
        current.update(element, context);
        if (current.isFinished()) {
            current.finish(element, context);
            index++;
            if (index >= animations.size()) {
                finished = true;
            } else {
                animations.get(index).start(element, context);
            }
        }
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void finish(ArcGuiElement element, ArcGuiContext context) {
        if (!animations.isEmpty() && index < animations.size()) {
            animations.get(index).finish(element, context);
        }
        finished = true;
    }
}
