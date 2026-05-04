package org.arcadia.arc_quest.mutil.input;

import org.arcadia.arc_quest.mutil.animation.ArcAnimClock;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;

public class ArcButtonElement extends ArcGuiElement {
    protected Runnable onClick;
    protected boolean pressed;
    protected boolean enabled = true;
    protected float hoverProgress;
    protected float pressProgress;

    public ArcButtonElement(int x, int y, int width, int height, Runnable onClick) {
        super(x, y, width, height);
        this.onClick = onClick;
    }

    public ArcButtonElement setOnClick(Runnable onClick) {
        this.onClick = onClick;
        return this;
    }

    public ArcButtonElement setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public float getHoverProgress() {
        return hoverProgress;
    }

    public float getPressProgress() {
        return pressProgress;
    }

    public void setHoverProgress(float hoverProgress) {
        this.hoverProgress = clamp01(hoverProgress);
    }

    public float stepHover(boolean hovered, float speed, float deltaTime) {
        float target = hovered && enabled ? 1f : 0f;
        hoverProgress += (target - hoverProgress) * Math.min(1f, Math.max(0f, deltaTime) * speed);
        return hoverProgress;
    }

    public static boolean containsLocal(float localX, float localY, int x, int y, int width, int height) {
        return localX >= x && localX <= x + width && localY >= y && localY <= y + height;
    }

    @Override
    protected void update(ArcGuiContext context, int refX, int refY) {
        hoverProgress = ArcAnimClock.lerp(hoverProgress, enabled && focused ? 1f : 0f, 0.2f, context.deltaTime());
        pressProgress = ArcAnimClock.lerp(pressProgress, pressed ? 1f : 0f, 0.3f, context.deltaTime());
    }

    @Override
    public boolean onMouseClick(double mouseX, double mouseY, int button) {
        if (super.onMouseClick(mouseX, mouseY, button)) return true;
        if (!enabled || button != 0 || !focused) return false;
        pressed = true;
        return true;
    }

    @Override
    public void onMouseRelease(double mouseX, double mouseY, int button) {
        super.onMouseRelease(mouseX, mouseY, button);
        if (button == 0 && pressed) {
            boolean shouldClick = enabled && focused;
            pressed = false;
            if (shouldClick && onClick != null) onClick.run();
        }
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
