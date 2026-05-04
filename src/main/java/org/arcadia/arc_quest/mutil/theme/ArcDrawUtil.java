package org.arcadia.arc_quest.mutil.theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.core.ArcGuiColor;

import java.util.ArrayList;
import java.util.List;

public final class ArcDrawUtil {
    private ArcDrawUtil() {
    }

    public static int clampAlpha(int alpha) {
        return Math.max(0, Math.min(255, alpha));
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (clampAlpha(alpha) << 24);
    }

    public static void drawDualText(GuiGraphics graphics, Font font, float x, float y, String subtitle, String title, int subtitleColor, int titleColor, float alpha) {
        int subAlpha = (int) (((subtitleColor >> 24) & 0xFF) * alpha);
        int titleAlpha = (int) (((titleColor >> 24) & 0xFF) * alpha);
        if (subAlpha < 5 && titleAlpha < 5) return;
        if (subAlpha > 5) {
            graphics.pose().pushPose();
            graphics.pose().translate(Math.round(x), Math.round(y), 0);
            graphics.pose().scale(0.7f, 0.7f, 1f);
            graphics.drawString(font, subtitle, 0, 0, withAlpha(subtitleColor, subAlpha), true);
            graphics.pose().popPose();
        }
        if (titleAlpha > 5) {
            graphics.pose().pushPose();
            graphics.pose().translate(Math.round(x), Math.round(y + 10), 0);
            graphics.drawString(font, title, 0, 0, withAlpha(titleColor, titleAlpha), true);
            graphics.pose().popPose();
        }
    }

    public static int lerpColor(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a1 = (c1 >> 24) & 0xFF;
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF;
        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;
        int a = a1 + (int) ((a2 - a1) * t);
        int r = r1 + (int) ((r2 - r1) * t);
        int g = g1 + (int) ((g2 - g1) * t);
        int b = b1 + (int) ((b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void drawFrame(GuiGraphics graphics, int x, int y, int width, int height, int backgroundColor, int borderColor) {
        int right = x + width;
        int bottom = y + height;
        graphics.fill(x, y, right, bottom, backgroundColor);
        graphics.fill(x - 1, y - 1, right + 1, y, borderColor);
        graphics.fill(x - 1, bottom, right + 1, bottom + 1, borderColor);
        graphics.fill(x - 1, y, x, bottom, borderColor);
        graphics.fill(right, y, right + 1, bottom, borderColor);
    }

    public static void drawThinFrame(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public static void drawHorizontalLine(GuiGraphics graphics, int x1, int x2, int y, int color) {
        if (x2 <= x1) return;
        graphics.fill(x1, y, x2, y + 1, color);
    }

    public static void fillFullscreenDim(GuiGraphics graphics, int screenWidth, int screenHeight, int alpha) {
        if (alpha <= 0) return;
        graphics.fill(-1000, -1000, screenWidth + 1000, screenHeight + 1000, withAlpha(0x000000, alpha));
    }

    public static void drawScanlineGrid(GuiGraphics graphics, int x, int y, int width, int height, int spacing, int color) {
        if (spacing <= 0 || width <= 0 || height <= 0) return;
        for (int ix = x + spacing; ix < x + width; ix += spacing) {
            graphics.fill(ix, y, ix + 1, y + height, color);
        }
        for (int iy = y + spacing; iy < y + height; iy += spacing) {
            graphics.fill(x, iy, x + width, iy + 1, color);
        }
    }

    public static void drawAccentPanel(GuiGraphics graphics, int x, int y, int width, int height, int backgroundColor, int accentColor, int accentWidth) {
        graphics.fill(x, y, x + width, y + height, backgroundColor);
        drawCyberneticEdge(graphics, x, y, height, accentColor, 255, accentWidth);
    }

    public static void drawCyberneticEdge(GuiGraphics graphics, int x, int y, int height, int themeColor, int alpha, int accentWidth) {
        if (alpha < 5) return;
        int coreColor = themeColor & 0xFFFFFF;
        int topAlpha = alpha;
        int bottomAlpha = (int) (alpha * 0.15f);
        int colorTop = coreColor | (topAlpha << 24);
        int colorBottom = coreColor | (bottomAlpha << 24);
        graphics.fillGradient(x, y, x + Math.max(1, accentWidth), y + height, colorTop, colorBottom);
        int glowAlpha = (int) (topAlpha * 0.8f);
        int colorGlow = 0xFFFFFF | (glowAlpha << 24);
        graphics.fillGradient(x, y, x + 1, y + (height / 2), colorGlow, colorTop);
    }

    public static void drawProgressBar(GuiGraphics graphics, int x, int y, int width, int height, float progress, int bgColor, int fillColor) {
        graphics.fill(x, y, x + width, y + height, bgColor);
        int fillWidth = Math.round(width * Math.max(0f, Math.min(1f, progress)));
        if (fillWidth > 0) {
            graphics.fill(x, y, x + fillWidth, y + height, fillColor);
        }
    }

    public static void drawTextWithLine(GuiGraphics graphics, Font font, float x, float y, String text, int color, float lineWidth, float lineYOffset) {
        int alpha = (color >>> 24) & 0xFF;
        if (alpha < 5) return;
        graphics.drawString(font, text, Math.round(x), Math.round(y), color, true);
        if (lineWidth > 2) {
            int lineY = Math.round(y + lineYOffset);
            int lineColor = ArcGuiColor.withAlpha(color & 0x00FFFFFF, alpha);
            graphics.fill(Math.round(x), lineY, Math.round(x + lineWidth), lineY + 1, lineColor);
        }
    }

    public static List<String> wrapText(String text, int maxWidth, Font font) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        String[] paragraphs = text.split("\\n");
        for (String paragraph : paragraphs) {
            String[] words = paragraph.split(" ");
            StringBuilder current = new StringBuilder();
            for (String word : words) {
                String test = current.isEmpty() ? word : current + " " + word;
                if (font.width(test) > maxWidth && !current.isEmpty()) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    if (!current.isEmpty()) current.append(' ');
                    current.append(word);
                }
            }
            if (!current.isEmpty()) lines.add(current.toString());
        }
        return lines;
    }
}
