package org.arcadia.arc_quest.client.hud.quest.journal.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;

import java.util.List;

public final class JournalTooltipRenderer {

    private static final int PADDING = 6;
    private static final int CYBER_EDGE_WIDTH = 3;

    private JournalTooltipRenderer() {
    }

    public static Layout measure(Font font, List<Component> lines) {
        List<Component> safeLines = lines == null ? List.of() : List.copyOf(lines);
        int textMaxWidth = 0;
        for (Component line : safeLines) textMaxWidth = Math.max(textMaxWidth, font.width(line));
        int width = textMaxWidth + PADDING * 2 + CYBER_EDGE_WIDTH + 2;
        int height = safeLines.size() * font.lineHeight + PADDING * 2;
        return new Layout(safeLines, width, height);
    }

    public static void renderAtMouse(GuiGraphics gui,
                                     Font font,
                                     List<Component> lines,
                                     int mouseX,
                                     int mouseY,
                                     int screenWidth,
                                     int screenHeight,
                                     int themeColor) {
        Layout layout = measure(font, lines);
        if (layout.lines().isEmpty()) return;

        int x = mouseX + 12;
        int y = mouseY - 12;
        if (x + layout.width() > screenWidth) x = mouseX - layout.width() - 8;
        if (y + layout.height() > screenHeight) y = screenHeight - layout.height() - 2;
        if (y < 0) y = 2;

        drawFrame(gui, x, y, layout.width(), layout.height(), themeColor, 1.0F);
        drawText(gui, font, layout.lines(), x, y, 1.0F);
    }

    public static void drawFrame(GuiGraphics gui,
                                 int x,
                                 int y,
                                 int width,
                                 int height,
                                 int themeColor,
                                 float alpha) {
        int bgAlpha = Math.round(0xD0 * alpha);
        int borderAlpha = Math.round(0x66 * alpha);
        int edgeAlpha = Math.round(255 * alpha);

        gui.fill(x + CYBER_EDGE_WIDTH, y, x + width, y + height,
                HudAnimUtil.withAlpha(0x000000, bgAlpha));
        gui.fill(x + CYBER_EDGE_WIDTH, y, x + width, y + 1,
                HudAnimUtil.withAlpha(0xCCCCCC, borderAlpha));
        gui.fill(x + CYBER_EDGE_WIDTH, y + height - 1, x + width, y + height,
                HudAnimUtil.withAlpha(0xCCCCCC, borderAlpha));
        gui.fill(x + width - 1, y, x + width, y + height,
                HudAnimUtil.withAlpha(0xCCCCCC, borderAlpha));
        HudRenderUtil.drawCyberneticEdge(gui, x, y, height, themeColor, edgeAlpha);
    }

    public static void drawText(GuiGraphics gui,
                                Font font,
                                List<Component> lines,
                                int x,
                                int y,
                                float alpha) {
        int textAlpha = Math.round(255 * alpha);
        int textX = x + CYBER_EDGE_WIDTH + PADDING + 1;
        int textY = y + PADDING;
        for (Component line : lines) {
            gui.drawString(font, line, textX, textY,
                    HudAnimUtil.withAlpha(0xFFFFFF, textAlpha), true);
            textY += font.lineHeight;
        }
    }

    public record Layout(List<Component> lines, int width, int height) {
    }
}
