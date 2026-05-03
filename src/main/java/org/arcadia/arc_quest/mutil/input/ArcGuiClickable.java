package org.arcadia.arc_quest.mutil.input;

import org.arcadia.arc_quest.mutil.core.ArcGuiElement;

public class ArcGuiClickable extends ArcGuiElement {
    protected final Runnable onClick;

    public ArcGuiClickable(int x, int y, int width, int height, Runnable onClick) {
        super(x, y, width, height);
        this.onClick = onClick;
    }

    @Override
    public boolean onMouseClick(double mouseX, double mouseY, int button) {
        if (super.onMouseClick(mouseX, mouseY, button)) return true;
        if (focused && onClick != null) {
            onClick.run();
            return true;
        }
        return false;
    }
}
