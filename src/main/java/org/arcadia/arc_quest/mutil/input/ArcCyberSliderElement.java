package org.arcadia.arc_quest.mutil.input;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;
import org.arcadia.arc_quest.mutil.theme.ArcPanelChrome;

public class ArcCyberSliderElement extends ArcSliderElement {
    public ArcCyberSliderElement(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public static boolean containsLocal(float localX, float localY, int x, int y, int width, int height) {
        return localX >= x && localX <= x + width && localY >= y && localY <= y + height;
    }

    public static int valueFromLocal(float localX, int sliderX, int sliderWidth, int min, int max) {
        if (max <= min) return min;
        float pct = Math.max(0f, Math.min(1f, (localX - sliderX) / Math.max(1f, sliderWidth)));
        return min + Math.round(pct * (max - min));
    }

    public static float targetThumbX(int sliderX, int sliderWidth, int value, int min, int max) {
        if (max <= min) return sliderX + sliderWidth;
        return sliderX + (float) (value - min) / (max - min) * sliderWidth;
    }

    public static float stepVisualThumb(float current, float target, float dt) {
        if (current < 0) return target;
        return current + (target - current) * Math.min(1f, dt * 25f);
    }

    public static void renderCyber(GuiGraphics graphics, int x, int y, int width, int height, float thumbX, boolean dragging, float hoverProgress, int alpha, float alphaF, int themeColor) {
        graphics.fill(x, y, x + width, y + Math.max(1, height), ArcDrawUtil.withAlpha(0xFFFFFF, (int) (30 * alphaF)));
        if (thumbX >= x) {
            graphics.fill(x, y, (int) thumbX, y + Math.max(2, height + 1), ArcDrawUtil.withAlpha(themeColor, alpha));
            int tx = (int) thumbX;
            int thumbColor = dragging ? 0xFFFFFF : HudAnimUtil.lerpColor(themeColor, 0xFFFFFF, hoverProgress);
            graphics.fill(tx - 1, y - 2, tx + 1, y + 3, ArcDrawUtil.withAlpha(thumbColor, alpha));
        }
    }

    public static void renderProgressFrame(GuiGraphics graphics, int x, int y, int width, int height, float progress, int alpha, float alphaF, int themeColor) {
        int fillWidth = Math.max(0, (int) (width * Math.max(0f, Math.min(1f, progress))));
        graphics.fill(x, y, x + width, y + height, ArcDrawUtil.withAlpha(0xFFFFFF, (int) (20 * alphaF)));
        ArcPanelChrome.drawFastFrame(graphics, x - 1, y - 1, width + 2, height + 2, 1, ArcDrawUtil.withAlpha(0xFFFFFF, (int) (40 * alphaF)));
        if (fillWidth > 0) {
            graphics.fill(x, y, x + fillWidth, y + height, ArcDrawUtil.withAlpha(themeColor, alpha));
            graphics.fill(x + fillWidth - 2, y - 2, x + fillWidth + 1, y + height + 2, ArcDrawUtil.withAlpha(0xFFFFFF, alpha));
        }
    }
}
