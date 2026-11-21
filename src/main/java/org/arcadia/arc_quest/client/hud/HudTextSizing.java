package org.arcadia.arc_quest.client.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.style.HudDraw;

public final class HudTextSizing {
    private HudTextSizing() {}

    public static float fitPanel(int width, int height, int panelWidth, int panelHeight,
                                 float widthFraction, float heightFraction, double factor) {
        float base = Math.min(width * widthFraction / panelWidth, height * heightFraction / panelHeight);
        float limit = Math.min(width * .94f / panelWidth, height * .90f / panelHeight);
        return Math.min(base * (float) factor, limit);
    }

    public static void line(GuiGraphics g, Font font, Component text, int x, int y, int width,
                            float scale, int color, boolean centered) {
        line(g, font, text, x, y, width, scale, color, centered, true);
    }

    public static void line(GuiGraphics g, Font font, Component text, int x, int y, int width,
                            float scale, int color, boolean centered, boolean shadow) {
        if (width <= 0 || scale <= 0 || (color >>> 24) <= 3) return;
        var visible = StyledTextUtil.fitSingleLine(font, text, Math.max(1, (int) (width / scale)));
        g.pose().pushPose();
        try {
            g.pose().translate(x, y, 0);
            g.pose().scale(scale, scale, 1);
            HudDraw.drawString(g, font, visible, centered ? -(font.width(visible) + (HudDraw.isOre() && shadow ? 1 : 0)) / 2 : 0, 0, color, shadow);
        } finally {
            g.pose().popPose();
        }
    }
}
