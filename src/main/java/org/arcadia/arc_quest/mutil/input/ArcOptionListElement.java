package org.arcadia.arc_quest.mutil.input;

import org.arcadia.arc_quest.mutil.animation.ArcAnimClock;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;

import java.util.ArrayList;
import java.util.List;

public class ArcOptionListElement extends ArcGuiElement {
    private final List<ArcButtonElement> options = new ArrayList<>();
    private int selectedIndex = -1;
    private int spacing = 6;
    private float[] reveal = new float[0];
    private OptionSelected onSelected;

    public ArcOptionListElement(int x, int y, int width) {
        super(x, y, width, 0);
    }

    public ArcOptionListElement setSpacing(int spacing) {
        this.spacing = spacing;
        relayout();
        return this;
    }

    public ArcOptionListElement setOnSelected(OptionSelected onSelected) {
        this.onSelected = onSelected;
        return this;
    }

    public void setOptions(int count, int optionHeight) {
        clearChildren();
        options.clear();
        reveal = new float[count];
        for (int i = 0; i < count; i++) {
            final int index = i;
            ArcButtonElement button = new ArcButtonElement(0, i * (optionHeight + spacing), width, optionHeight, () -> select(index));
            options.add(button);
            addChild(button);
        }
        relayout();
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public ArcButtonElement getOption(int index) {
        return index >= 0 && index < options.size() ? options.get(index) : null;
    }

    public float getReveal(int index) {
        return index >= 0 && index < reveal.length ? reveal[index] : 0f;
    }

    public void select(int index) {
        if (index < 0 || index >= options.size()) return;
        selectedIndex = index;
        if (onSelected != null) onSelected.onSelected(index);
    }

    private void relayout() {
        for (int i = 0; i < options.size(); i++) {
            options.get(i).setPosition(0, i * (options.get(i).getHeight() + spacing));
            options.get(i).setWidth(width);
        }
        height = options.isEmpty() ? 0 : options.size() * options.get(0).getHeight() + (options.size() - 1) * spacing;
    }

    @Override
    protected void update(ArcGuiContext context, int refX, int refY) {
        for (int i = 0; i < reveal.length; i++) {
            float target = 1f;
            reveal[i] = ArcAnimClock.lerp(reveal[i], target, 0.10f + i * 0.02f, context.deltaTime());
        }
    }

    public interface OptionSelected {
        void onSelected(int index);
    }
}
