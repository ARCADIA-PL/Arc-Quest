package org.arcadia.arc_quest.client.hud.style;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/** Native pixel geometry inspired by OreUI: inset faces, top light and a two-pixel button foot. */
public final class OreUiRenderer {
    public enum Action { PRIMARY, SECONDARY, DANGER }
    private OreUiRenderer() {}

    public static void panel(GuiGraphics g, int x, int y, int w, int h, float alpha, int theme) {
        bevel(g, x, y, w, h, OrePalette.tintSurface(OrePalette.RAISED, theme, .12f),
                OrePalette.tintSurface(OrePalette.SPECULAR, theme, .40f),
                OrePalette.tintSurface(OrePalette.BORDER, theme, .16f), alpha, 1);
    }
    public static void accentPanel(GuiGraphics g, int x, int y, int w, int h, float alpha, int theme) {
        panel(g, x, y, w, h, alpha, theme);
        if (w > 3 && h > 3) g.fill(x + 1, y + 1, x + 3, y + h - 1, OrePalette.argb(theme, alpha));
    }
    /** One translucent body pass. An opaque bevel backing here would hide the scene. */
    public static void glassPanel(GuiGraphics g, int x, int y, int w, int h, int background, float edgeOpacity, int theme) {
        if (w <= 0 || h <= 0) return;
        if ((background >>> 24) != 0) g.fill(x, y, x + w, y + h, OrePalette.glassBackground(background, theme));
        outline(g, x, y, w, h, edgeOpacity * .70f, theme);
    }
    public static void glassAccentPanel(GuiGraphics g, int x, int y, int w, int h, int background, float edgeOpacity, int theme) {
        glassPanel(g, x, y, w, h, background, edgeOpacity, theme);
        if (w > 3 && h > 3) g.fill(x + 1, y + 1, x + 3, y + h - 1, OrePalette.argb(theme, edgeOpacity));
    }
    public static void card(GuiGraphics g, int x, int y, int w, int h, boolean selected, boolean hovered, float alpha, int theme) {
        bevel(g, x, y, w, h, selected ? OrePalette.shade(theme, .30f)
                        : OrePalette.tintSurface(hovered ? OrePalette.SURFACE : OrePalette.RAISED, theme, hovered ? .25f : .12f),
                selected ? theme : OrePalette.tintSurface(OrePalette.SPECULAR, theme, hovered ? .70f : .40f),
                OrePalette.tintSurface(OrePalette.BORDER, theme, .18f), alpha, 1);
        if (selected && w > 4 && h > 4) g.fill(x + 1, y + 1, x + 3, y + h - 1, OrePalette.argb(theme, alpha));
    }
    public static void outline(GuiGraphics g, int x, int y, int w, int h, float alpha, int accent) {
        if (w <= 0 || h <= 0 || alpha <= 0) return;
        g.renderOutline(x, y, w, h, OrePalette.argb(OrePalette.BORDER, alpha));
        if (w > 3 && h > 3) {
            g.fill(x + 1, y + 1, x + w - 1, y + 2, OrePalette.argb(OrePalette.isChromatic(accent) ? accent : OrePalette.SPECULAR, alpha));
            g.fill(x + 1, y + 1, x + 2, y + h - 1, OrePalette.argb(OrePalette.isChromatic(accent) ? accent : OrePalette.SPECULAR, alpha));
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, OrePalette.argb(OrePalette.BEVEL_SHADOW, alpha));
        }
    }
    public static void field(GuiGraphics g, int x, int y, int w, int h, boolean focused, float alpha, int theme) {
        bevel(g, x, y, w, h, OrePalette.tintSurface(OrePalette.SUNKEN, theme, .12f), OrePalette.BORDER,
                OrePalette.tintSurface(OrePalette.SPECULAR, theme, .40f), alpha, 1);
        if (focused) g.renderOutline(x, y, w, h, OrePalette.argb(theme, alpha));
    }
    /** Returns the pressed text offset; the input bounds remain unchanged. */
    public static int button(GuiGraphics g, int x, int y, int w, int h, Action action,
                             boolean hovered, boolean enabled, float alpha, int theme) {
        boolean pressed = enabled && hovered && mouseDown();
        int offset = pressed ? Math.min(2, Math.max(0, h / 5)) : 0;
        int face, light, foot;
        if (!enabled) { face = OrePalette.SECONDARY; light = face; foot = OrePalette.SECONDARY_SHADOW; }
        else if (action == Action.SECONDARY) {
            face = OrePalette.tintSurface(pressed ? OrePalette.SECONDARY_PRESSED : hovered ? OrePalette.SECONDARY_HOVER : OrePalette.SECONDARY,
                    theme, hovered ? .25f : .16f);
            light = OrePalette.tintSurface(0xF4F6F9, theme, .14f);
            foot = OrePalette.tintSurface(OrePalette.SECONDARY_SHADOW, theme, .30f);
        } else {
            face = OrePalette.actionFace(theme, pressed ? .48f : hovered ? .72f : .60f);
            light = OrePalette.shade(theme, hovered ? 1f : .85f);
            foot = OrePalette.shade(theme, .30f);
        }
        bevel(g, x, y + offset, w, h - offset, face, light, foot, alpha, pressed ? 1 : 3);
        if (hovered && enabled && w > 4 && h > 4)
            g.renderOutline(x + 1, y + offset + 1, w - 2, h - offset - 2,
                    OrePalette.argb(action == Action.SECONDARY ? OrePalette.shade(theme, .55f) : OrePalette.TEXT, alpha));
        return offset;
    }
    public static int buttonText(Action action, boolean enabled, float alpha) {
        return OrePalette.argb(!enabled ? OrePalette.SURFACE : action == Action.SECONDARY ? OrePalette.BORDER : OrePalette.TEXT, alpha);
    }
    public static void labeledButton(GuiGraphics g, net.minecraft.client.gui.Font font,
                                      net.minecraft.network.chat.Component text, int x, int y, int w, int h,
                                      Action action, boolean hovered, boolean enabled, float alpha, int theme) {
        if (alpha <= 3f / 255f) return;
        int offset = button(g, x, y, w, h, action, hovered, enabled, alpha, theme);
        var visible = org.arcadia.arc_quest.client.hud.StyledTextUtil.fitSingleLine(font, text, Math.max(1, w - 8));
        g.drawString(font, visible, x + (w - font.width(visible)) / 2, y + (h - font.lineHeight) / 2 - 1 + offset,
                buttonText(action, enabled, alpha), enabled && action != Action.SECONDARY);
    }
    /** Stepped silhouette, with the same origin and orientation as the default marker. */
    public static void marker(GuiGraphics g, int accent, float tier, boolean pointer) {
        int radius = pointer ? 7 : Math.round(12 - Math.min(2, Math.max(0, tier)) * 3);
        float alpha = (accent >>> 24) / 255f;
        int face = accent & 0xFFFFFF;
        for (int row = -radius; row <= radius; row++) {
            if (pointer && row > 1) continue;
            int half = pointer ? Math.max(1, row + radius + 1) : radius - Math.abs(row) + 1;
            g.fill(-half - 1, row, half + 2, row + 1, OrePalette.argb(OrePalette.BORDER, alpha));
            g.fill(-half, row, half + 1, row + 1, OrePalette.argb(row < -radius / 2 ? OrePalette.TEXT : face, alpha));
        }
        if (!pointer) g.fill(-2, -2, 3, 3, OrePalette.argb(OrePalette.RAISED, alpha));
    }
    public static void progress(GuiGraphics g, int x, int y, int w, int h, float fraction, float alpha, int theme) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + h, OrePalette.argb(OrePalette.tintSurface(OrePalette.SUNKEN, theme, .12f), alpha));
        int filled = Math.round(w * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) {
            g.fill(x, y, x + filled, y + h, OrePalette.argb(OrePalette.shade(theme, .72f), alpha));
            g.fill(x, y, x + filled, y + 1, OrePalette.argb(theme, alpha));
        }
    }
    private static void bevel(GuiGraphics g, int x, int y, int w, int h, int face, int light, int foot, float alpha, int depth) {
        if (w <= 0 || h <= 0 || alpha <= 0) return;
        g.fill(x, y, x + w, y + h, OrePalette.argb(OrePalette.BORDER, alpha));
        if (w < 3 || h < 3) return;
        int d = Math.min(depth, h - 2);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, OrePalette.argb(face, alpha));
        g.fill(x + 1, y + 1, x + w - 1, y + 2, OrePalette.argb(light, alpha));
        g.fill(x + 1, y + 1, x + 2, y + h - d - 1, OrePalette.argb(light, alpha));
        g.fill(x + 1, y + h - d - 1, x + w - 1, y + h - 1, OrePalette.argb(foot, alpha));
    }
    private static boolean mouseDown() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getWindow() != null
                && GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }
}
