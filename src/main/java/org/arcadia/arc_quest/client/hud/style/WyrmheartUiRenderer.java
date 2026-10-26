package org.arcadia.arc_quest.client.hud.style;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.config.HudArtStyle;

import static org.arcadia.arc_quest.client.hud.style.HudStylePalette.*;

/** Wyrmheart's CC0 engraved metalwork; native theme-aware controls and single-pass dark glass. */
public final class WyrmheartUiRenderer {
    private static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath("arc_quest", "textures/gui/style/wyrmheart.png");
    private WyrmheartUiRenderer() {}

    public static void panel(GuiGraphics g, int x, int y, int w, int h, float alpha, int theme) {
        card(g, x, y, w, h, false, false, alpha, theme);
    }

    public static void glassPanel(GuiGraphics g, int x, int y, int w, int h, int background, float alpha, int theme) {
        if (w <= 0 || h <= 0) return;
        // Background already contains animation opacity. Never add an opaque backing or a second body pass.
        if ((background >>> 24) != 0) g.fill(x, y, x + w, y + h, glass(background, theme));
        outline(g, x, y, w, h, alpha, theme);
    }

    public static void card(GuiGraphics g, int x, int y, int w, int h, boolean selected, boolean hovered, float alpha, int theme) {
        if (w <= 0 || h <= 0 || unit(alpha) == 0) return;
        g.fill(x, y, x + w, y + h, argb(surface(HudArtStyle.WYRMHEART, theme, selected, hovered), alpha));
        outline(g, x, y, w, h, alpha * (selected || hovered ? 1 : .82f), theme);
        if ((selected || hovered) && w > 12 && h > 8) {
            g.fill(x + 5, y + 3, x + w - 5, y + 4, argb(theme, alpha));
            if (selected) g.fill(x + 3, y + 5, x + 4, y + h - 5, argb(theme, alpha * .85f));
        }
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, float alpha, int theme) {
        if (w <= 0 || h <= 0 || unit(alpha) == 0) return;
        if (w < 8 || h < 8) { g.renderOutline(x, y, w, h, argb(theme, alpha)); return; }
        if (w <= 48 && h <= 48 && Math.abs(w - h) <= 4) {
            texture(g, mix(0xE6DCCB, theme, .30f), alpha, () -> patch(g, x, y, w, h, 192, 80, 40, 40));
            return;
        }
        int edge = h >= 48 && w >= 96 ? 4 : 2;
        texture(g, mix(0xE6DCCB, theme, .30f), alpha, () -> {
            patch(g, x, y, w, edge, 0, 0, 64, 8);
            patch(g, x, y + h - edge, w, edge, 0, 8, 64, 8);
            patch(g, x, y + edge, edge, h - edge * 2, 64, 0, 8, 64);
            patch(g, x + w - edge, y + edge, edge, h - edge * 2, 72, 0, 8, 64);
            if (w >= 96 && h >= 48) {
                int size = Math.min(24, h / 3);
                patch(g, x, y, size, size, 0, 80, 48, 48);
                patch(g, x + w - size, y, size, size, 48, 80, 48, 48);
                patch(g, x, y + h - size, size, size, 96, 80, 48, 48);
                patch(g, x + w - size, y + h - size, size, size, 144, 80, 48, 48);
            }
        });
        // Thin colored enamel complements the neutral aged metal; the source's red is removed.
        if (w > 24 && h > 8) {
            int inset = w >= 96 && h >= 48 ? Math.min(24, h / 3) : 7;
            g.fill(x + inset, y + edge - 1, x + w - inset, y + edge, argb(theme, alpha * .65f));
            g.fill(x + inset, y + h - edge, x + w - inset, y + h - edge + 1, argb(theme, alpha * .45f));
        }
    }

    public static void field(GuiGraphics g, int x, int y, int w, int h, boolean focused, float alpha, int theme) {
        if (w <= 0 || h <= 0 || unit(alpha) == 0) return;
        g.fill(x, y, x + w, y + h, argb(HudStylePalette.field(HudArtStyle.WYRMHEART, theme), alpha));
        if (w < 12 || h < 8) { outline(g, x, y, w, h, alpha, theme); return; }
        int edge = 3;
        texture(g, mix(0xE6DCCB, theme, focused ? .40f : .20f), alpha * (focused ? 1 : .70f), () -> {
            int[] offsets = {0, edge, w - edge}, widths = {edge, w - edge * 2, edge};
            int[] rows = {0, edge, h - edge}, heights = {edge, h - edge * 2, edge};
            int[] uv = {0, 8, 40}, sizes = {8, 32, 8};
            for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
                if (row == 1 && col == 1) continue;
                patch(g, x + offsets[col], y + rows[row], widths[col], heights[row],
                        128 + uv[col], uv[row], sizes[col], sizes[row]);
            }
        });
        if (focused) g.fill(x + 4, y + h - 3, x + w - 4, y + h - 2, argb(theme, alpha));
    }

    public static void button(GuiGraphics g, int x, int y, int w, int h, HudStyleRenderer.Action action,
                              boolean hovered, boolean enabled, boolean pressed, float alpha, int theme) {
        if (w <= 0 || h <= 0 || unit(alpha) == 0) return;
        boolean primary = enabled && action != HudStyleRenderer.Action.SECONDARY;
        int face = !enabled ? 0x242321 : primary ? actionFace(theme, hovered, pressed)
                : surface(HudArtStyle.WYRMHEART, theme, pressed, hovered);
        int accent = enabled ? theme : 0x777777;
        g.fill(x, y, x + w, y + h, argb(face, alpha));
        if (w < 24 || h < 10) { outline(g, x, y, w, h, alpha, accent); return; }
        // Original bevelled button face, neutralized and tinted; texture pixels never exceed face luminance.
        if (primary) texture(g, face, alpha, () -> buttonPatches(g, x, y, w, h, 144));
        texture(g, mix(0xE6DCCB, accent, hovered && enabled ? .40f : .22f), alpha * (enabled ? 1 : .55f),
                () -> buttonPatches(g, x, y, w, h, 176));
        if (enabled) {
            g.fill(x + 9, y + 1, x + w - 9, y + 2, argb(accent, alpha * (hovered ? 1 : .65f)));
            g.fill(x + 9, y + h - 2, x + w - 9, y + h - 1, argb(accent, alpha * (pressed ? .9f : .35f)));
        }
    }

    private static void buttonPatches(GuiGraphics g, int x, int y, int w, int h, int v) {
        // Fixed end caps keep the original chamfers and ornamental points at every button width.
        int cap = Math.min(9, w / 3);
        patch(g, x, y, cap, h, 0, v, 28, 28);
        patch(g, x + cap, y, w - cap * 2, h, 28, v, 112, 28);
        patch(g, x + w - cap, y, cap, h, 140, v, 28, 28);
    }

    public static void progress(GuiGraphics g, int x, int y, int w, int h, float fraction, float alpha, int theme) {
        if (w <= 0 || h <= 0 || unit(alpha) == 0) return;
        g.fill(x, y, x + w, y + h, argb(tint(0x151411, theme, .12f), alpha));
        int filled = Math.round(w * unit(fraction));
        if (filled > 0) {
            g.fillGradient(x, y, x + filled, y + h, argb(shade(theme, .85f), alpha), argb(shade(theme, .32f), alpha));
            g.fill(x, y, x + filled, y + 1, argb(theme, alpha));
        }
        if (h >= 6) {
            g.fill(x, y + h - 1, x + w, y + h, argb(mix(0x96846A, theme, .3f), alpha));
            if (filled > 2 && filled < w) g.fill(x + filled - 1, y + 1, x + filled, y + h - 1, argb(theme, alpha));
        }
    }

    public static void marker(GuiGraphics g, int accent, float tier, boolean pointer) {
        float alpha = (accent >>> 24) / 255f;
        if (unit(alpha) == 0) return;
        int radius = pointer ? 7 : Math.round(12 - unit(tier / 2) * 6);
        if (pointer) {
            for (int row = -radius; row <= 0; row++) {
                int half = row + radius;
                g.fill(-half, row, half + 1, row + 1, argb(mix(0xB8A485, accent, .4f), alpha));
                if (half > 2) g.fill(-half + 2, row, half - 1, row + 1, argb(shade(accent, .65f), alpha));
            }
            return;
        }
        // A small round medallion replaces the reference's baked inventory icon with the live quest color.
        for (int row = -radius + 1; row < radius; row++) {
            int half = (int) Math.sqrt((radius - 1) * (radius - 1) - row * row);
            g.fill(-half, row, half + 1, row + 1, argb(shade(accent, row < 0 ? .45f : .22f), alpha));
        }
        texture(g, mix(0xF2DFBE, accent, .25f), alpha,
                () -> patch(g, -radius, -radius, radius * 2 + 1, radius * 2 + 1, 192, 0, 64, 64));
        for (int row = -3; row <= 3; row++) {
            int half = 3 - Math.abs(row);
            g.fill(-half, row, half + 1, row + 1, accent);
        }
    }

    private static void texture(GuiGraphics g, int color, float alpha, Runnable draw) {
        g.flush(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, unit(alpha));
        try { draw.run(); }
        finally { RenderSystem.setShaderColor(1, 1, 1, 1); }
    }

    private static void patch(GuiGraphics g, int x, int y, int w, int h, int u, int v, int sw, int sh) {
        if (w > 0 && h > 0) g.blit(ATLAS, x, y, w, h, u, v, sw, sh, 256, 256);
    }
}
