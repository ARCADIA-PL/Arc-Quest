package org.arcadia.arc_quest.mutil.theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.core.ArcGuiColor;

import java.util.ArrayList;
import java.util.List;

public final class ArcDrawUtil {
    private ArcDrawUtil() {
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
