package org.arcadia.arc_quest.mutil.theme;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.core.ArcGuiColor;

public final class ArcPanelChrome {
    private ArcPanelChrome() {
    }

    public static void drawFastFrame(GuiGraphics graphics, int x, int y, int width, int height, int thickness, int color) {
        if (thickness <= 0) return;
        graphics.fill(x, y, x + width, y + thickness, color);
        graphics.fill(x, y + height - thickness, x + width, y + height, color);
        graphics.fill(x, y + thickness, x + thickness, y + height - thickness, color);
        graphics.fill(x + width - thickness, y + thickness, x + width, y + height - thickness, color);
    }

    public static void drawGlassPanel(GuiGraphics graphics, int x, int y, int width, int height, int bgColor, int bgAlpha, int accentColor, int accentAlpha, int accentWidth) {
        graphics.fill(x, y, x + width, y + height, ArcGuiColor.withAlpha(bgColor & 0x00FFFFFF, bgAlpha));
        ArcDrawUtil.drawCyberneticEdge(graphics, x, y, height, accentColor, accentAlpha, accentWidth);
    }

    public static void drawToastPanel(GuiGraphics graphics, int x, int y, int width, int height, int bgColor, int bgAlpha, int accentColor, int accentAlpha, int accentWidth, int lineAlpha) {
        drawGlassPanel(graphics, x, y, width, height, bgColor, bgAlpha, accentColor, accentAlpha, accentWidth);
        if (lineAlpha > 0) {
            graphics.fill(x + accentWidth, y + height - 1, x + width, y + height, ArcGuiColor.withAlpha(accentColor & 0x00FFFFFF, lineAlpha));
        }
    }

    public static void drawCornerBrackets(GuiGraphics graphics, int x, int y, int width, int height, int size, int color) {
        graphics.fill(x, y, x + size, y + 1, color);
        graphics.fill(x, y, x + 1, y + size, color);
        graphics.fill(x + width - size, y, x + width, y + 1, color);
        graphics.fill(x + width - 1, y, x + width, y + size, color);
        graphics.fill(x, y + height - 1, x + size, y + height, color);
        graphics.fill(x, y + height - size, x + 1, y + height, color);
        graphics.fill(x + width - size, y + height - 1, x + width, y + height, color);
        graphics.fill(x + width - 1, y + height - size, x + width, y + height, color);
    }
}
