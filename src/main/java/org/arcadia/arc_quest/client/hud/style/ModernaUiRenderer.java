package org.arcadia.arc_quest.client.hud.style;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.config.HudArtStyle;

import static org.arcadia.arc_quest.client.hud.style.HudStylePalette.*;

/** Jorge Avila's Moderna metalwork (CC BY 3.0), with native theme-aware dark surfaces. */
public final class ModernaUiRenderer {
    private static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath("arc_quest", "textures/gui/style/moderna.png");
    private ModernaUiRenderer() {}

    public static void panel(GuiGraphics g, int x, int y, int w, int h, float alpha, int theme) {
        card(g, x, y, w, h, false, false, alpha, theme);
    }
    public static void glassPanel(GuiGraphics g, int x, int y, int w, int h, int background, float alpha, int theme) {
        if (w <= 0 || h <= 0) return;
        // Exactly one body pass. The extracted frame has a transparent center.
        if ((background >>> 24) != 0) g.fill(x, y, x + w, y + h, glass(background, theme));
        outline(g, x, y, w, h, alpha, theme);
    }
    public static void card(GuiGraphics g, int x, int y, int w, int h, boolean selected, boolean hovered, float alpha, int theme) {
        if (w <= 0 || h <= 0 || unit(alpha) == 0) return;
        g.fill(x, y, x + w, y + h, argb(surface(HudArtStyle.MODERNA, theme, selected, hovered), alpha));
        outline(g, x, y, w, h, alpha * (selected || hovered ? 1 : .75f), theme);
        if ((selected || hovered) && w > 12 && h > 6)
            g.fill(x + 5, y + 2, x + w - 5, y + 3, argb(theme, alpha));
    }
    public static void outline(GuiGraphics g, int x, int y, int w, int h, float alpha, int theme) {
        if (w <= 0 || h <= 0 || unit(alpha) == 0) return;
        if (w < 8 || h < 8) { g.renderOutline(x, y, w, h, argb(theme, alpha)); return; }
        int edge = Math.min(3, Math.min(w, h) / 2);
        // Eight frame patches; the transparent center is never stretched over content.
        g.flush(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        int tint = mix(0xE7D5B8, theme, .22f);
        RenderSystem.setShaderColor((tint >> 16 & 255) / 255f, (tint >> 8 & 255) / 255f, (tint & 255) / 255f, unit(alpha));
        try {
            patch(g, x, y, edge, edge, 0, 0, 8, 8);
            patch(g, x + edge, y, w - edge * 2, edge, 8, 0, 32, 8);
            patch(g, x + w - edge, y, edge, edge, 40, 0, 8, 8);
            patch(g, x, y + edge, edge, h - edge * 2, 0, 8, 8, 32);
            patch(g, x + w - edge, y + edge, edge, h - edge * 2, 40, 8, 8, 32);
            patch(g, x, y + h - edge, edge, edge, 0, 40, 8, 8);
            patch(g, x + edge, y + h - edge, w - edge * 2, edge, 8, 40, 32, 8);
            patch(g, x + w - edge, y + h - edge, edge, edge, 40, 40, 8, 8);
            if (w >= 96 && h >= 48) {
                patch(g, x, y, 12, 12, 48, 0, 24, 24);
                patch(g, x + w - 12, y, 12, 12, 72, 0, 24, 24);
                patch(g, x, y + h - 12, 12, 12, 48, 24, 24, 24);
                patch(g, x + w - 12, y + h - 12, 12, 12, 72, 24, 24, 24);
            }
        } finally { RenderSystem.setShaderColor(1, 1, 1, 1); }
        // Colored inlay carries the owning quest/category/rarity color, not a fixed cyan.
        if (w > 16 && h > 8) {
            g.fill(x + 7, y + 1, x + w - 7, y + 2, argb(theme, alpha * .70f));
            g.fill(x + 7, y + h - 2, x + w - 7, y + h - 1, argb(theme, alpha * .35f));
        }
    }
    public static void field(GuiGraphics g, int x, int y, int w, int h, boolean focused, float alpha, int theme) {
        if (w <= 0 || h <= 0 || unit(alpha) == 0) return;
        g.fill(x, y, x + w, y + h, argb(HudStylePalette.field(HudArtStyle.MODERNA, theme), alpha));
        outline(g, x, y, w, h, alpha * (focused ? 1 : .6f), theme);
        if (focused && w > 8 && h > 4) g.fill(x + 4, y + h - 3, x + w - 4, y + h - 2, argb(theme, alpha));
    }
    public static void button(GuiGraphics g, int x, int y, int w, int h, HudStyleRenderer.Action action,
                               boolean hovered, boolean enabled, boolean pressed, float alpha, int theme) {
        int accent = enabled ? theme : 0x777777;
        boolean primary = enabled && action != HudStyleRenderer.Action.SECONDARY;
        int face = !enabled ? 0x242424 : primary ? actionFace(theme, hovered, pressed)
                : surface(HudArtStyle.MODERNA, theme, pressed, hovered);
        g.fill(x, y, x + w, y + h, argb(face, alpha));
        if (primary && w >= 16 && h >= 10) {
            // Grayscale crystal from the original PSD, recolored in the caller's theme.
            g.flush(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
            int crystal = actionFace(theme, hovered, pressed);
            RenderSystem.setShaderColor((crystal >> 16 & 255) / 255f, (crystal >> 8 & 255) / 255f, (crystal & 255) / 255f, unit(alpha));
            try {
                patch(g, x + 2, y + 2, 5, h - 4, 0, 64, 12, 31);
                patch(g, x + 7, y + 2, w - 14, h - 4, 12, 64, 101, 31);
                patch(g, x + w - 7, y + 2, 5, h - 4, 113, 64, 12, 31);
            } finally { RenderSystem.setShaderColor(1, 1, 1, 1); }
        }
        outline(g, x, y, w, h, alpha * (hovered && enabled ? 1 : .8f), accent);
    }
    public static void progress(GuiGraphics g, int x, int y, int w, int h, float fraction, float alpha, int theme) {
        if (w <= 0 || h <= 0 || unit(alpha) == 0) return;
        g.fill(x, y, x + w, y + h, argb(tint(0x17130F, theme, .12f), alpha));
        int filled = Math.round(w * unit(fraction));
        if (filled > 0) {
            g.fillGradient(x, y, x + filled, y + h, argb(shade(theme, .78f), alpha), argb(shade(theme, .30f), alpha));
            g.fill(x, y, x + filled, y + 1, argb(theme, alpha));
        }
        if (h >= 6) {
            g.fill(x, y + h - 1, x + w, y + h, argb(0x7F6C50, alpha));
            g.fill(x, y + 1, x + 1, y + h - 1, argb(0x7F6C50, alpha));
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, argb(0x7F6C50, alpha));
        }
    }
    public static void marker(GuiGraphics g, int accent, float tier, boolean pointer) {
        int radius = pointer ? 7 : Math.round(12 - Math.max(0, Math.min(2, tier)) * 3);
        float alpha = (accent >>> 24) / 255f;
        for (int row = -radius; row <= (pointer ? 0 : radius); row++) {
            int half = pointer ? row + radius + 1 : radius - Math.abs(row) + 1;
            g.fill(-half, row, half + 1, row + 1, argb(mix(0xA78E67, accent, .25f), alpha));
            if (half > 2) g.fill(-half + 2, row, half - 1, row + 1,
                    argb(shade(accent, row < 0 ? .85f : .45f), alpha));
        }
        if (!pointer) g.fill(-1, -3, 1, 2, argb(0xFFF1D9, alpha));
    }
    private static void patch(GuiGraphics g, int x, int y, int w, int h, int u, int v, int sw, int sh) {
        if (w > 0 && h > 0) g.blit(ATLAS, x, y, w, h, u, v, sw, sh, 128, 128);
    }
}
