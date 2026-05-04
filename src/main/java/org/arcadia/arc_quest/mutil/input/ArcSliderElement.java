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

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    public boolean isDragging() {
        return dragging;
    }

    public float getProgress() {
        return progressFor(value, min, max);
    }

    public void setDragging(boolean dragging) {
        this.dragging = dragging;
    }

    public void setValueSilently(int value) {
        this.value = clampValue(value, min, max);
    }

    public void setValue(int value) {
        int next = clampValue(value, min, max);
        if (this.value == next) return;
        this.value = next;
        if (onChanged != null) onChanged.onChanged(next);
    }

    public static int valueFromProgress(float progress, int min, int max) {
        int safeMin = Math.min(min, max);
        int safeMax = Math.max(min, max);
        return clampValue(Math.round(safeMin + clamp01(progress) * (safeMax - safeMin)), safeMin, safeMax);
    }

    public static float progressFor(int value, int min, int max) {
        int safeMin = Math.min(min, max);
        int safeMax = Math.max(min, max);
        if (safeMax <= safeMin) return 0f;
        return (clampValue(value, safeMin, safeMax) - safeMin) / (float) (safeMax - safeMin);
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
        setValue(valueFromProgress(progress, min, max));
    }

    private static int clampValue(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    public interface ValueChanged {
        void onChanged(int value);
    }
}
