package org.arcadia.arc_quest.client.config;

import org.arcadia.arc_quest.client.hud.style.HudStyleRenderer;
import org.arcadia.arc_quest.client.hud.style.HudDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalTabStrip;

import java.util.List;

final class ArcQuestTopBarButtonRenderer {
    static final int Y = 7;
    static final int MARGIN = 9;
    static final int GAP = 7;
    static final int HEIGHT = 18;
    static final int ICON_WIDTH = 28;
    private static final int HORIZONTAL_PADDING = 8;

    private ArcQuestTopBarButtonRenderer() {
    }

    static int width(Font font, Component text) {
        return Math.max(28, font.width(text) + HORIZONTAL_PADDING * 2);
    }

    static Bounds bounds(Font font, Component text, int x) {
        return new Bounds(x, Y, width(font, text), HEIGHT);
    }

    enum Icon {
        SETTINGS("...###...", ".#.###.#.", "#########", ".##...##.", "###...###",
                ".##...##.", "#########", ".#.###.#.", "...###..."),
        TEXT_SIZE("..##.........", ".#..#........", "#....#..###..", "#....#.....#.",
                "######..####.", "#....#.#...#.", "#....#..####."),
        TRACKER("##...##########", "##.............", "##.............", "##.............",
                "##...########..", "##.............", "##.............", "##.............", "##...#####.....");

        final String[] pixels;
        Icon(String... pixels) { this.pixels = pixels; }
    }

    static HudRect iconBounds(int x) {
        return new HudRect(x, Y, ICON_WIDTH, HEIGHT);
    }

    static void renderJournalIcon(QuestJournalScreen screen, GuiGraphics g, Icon icon, Component tooltip,
                                  int x, int mouseX, int mouseY, int theme, float opacity, boolean emphasized) {
        if (drawIcon(g, icon, x, mouseX, mouseY, theme, opacity, emphasized)) {
            screen.requestPointerCursor();
            screen.setHoveredCustomTooltip(List.of(tooltip));
        }
    }

    static boolean drawIcon(GuiGraphics g, Icon icon, int x, int mouseX, int mouseY,
                            int theme, float opacity, boolean emphasized) {
        HudRect bounds = iconBounds(x);
        return drawIcon(g, icon, bounds, bounds.contains(mouseX, mouseY), theme, opacity, emphasized);
    }

    static boolean drawIcon(GuiGraphics g, Icon icon, HudRect bounds, boolean hovered, int theme,
                            float opacity, boolean emphasized) {
        int alpha = fade(0xFFFFFFFF, opacity) >>> 24;
        if (alpha <= 8) return false;
        JournalTabStrip.drawTabFace(g, bounds, true, hovered, alpha, theme);
        int color = fade(0xFFFFFFFF, opacity * (emphasized || hovered ? 1f : .62f));
        int startX = bounds.x() + (bounds.width() - icon.pixels[0].length()) / 2;
        int startY = JournalTabStrip.contentTop(bounds, icon.pixels.length, false);
        for (int row = 0; row < icon.pixels.length; row++) {
            String pixels = icon.pixels[row];
            for (int col = 0; col < pixels.length(); col++) {
                if (pixels.charAt(col) == '#') HudDraw.fill(g, startX + col, startY + row, startX + col + 1, startY + row + 1, color);
            }
        }
        return hovered;
    }

    static void render(GuiGraphics graphics, Font font, Component text, int x,
                       int mouseX, int mouseY, int accentColor, int textColor,
                       boolean emphasized) {
        render(graphics, font, text, x, mouseX, mouseY, accentColor, textColor, emphasized, 1f);
    }

    static void render(GuiGraphics graphics, Font font, Component text, int x,
                       int mouseX, int mouseY, int accentColor, int textColor,
                       boolean emphasized, float opacity) {
        if (!Float.isFinite(opacity) || opacity <= 3f / 255f) return;
        Bounds bounds = bounds(font, text, x);
        boolean hovered = bounds.contains(mouseX, mouseY);
        if (HudDraw.isStyled()) {
            HudStyleRenderer.labeledButton(graphics, font, text, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    HudStyleRenderer.Action.SECONDARY, hovered, true, opacity, accentColor);
            return;
        }
        if (hovered || emphasized) {
            HudDraw.fill(graphics, bounds.x(), bounds.y() + 2,
                    bounds.x() + bounds.width(), bounds.y() + bounds.height() - 2,
                    fade(withAlpha(accentColor, hovered ? 20 : 12), opacity));
        }
        int textX = bounds.x() + HORIZONTAL_PADDING + (hovered ? 2 : 0);
        int textY = bounds.y() + (bounds.height() - font.lineHeight) / 2;
        int ink = fade(hovered ? 0xFFFFFFFF : textColor, opacity);
        // Minecraft treats text alpha 0..3 as an omitted alpha and makes the glyph opaque.
        if ((ink >>> 24) > 3) HudDraw.drawString(graphics, font, text, textX, textY, ink, true);
        HudDraw.fill(graphics, bounds.x(), bounds.y() + bounds.height() - 2,
                bounds.x() + bounds.width(), bounds.y() + bounds.height(),
                fade(withAlpha(accentColor, hovered || emphasized ? 255 : 205), opacity));
    }

    static int fade(int color, float opacity) {
        float normalized = Float.isFinite(opacity) ? Math.max(0, Math.min(1, opacity)) : 0;
        return withAlpha(color, Math.round((color >>> 24) * normalized));
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }

    record Bounds(int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width
                    && mouseY >= y && mouseY < y + height;
        }
    }
}
