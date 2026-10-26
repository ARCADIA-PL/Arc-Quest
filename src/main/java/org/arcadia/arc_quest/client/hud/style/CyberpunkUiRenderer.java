package org.arcadia.arc_quest.client.hud.style;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.config.HudArtStyle;

import static org.arcadia.arc_quest.client.hud.style.HudStylePalette.*;

/** Native adaptation of alddesign/cyberpunk-css cutouts, notches and offset accent edges (MIT). */
public final class CyberpunkUiRenderer {
    private CyberpunkUiRenderer() {}

    public static void panel(GuiGraphics g, int x, int y, int w, int h, float alpha, int theme) {
        card(g, x, y, w, h, false, false, alpha, theme);
    }
    public static void glassPanel(GuiGraphics g, int x, int y, int w, int h, int background, float alpha, int theme) {
        if (w <= 0 || h <= 0) return;
        fillCut(g, x, y, w, h, glass(background, theme));
        outline(g, x, y, w, h, alpha * .85f, theme);
    }
    public static void card(GuiGraphics g, int x, int y, int w, int h, boolean selected, boolean hovered, float alpha, int theme) {
        if (w <= 0 || h <= 0 || unit(alpha) == 0) return;
        fillCut(g, x, y, w, h, argb(surface(HudArtStyle.CYBERPUNK, theme, selected, hovered), alpha));
        outline(g, x, y, w, h, alpha * (selected || hovered ? 1 : .55f), theme);
        if ((selected || hovered) && w > 12 && h > 10)
            g.fill(x + 3, y + 3, x + 5, y + h - cut(w, h) - 1, argb(theme, alpha));
    }
    public static void outline(GuiGraphics g, int x, int y, int w, int h, float alpha, int theme) {
        if (w <= 0 || h <= 0 || unit(alpha) == 0) return;
        int cut = cut(w, h), edge = argb(theme, alpha);
        if (cut == 0) { g.renderOutline(x, y, w, h, edge); return; }
        int notchStart = w * 4 / 5, notchEnd = w * 19 / 20;
        g.fill(x, y, x + w - cut, y + 1, edge);
        g.fill(x, y, x + 1, y + h - cut, edge);
        g.fill(x + w - 1, y + cut, x + w, y + h, edge);
        g.fill(x + cut, y + h - 1, x + notchStart, y + h, edge);
        g.fill(x + notchEnd, y + h - 1, x + w, y + h, edge);
        g.fill(x + notchStart, y + h - 3, x + notchEnd, y + h - 2, edge);
        g.fill(x + notchStart - 1, y + h - 3, x + notchStart, y + h, edge);
        g.fill(x + notchEnd, y + h - 3, x + notchEnd + 1, y + h, edge);
        for (int i = 0; i < cut; i++) {
            g.fill(x + w - cut + i - 1, y + i, x + w - cut + i + 1, y + i + 1, edge);
            g.fill(x + i, y + h - cut + i, x + i + 2, y + h - cut + i + 1, edge);
        }
        if (w > 60 && h >= 20) {
            g.fill(x + 7, y + 2, x + Math.min(w / 3, 30), y + 3, edge);
            // Two restrained signal ticks echo the CSS offset shadow without flashing text.
            g.fill(x + w - 4, y + cut + 3, x + w - 2, y + cut + 6, edge);
            g.fill(x + w - 4, y + cut + 8, x + w - 2, y + cut + 10, argb(theme, alpha * .45f));
        }
    }
    public static void field(GuiGraphics g, int x, int y, int w, int h, boolean focused, float alpha, int theme) {
        if (w <= 0 || h <= 0 || unit(alpha) == 0) return;
        fillCut(g, x, y, w, h, argb(HudStylePalette.field(HudArtStyle.CYBERPUNK, theme), alpha));
        outline(g, x, y, w, h, alpha * (focused ? 1 : .45f), theme);
        if (focused && w > 16 && h > 8) g.fill(x + 6, y + h - 4, x + w * 3 / 4, y + h - 3, argb(theme, alpha));
    }
    public static void button(GuiGraphics g, int x, int y, int w, int h, HudStyleRenderer.Action action,
                               boolean hovered, boolean enabled, boolean pressed, float alpha, int theme) {
        int accent = enabled ? theme : 0x707781;
        int face = !enabled ? 0x24272C : action == HudStyleRenderer.Action.SECONDARY
                ? surface(HudArtStyle.CYBERPUNK, theme, pressed, hovered) : actionFace(theme, hovered, pressed);
        fillCut(g, x, y, w, h, argb(face, alpha));
        outline(g, x, y, w, h, alpha * (hovered && enabled ? 1 : .75f), accent);
        if (hovered && enabled && w > 18 && h > 12)
            g.fill(x + w - 3, y + cut(w, h) + 1, x + w - 1, y + h - 4, argb(accent, alpha));
    }
    public static void progress(GuiGraphics g, int x, int y, int w, int h, float fraction, float alpha, int theme) {
        if (w <= 0 || h <= 0 || unit(alpha) == 0) return;
        g.fill(x, y, x + w, y + h, argb(tint(0x090D14, theme, .15f), alpha));
        int filled = Math.round(w * unit(fraction));
        if (filled <= 0) return;
        g.fill(x, y, x + filled, y + h, argb(shade(theme, .60f), alpha));
        g.fill(x, y, x + filled, y + 1, argb(theme, alpha));
        if (h >= 3) for (int dx = 9; dx < filled - 1; dx += 12)
            g.fill(x + dx, y + 1, x + dx + 1, y + h, argb(0x0A1018, alpha));
    }
    public static void marker(GuiGraphics g, int accent, float tier, boolean pointer) {
        int radius = pointer ? 7 : Math.round(12 - Math.max(0, Math.min(2, tier)) * 3);
        float alpha = (accent >>> 24) / 255f;
        for (int row = -radius; row <= (pointer ? 0 : radius); row++) {
            int half = pointer ? row + radius + 1 : radius - Math.abs(row) + 1;
            int edge = argb(accent, alpha);
            g.fill(-half, row, -half + Math.min(2, half), row + 1, edge);
            g.fill(half - Math.min(2, half) + 1, row, half + 1, row + 1, edge);
        }
        if (!pointer) {
            g.fill(-1, -3, 2, 4, argb(accent, alpha));
            g.fill(-3, -1, 4, 2, argb(accent, alpha));
        }
    }
    private static int cut(int w, int h) { return w < 16 || h < 8 ? 0 : Math.min(6, Math.min(w / 5, h / 3)); }
    /** Disjoint bands keep the translucent body at exactly the supplied opacity. */
    private static void fillCut(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        int cut = cut(w, h);
        if (cut == 0) { g.fill(x, y, x + w, y + h, color); return; }
        g.fill(x, y + cut, x + w, y + h - cut, color);
        for (int i = 0; i < cut; i++) {
            g.fill(x, y + i, x + w - cut + i, y + i + 1, color);
            int left = x + i, row = y + h - cut + i;
            if (row >= y + h - 2) {
                g.fill(left, row, x + w * 4 / 5, row + 1, color);
                g.fill(x + w * 19 / 20, row, x + w, row + 1, color);
            } else g.fill(left, row, x + w, row + 1, color);
        }
    }
}
