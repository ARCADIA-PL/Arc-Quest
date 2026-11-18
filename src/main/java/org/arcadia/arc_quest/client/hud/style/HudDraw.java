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

    public static void surface(GuiGraphics g, int x1, int y1, int x2, int y2, int legacy, float opacity, int theme) {
        if (isStyled()) HudStyleRenderer.panel(g, x1, y1, x2 - x1, y2 - y1, opacity, theme);
        else g.fill(x1, y1, x2, y2, legacy);
    }
    /** Background already includes its animation alpha; edge opacity must not replace it. */
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
        // Preserve continuous opacity on scene scrims and scroll-edge fades.
        // Solid Ore surfaces and controls are drawn by their semantic renderers.
        g.fillGradient(x1, y1, x2, y2, ink(top), ink(bottom));
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
