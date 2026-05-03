package org.arcadia.arc_quest.mutil.screen;

import org.arcadia.arc_quest.mutil.core.ArcGuiElement;

public class ArcPanelRoot extends ArcGuiElement {
    private boolean active = true;

    public ArcPanelRoot(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        setVisible(active);
    }
}
