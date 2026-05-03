package org.arcadia.arc_quest.mutil.input;

import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;

public class ArcSliderElement extends ArcGuiElement {
    private int min = 0;
    private int max = 1;
    private int value = 0;
    private boolean dragging;
    private ValueChanged onChanged;

    public ArcSliderElement(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public ArcSliderElement configure(int min, int max, int value) {
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
        setValue(value);
        return this;
    }

    public ArcSliderElement setOnChanged(ValueChanged onChanged) {
        this.onChanged = onChanged;
        return this;
    }

    public int getValue() {
        return value;
    }

    public float getProgress() {
        if (max <= min) return 0f;
        return (value - min) / (float) (max - min);
    }

    public void setValue(int value) {
        int next = Math.max(min, Math.min(max, value));
        if (this.value == next) return;
        this.value = next;
        if (onChanged != null) onChanged.onChanged(next);
    }

    @Override
    public boolean onMouseClick(double mouseX, double mouseY, int button) {
        if (button != 0 || !focused) return super.onMouseClick(mouseX, mouseY, button);
        dragging = true;
        updateFromMouse(mouseX);
        return true;
    }

    @Override
    public void onMouseRelease(double mouseX, double mouseY, int button) {
        super.onMouseRelease(mouseX, mouseY, button);
        if (button == 0) dragging = false;
    }

    @Override
    protected void update(ArcGuiContext context, int refX, int refY) {
        if (dragging) updateFromMouse(context.mouseX());
    }

    private void updateFromMouse(double mouseX) {
        double localX = mouseX - getAbsoluteX();
        float progress = (float) Math.max(0.0, Math.min(1.0, localX / Math.max(1, width)));
        setValue(Math.round(min + progress * (max - min)));
    }

    public interface ValueChanged {
        void onChanged(int value);
    }
}
