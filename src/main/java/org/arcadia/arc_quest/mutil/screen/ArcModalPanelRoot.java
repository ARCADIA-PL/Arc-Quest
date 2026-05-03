package org.arcadia.arc_quest.mutil.screen;

import org.arcadia.arc_quest.mutil.animation.ArcAnimClock;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;

public class ArcModalPanelRoot extends ArcPanelRoot {
    private boolean closing;
    private float transition;
    private float enterSpeed = 0.18f;
    private float exitSpeed = 0.2f;

    public ArcModalPanelRoot(int x, int y, int width, int height) {
        super(x, y, width, height);
        this.transition = 0f;
    }

    public void open() {
        closing = false;
        setActive(true);
    }

    public void close() {
        closing = true;
    }

    public boolean isClosing() {
        return closing;
    }

    public float getTransition() {
        return transition;
    }

    public ArcModalPanelRoot setSpeeds(float enterSpeed, float exitSpeed) {
        this.enterSpeed = enterSpeed;
        this.exitSpeed = exitSpeed;
        return this;
    }

    @Override
    protected void update(ArcGuiContext context, int refX, int refY) {
        transition = ArcAnimClock.lerp(transition, closing ? 0f : 1f, closing ? exitSpeed : enterSpeed, context.deltaTime());
        if (closing && transition < 0.01f) {
            closing = false;
            setActive(false);
            transition = 0f;
        }
        setOpacity(transition);
    }
}
