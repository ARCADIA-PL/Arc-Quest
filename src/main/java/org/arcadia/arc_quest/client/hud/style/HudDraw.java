package org.arcadia.arc_quest.client.hud.style;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.config.ArcQuestConfig;
import org.arcadia.arc_quest.config.HudArtStyle;

/** Explicit ArcQ-only drawing boundary. Never intercepts Minecraft, JEI, textures or item passes. */
public final class HudDraw {
    private HudDraw() {}
    public static boolean isOre() { return ArcQuestConfig.hudArtStyle() == HudArtStyle.ORE; }
    public static boolean isStyled() { return ArcQuestConfig.hudArtStyle() != HudArtStyle.DEFAULT; }
    public static int ink(int color) { return isOre() ? OrePalette.decoration(color) : color; }
    public static int textInk(int color) { return isOre() ? OrePalette.text(color) : color; }

    /** Uses the existing scroll geometry and draws the Ore skin when selected. */
    public static boolean oreScrollbar(GuiGraphics g, int x, int y, int width, int height,
                                       int thumbY, int thumbHeight, boolean dragging, float alpha, int theme) {
        if (!isOre()) return false;
        OreUiRenderer.scrollbar(g, x, y, width, height, thumbY - y, thumbHeight, false, dragging, alpha, theme);
        return true;
    }

    public static void surface(GuiGraphics g, int x1, int y1, int x2, int y2, int legacy, float opacity, int theme) {
        if (isStyled()) HudStyleRenderer.panel(g, x1, y1, x2 - x1, y2 - y1, opacity, theme);
        else g.fill(x1, y1, x2, y2, legacy);
    }
    /** Background includes its legacy alpha. Ore uses the animation opacity for its solid material. */
    public static void glassSurface(GuiGraphics g, int x1, int y1, int x2, int y2, int background, float edgeOpacity, int theme) {
        if (isStyled()) HudStyleRenderer.glassPanel(g, x1, y1, x2 - x1, y2 - y1, background, edgeOpacity, theme);
        else g.fill(x1, y1, x2, y2, background);
    }
    public static void glassFrame(GuiGraphics g, int x, int y, int w, int h, int background, int border, float edgeOpacity, int theme) {
        if (isStyled()) HudStyleRenderer.glassPanel(g, x, y, w, h, background, edgeOpacity, theme);
        else org.arcadia.arc_quest.client.hud.HudAnimUtil.drawFrame(g, x, y, w, h, background, border);
    }
    public static void frame(GuiGraphics g, int x, int y, int w, int h, int background, int border, float opacity) {
        if (isStyled()) HudStyleRenderer.panel(g, x, y, w, h, opacity, border);
        else org.arcadia.arc_quest.client.hud.HudAnimUtil.drawFrame(g, x, y, w, h, background, border);
    }

    public static void fill(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y2, ink(color));
    }
    public static void fill(GuiGraphics g, int x1, int y1, int x2, int y2, int z, int color) {
        g.fill(x1, y1, x2, y2, z, ink(color));
    }
    public static void fillGradient(GuiGraphics g, int x1, int y1, int x2, int y2, int top, int bottom) {
        if (!isOre()) { g.fillGradient(x1, y1, x2, y2, top, bottom); return; }
        // Ore trim uses discrete bands instead of the default glow.
        int mid = y1 + (y2 - y1) / 2;
        if (mid > y1 && (top >>> 24) != 0) g.fill(x1, y1, x2, mid, OrePalette.neutral(top));
        if (y2 > mid && (bottom >>> 24) != 0) g.fill(x1, mid, x2, y2, OrePalette.neutral(bottom));
    }
    public static void renderOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.renderOutline(x, y, w, h, ink(color));
    }
    public static int drawString(GuiGraphics g, Font font, String text, int x, int y, int color, boolean shadow) {
        return g.drawString(font, text, x, y, textInk(color), shadow);
    }
    public static int drawString(GuiGraphics g, Font font, String text, float x, float y, int color, boolean shadow) {
        return g.drawString(font, text, x, y, textInk(color), shadow);
    }
    public static int drawString(GuiGraphics g, Font font, Component text, int x, int y, int color, boolean shadow) {
        return g.drawString(font, text, x, y, textInk(color), shadow);
    }
    public static int drawString(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        return g.drawString(font, text, x, y, textInk(color), shadow);
    }
    public static int drawString(GuiGraphics g, Font font, String text, int x, int y, int color) {
        return drawString(g, font, text, x, y, color, true);
    }
    public static int drawString(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        return drawString(g, font, text, x, y, color, true);
    }
    public static int drawString(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color) {
        return drawString(g, font, text, x, y, color, true);
    }
    public static void drawCenteredString(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.drawCenteredString(font, text, x, y, textInk(color));
    }
    public static void drawCenteredString(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        g.drawCenteredString(font, text, x, y, textInk(color));
    }
    public static void drawCenteredString(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color) {
        g.drawCenteredString(font, text, x, y, textInk(color));
    }
}
