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

    public static void renderThin(GuiGraphics graphics, int x, int y, int width, float progress, int alpha, int themeColor) {
        int clampedAlpha = ArcDrawUtil.clampAlpha(alpha);
        float clampedProgress = Math.max(0f, Math.min(1f, progress));
        graphics.fill(x, y, x + width, y + 1, ArcDrawUtil.withAlpha(0x334455, (int) (clampedAlpha * 0.4f)));
        int fillWidth = (int) (width * clampedProgress);
        if (fillWidth > 0) {
            graphics.fill(x, y, x + fillWidth, y + 1, ArcDrawUtil.withAlpha(themeColor, clampedAlpha));
        }
        graphics.fill(x + fillWidth - 1, y - 1, x + fillWidth + 1, y + 2, ArcDrawUtil.withAlpha(0xFFFFFF, clampedAlpha));
    }

    public static void renderKeyframeMarkers(GuiGraphics graphics, int x, int y, int width, int alpha, int themeColor, int totalTime, int keyframeCount, KeyframeTime keyframeTime) {
        if (totalTime <= 0 || keyframeCount <= 0 || keyframeTime == null) return;
        int clampedAlpha = ArcDrawUtil.clampAlpha(alpha);
        for (int k = 0; k < keyframeCount; k++) {
            float progress = Math.max(0f, Math.min(1f, keyframeTime.timeAt(k) / (float) totalTime));
            int markerX = x + (int) (width * progress);
            graphics.fill(markerX, y - 1, markerX + 1, y + 2, ArcDrawUtil.withAlpha(themeColor, clampedAlpha));
        }
    }

    public static float progressFromLocal(float localX, int x, int width) {
        return Math.max(0f, Math.min(1f, (localX - x) / Math.max(1f, width)));
    }

    public interface KeyframeTime {
        int timeAt(int index);
    }
}
