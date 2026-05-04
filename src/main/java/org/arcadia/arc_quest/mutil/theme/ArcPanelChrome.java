package org.arcadia.arc_quest.mutil.theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.core.ArcGuiColor;

public final class ArcPanelChrome {
    public static final int DEFAULT_BORDER_RGB = 0xCCCCCC;
    public static final int DEFAULT_HEADER_RGB = 0x667788;
    public static final int DEFAULT_DIVIDER_INSET = 10;
    public static final int DEFAULT_ACCENT_WIDTH = 3;

    private ArcPanelChrome() {
    }

    public static void drawFastFrame(GuiGraphics graphics, int x, int y, int width, int height, int thickness, int color) {
        if (thickness <= 0) return;
        graphics.fill(x, y, x + width, y + thickness, color);
        graphics.fill(x, y + height - thickness, x + width, y + height, color);
        graphics.fill(x, y + thickness, x + thickness, y + height - thickness, color);
        graphics.fill(x + width - thickness, y + thickness, x + width, y + height - thickness, color);
    }

    public static void drawQuestPanel(GuiGraphics graphics, int x, int y, int width, int height, int backgroundRgb, int backgroundAlpha, int borderRgb, int borderAlpha, int accentColor, int accentAlpha) {
        drawQuestPanel(graphics, x, y, width, height, backgroundRgb, backgroundAlpha, borderRgb, borderAlpha, accentColor, accentAlpha, DEFAULT_ACCENT_WIDTH);
    }

    public static void drawQuestPanel(GuiGraphics graphics, int x, int y, int width, int height, int backgroundRgb, int backgroundAlpha, int borderRgb, int borderAlpha, int accentColor, int accentAlpha, int accentWidth) {
        int safeAccentWidth = Math.max(1, accentWidth);
        graphics.fill(x + safeAccentWidth, y, x + width, y + height, ArcDrawUtil.withAlpha(backgroundRgb, backgroundAlpha));
        ArcDrawUtil.drawThinFrame(graphics, x + safeAccentWidth, y, width - safeAccentWidth, height, ArcDrawUtil.withAlpha(borderRgb, borderAlpha));
        ArcDrawUtil.drawCyberneticEdge(graphics, x, y, height, accentColor, accentAlpha, safeAccentWidth);
    }

    public static void drawTopDivider(GuiGraphics graphics, int panelWidth, int topBarHeight, int borderRgb, int borderAlpha) {
        drawTopDivider(graphics, DEFAULT_DIVIDER_INSET, panelWidth - DEFAULT_DIVIDER_INSET, topBarHeight, borderRgb, borderAlpha);
    }

    public static void drawTopDivider(GuiGraphics graphics, int x1, int x2, int topBarHeight, int borderRgb, int borderAlpha) {
        ArcDrawUtil.drawHorizontalLine(graphics, x1, x2, topBarHeight - 1, ArcDrawUtil.withAlpha(borderRgb, borderAlpha));
    }

    public static void drawHeader(GuiGraphics graphics, Font font, String text, float scale, int x, int y, int color, int alpha) {
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, text, Math.round(x / scale), Math.round(y / scale), ArcDrawUtil.withAlpha(color, alpha), false);
        graphics.pose().popPose();
    }

    public static void drawGrid(GuiGraphics graphics, int x, int y, int width, int height, int spacing, int colorRgb, int alpha) {
        ArcDrawUtil.drawScanlineGrid(graphics, x, y, width, height, spacing, ArcDrawUtil.withAlpha(colorRgb, alpha));
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
