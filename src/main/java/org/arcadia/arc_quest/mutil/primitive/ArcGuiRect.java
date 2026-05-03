package org.arcadia.arc_quest.mutil.primitive;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.core.ArcGuiColor;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;

public class ArcGuiRect extends ArcGuiElement {
    protected int color;
    protected boolean halfPixelOffset;

    public ArcGuiRect(int x, int y, int width, int height, int color) {
        this(x, y, width, height, color, false);
    }

    public ArcGuiRect(int x, int y, int width, int height, int color, boolean halfPixelOffset) {
        super(x, y, halfPixelOffset ? width + 1 : width, halfPixelOffset ? height + 1 : height);
        this.color = color;
        this.halfPixelOffset = halfPixelOffset;
    }

    public ArcGuiRect setColor(int color) {
        this.color = color;
        return this;
    }

    public int getColor() {
        return color;
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!visible) return;
        int finalColor = ArcGuiColor.withOpacity(color, inheritedOpacity * opacity);
        if (halfPixelOffset) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.5F, 0.5F, 0);
            graphics.fill(refX + x, refY + y, refX + x + width - 1, refY + y + height - 1, finalColor);
            graphics.pose().popPose();
        } else {
            graphics.fill(refX + x, refY + y, refX + x + width, refY + y + height, finalColor);
        }
        drawChildren(graphics, context, refX + x, refY + y, inheritedOpacity * opacity);
    }
}
