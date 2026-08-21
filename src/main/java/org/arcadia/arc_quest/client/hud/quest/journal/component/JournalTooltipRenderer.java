package org.arcadia.arc_quest.client.hud.quest.journal.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;

import java.util.List;

public final class JournalTooltipRenderer {

    private static final int PADDING = 6;
    private static final int CYBER_EDGE_WIDTH = 3;
    private static final int ITEM_ICON_SIZE = 16;
    private static final int ITEM_ICON_GAP = 5;

    private JournalTooltipRenderer() {
    }

    public static Layout measure(Font font, List<Component> lines) {
        return measure(font, lines, false);
    }

    public static Layout measureWithItemIcon(Font font, List<Component> lines) {
        return measure(font, lines, true);
    }

    private static Layout measure(Font font, List<Component> lines, boolean withItemIcon) {
        List<Component> safeLines = lines == null ? List.of() : List.copyOf(lines);
        int textMaxWidth = 0;
        for (Component line : safeLines) textMaxWidth = Math.max(textMaxWidth, font.width(line));
        int textHeight = safeLines.size() * font.lineHeight;
        int contentHeight = withItemIcon ? Math.max(textHeight, ITEM_ICON_SIZE) : textHeight;
        int textOffsetX = withItemIcon ? ITEM_ICON_SIZE + ITEM_ICON_GAP : 0;
        int width = textMaxWidth + textOffsetX + PADDING * 2 + CYBER_EDGE_WIDTH + 2;
        int height = contentHeight + PADDING * 2;
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
        drawText(gui, font, lines, x, y, alpha, 0, 0);
    }

    public static void drawItemTooltipText(GuiGraphics gui,
                                           Font font,
                                           List<Component> lines,
                                           int x,
                                           int y,
                                           float alpha) {
        int textHeight = lines.size() * font.lineHeight;
        int contentHeight = Math.max(textHeight, ITEM_ICON_SIZE);
        drawText(gui, font, lines, x, y, alpha, ITEM_ICON_SIZE + ITEM_ICON_GAP,
                Math.max(0, (contentHeight - textHeight) / 2));
    }

    private static void drawText(GuiGraphics gui,
                                 Font font,
                                 List<Component> lines,
                                 int x,
                                 int y,
                                 float alpha,
                                 int textOffsetX,
                                 int textOffsetY) {
        int textAlpha = Math.round(255 * alpha);
        int textX = x + CYBER_EDGE_WIDTH + PADDING + 1 + textOffsetX;
        int textY = y + PADDING + textOffsetY;
        for (Component line : lines) {
            gui.drawString(font, line, textX, textY,
                    HudAnimUtil.withAlpha(0xFFFFFF, textAlpha), true);
            textY += font.lineHeight;
        }
    }

    public static void drawItemIcon(GuiGraphics gui, ItemStack stack, Layout layout, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        int iconX = x + CYBER_EDGE_WIDTH + PADDING + 1;
        int iconY = y + Math.max(0, (layout.height() - ITEM_ICON_SIZE) / 2);
        gui.renderFakeItem(stack, iconX, iconY);
    }

    public record Layout(List<Component> lines, int width, int height) {
    }
}
