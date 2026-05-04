package org.arcadia.arc_quest.mutil.input;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;

public class ArcHologramButtonElement extends ArcButtonElement {
    public ArcHologramButtonElement(int x, int y, int width, int height, Runnable onClick) {
        super(x, y, width, height, onClick);
    }

    public static void renderHologram(GuiGraphics graphics, Font font, int x, int y, int width, int height, String text, float hoverProgress, int alpha, int baseColor, int themeColor) {
        int currentColor = interpolateColor(baseColor, themeColor, hoverProgress);
        graphics.pose().pushPose();
        float baseTextScale = text.length() == 1 ? 1.4f : 1.2f;
        float textScale = baseTextScale + (0.35f * hoverProgress);
        graphics.pose().translate(x + width / 2f, y + height / 2f - (font.lineHeight * textScale) / 2f + 1, 0);
        graphics.pose().scale(textScale, textScale, 1f);
        graphics.drawCenteredString(font, text, 0, 0, ArcDrawUtil.withAlpha(currentColor, alpha));
        graphics.pose().popPose();
    }

    private static int interpolateColor(int c1, int c2, float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;
        int r = (int) (r1 + (r2 - r1) * clamped);
        int g = (int) (g1 + (g2 - g1) * clamped);
        int b = (int) (b1 + (b2 - b1) * clamped);
        return (r << 16) | (g << 8) | b;
    }
}
