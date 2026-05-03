package org.arcadia.arc_quest.mutil.theme;

import net.minecraft.client.gui.GuiGraphics;

public final class ArcDynamicScissor {
    private ArcDynamicScissor() {
    }

    public static void enableHorizontalReveal(GuiGraphics graphics, int x, int y, int width, int height, float revealProgress, float wipeProgress, boolean entering, boolean exiting) {
        int left = x - 20;
        int right;
        if (entering) right = x + Math.round(width * clamp01(revealProgress));
        else if (exiting) right = x + Math.round(width * (1f - clamp01(wipeProgress)));
        else right = x + width + 20;
        graphics.enableScissor(left, y - 10, Math.max(left, right), y + height + 20);
    }

    public static void enableVerticalReveal(GuiGraphics graphics, int x, int y, int width, int height, float revealProgress, float wipeProgress, boolean entering, boolean exiting) {
        int top = y;
        int bottom;
        if (entering) bottom = y + Math.round(height * clamp01(revealProgress));
        else if (exiting) bottom = y + Math.round(height * (1f - clamp01(wipeProgress)));
        else bottom = y + height;
        graphics.enableScissor(x, top, x + width, Math.max(top, bottom));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
