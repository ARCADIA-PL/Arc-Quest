package org.arcadia.arc_quest.mutil.layout;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;

public class ArcTransformGroup extends ArcGuiElement {
    private float translateX;
    private float translateY;
    private float scaleX = 1f;
    private float scaleY = 1f;
    private float zOffset;

    public ArcTransformGroup(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public ArcTransformGroup setTranslation(float translateX, float translateY) {
        this.translateX = translateX;
        this.translateY = translateY;
        return this;
    }

    public ArcTransformGroup setScale(float scale) {
        this.scaleX = scale;
        this.scaleY = scale;
        return this;
    }

    public ArcTransformGroup setScale(float scaleX, float scaleY) {
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        return this;
    }

    public ArcTransformGroup setZOffset(float zOffset) {
        this.zOffset = zOffset;
        return this;
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!visible) return;
        graphics.pose().pushPose();
        graphics.pose().translate(refX + x + translateX, refY + y + translateY, zOffset);
        graphics.pose().scale(scaleX, scaleY, 1f);
        drawChildren(graphics, context, 0, 0, inheritedOpacity * opacity);
        graphics.pose().popPose();
    }
}
