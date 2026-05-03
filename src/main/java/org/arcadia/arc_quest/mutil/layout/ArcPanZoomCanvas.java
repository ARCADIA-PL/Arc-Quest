package org.arcadia.arc_quest.mutil.layout;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.animation.ArcAnimClock;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.screen.ArcScissorStack;

public class ArcPanZoomCanvas extends ArcGuiElement {
    private float zoom = 1f;
    private float targetZoom = 1f;
    private float minZoom = 1f;
    private float maxZoom = 2f;
    private float panX;
    private float panY;
    private float targetPanX;
    private float targetPanY;
    private boolean panning;
    private double lastDragX;
    private double lastDragY;
    private final ArcScissorStack scissorStack = new ArcScissorStack();

    public ArcPanZoomCanvas(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public ArcPanZoomCanvas setZoomRange(float minZoom, float maxZoom) {
        this.minZoom = minZoom;
        this.maxZoom = Math.max(minZoom, maxZoom);
        targetZoom = Math.max(this.minZoom, Math.min(this.maxZoom, targetZoom));
        return this;
    }

    public ArcPanZoomCanvas setTarget(float targetZoom, float targetPanX, float targetPanY) {
        this.targetZoom = Math.max(minZoom, Math.min(maxZoom, targetZoom));
        this.targetPanX = targetPanX;
        this.targetPanY = targetPanY;
        return this;
    }

    public float getZoom() {
        return zoom;
    }

    public float getPanX() {
        return panX;
    }

    public float getPanY() {
        return panY;
    }

    @Override
    protected void update(ArcGuiContext context, int refX, int refY) {
        zoom = ArcAnimClock.lerp(zoom, targetZoom, 0.18f, context.deltaTime());
        panX = ArcAnimClock.lerp(panX, targetPanX, 0.18f, context.deltaTime());
        panY = ArcAnimClock.lerp(panY, targetPanY, 0.18f, context.deltaTime());
        if (panning) {
            targetPanX += (float) (context.mouseX() - lastDragX);
            targetPanY += (float) (context.mouseY() - lastDragY);
            lastDragX = context.mouseX();
            lastDragY = context.mouseY();
        }
    }

    @Override
    public boolean onMouseClick(double mouseX, double mouseY, int button) {
        if (super.onMouseClick(mouseX, mouseY, button)) return true;
        if (button == 0 && focused) {
            panning = true;
            lastDragX = mouseX;
            lastDragY = mouseY;
            return true;
        }
        return false;
    }

    @Override
    public void onMouseRelease(double mouseX, double mouseY, int button) {
        super.onMouseRelease(mouseX, mouseY, button);
        if (button == 0) panning = false;
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double distance) {
        if (!focused) return super.onMouseScroll(mouseX, mouseY, distance);
        float oldZoom = targetZoom;
        targetZoom = Math.max(minZoom, Math.min(maxZoom, targetZoom + (float) distance * 0.15f));
        targetPanX = (float) ((mouseX - x) - (((mouseX - x) - targetPanX) / oldZoom) * targetZoom);
        targetPanY = (float) ((mouseY - y) - (((mouseY - y) - targetPanY) / oldZoom) * targetZoom);
        return true;
    }

    @Override
    protected void drawChildren(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        scissorStack.push(graphics, refX, refY, width, height);
        graphics.pose().pushPose();
        graphics.pose().translate(refX + panX, refY + panY, 0);
        graphics.pose().scale(zoom, zoom, 1f);
        super.drawChildren(graphics, context, 0, 0, inheritedOpacity);
        graphics.pose().popPose();
        scissorStack.pop(graphics);
    }
}
