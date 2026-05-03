package org.arcadia.arc_quest.mutil.primitive;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;

public class ArcGuiProgressBar extends ArcGuiElement {
    private float progress = 0f;
    private float displayedProgress = 0f;
    private float smoothing = 12f;
    private int backgroundColor = 0x33FFFFFF;
    private int fillColor = 0xCC4FC3F7;
    private int glowColor = 0xFFFFFFFF;
    private boolean glow = true;

    public ArcGuiProgressBar(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public ArcGuiProgressBar setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        return this;
    }

    public ArcGuiProgressBar setDisplayedProgress(float displayedProgress) {
        this.displayedProgress = Math.max(0f, Math.min(1f, displayedProgress));
        return this;
    }

    public ArcGuiProgressBar setSmoothing(float smoothing) {
        this.smoothing = Math.max(0f, smoothing);
        return this;
    }

    public ArcGuiProgressBar setColors(int backgroundColor, int fillColor, int glowColor) {
        this.backgroundColor = backgroundColor;
        this.fillColor = fillColor;
        this.glowColor = glowColor;
        return this;
    }

    public ArcGuiProgressBar setGlow(boolean glow) {
        this.glow = glow;
        return this;
    }

    @Override
    protected void update(ArcGuiContext context, int refX, int refY) {
        if (smoothing <= 0f) {
            displayedProgress = progress;
            return;
        }
        float diff = progress - displayedProgress;
        if (Math.abs(diff) > 0.001f) {
            float lerpFactor = 1.0f - (float) Math.exp(-smoothing * context.deltaTime());
            displayedProgress += diff * lerpFactor;
        } else {
            displayedProgress = progress;
        }
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!visible) return;
        int drawX = refX + x;
        int drawY = refY + y;
        ArcDrawUtil.drawProgressBar(graphics, drawX, drawY, width, height, displayedProgress, backgroundColor, fillColor);
        if (glow && displayedProgress > 0f) {
            int fillWidth = Math.round(width * displayedProgress);
            graphics.fill(drawX + fillWidth - 2, drawY - 1, drawX + fillWidth, drawY + height + 1, glowColor);
        }
        drawChildren(graphics, context, drawX, drawY, inheritedOpacity * opacity);
    }
}
