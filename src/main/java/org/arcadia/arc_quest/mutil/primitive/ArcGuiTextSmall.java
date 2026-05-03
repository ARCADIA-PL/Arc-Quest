package org.arcadia.arc_quest.mutil.primitive;

public class ArcGuiTextSmall extends ArcGuiText {
    public ArcGuiTextSmall(int x, int y, String text) {
        super(x, y, text);
        this.height = 7;
    }

    public ArcGuiTextSmall(int x, int y, int width, String text) {
        super(x, y, width, text);
        this.height = 7;
    }
}
