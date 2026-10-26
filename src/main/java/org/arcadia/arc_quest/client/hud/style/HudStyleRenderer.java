package org.arcadia.arc_quest.client.hud.style;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.StyledTextUtil;
import org.arcadia.arc_quest.config.ArcQuestConfig;
import org.arcadia.arc_quest.config.HudArtStyle;
import org.lwjgl.glfw.GLFW;

/** Shared semantic controls. DEFAULT stays on the original call sites, with no drawing interception. */
public final class HudStyleRenderer {
    public enum Action { PRIMARY, SECONDARY, DANGER }
    private HudStyleRenderer() {}
    private static HudArtStyle style() { return ArcQuestConfig.hudArtStyle(); }
    private static OreUiRenderer.Action oreAction(Action action) {
        return switch (action) {
            case PRIMARY -> OreUiRenderer.Action.PRIMARY;
            case SECONDARY -> OreUiRenderer.Action.SECONDARY;
            case DANGER -> OreUiRenderer.Action.DANGER;
        };
    }
    public static void panel(GuiGraphics g, int x, int y, int w, int h, float alpha, int theme) {
        switch (style()) {
            case ORE -> OreUiRenderer.panel(g, x, y, w, h, alpha, theme);
            case MODERNA -> ModernaUiRenderer.panel(g, x, y, w, h, alpha, theme);
            case CYBERPUNK -> CyberpunkUiRenderer.panel(g, x, y, w, h, alpha, theme);
            case WYRMHEART -> WyrmheartUiRenderer.panel(g, x, y, w, h, alpha, theme);
            default -> throw new IllegalStateException("DEFAULT uses the original renderer");
        }
    }
    public static void glassPanel(GuiGraphics g, int x, int y, int w, int h, int background, float alpha, int theme) {
        switch (style()) {
            case ORE -> OreUiRenderer.glassPanel(g, x, y, w, h, background, alpha, theme);
            case MODERNA -> ModernaUiRenderer.glassPanel(g, x, y, w, h, background, alpha, theme);
            case CYBERPUNK -> CyberpunkUiRenderer.glassPanel(g, x, y, w, h, background, alpha, theme);
            case WYRMHEART -> WyrmheartUiRenderer.glassPanel(g, x, y, w, h, background, alpha, theme);
            default -> throw new IllegalStateException("DEFAULT uses the original renderer");
        }
    }
    public static void glassAccentPanel(GuiGraphics g, int x, int y, int w, int h, int background, float alpha, int theme) {
        if (style() == HudArtStyle.ORE) OreUiRenderer.glassAccentPanel(g, x, y, w, h, background, alpha, theme);
        else {
            glassPanel(g, x, y, w, h, background, alpha, theme);
            if (w > 16 && h > 12) g.fill(x + 4, y + 6, x + 5, y + h - 6, HudStylePalette.argb(theme, alpha * .8f));
        }
    }
    public static void outline(GuiGraphics g, int x, int y, int w, int h, float alpha, int theme) {
        switch (style()) {
            case ORE -> OreUiRenderer.outline(g, x, y, w, h, alpha, theme);
            case MODERNA -> ModernaUiRenderer.outline(g, x, y, w, h, alpha, theme);
            case CYBERPUNK -> CyberpunkUiRenderer.outline(g, x, y, w, h, alpha, theme);
            case WYRMHEART -> WyrmheartUiRenderer.outline(g, x, y, w, h, alpha, theme);
            default -> throw new IllegalStateException("DEFAULT uses the original renderer");
        }
    }
    public static void card(GuiGraphics g, int x, int y, int w, int h, boolean selected, boolean hovered, float alpha, int theme) {
        switch (style()) {
            case ORE -> OreUiRenderer.card(g, x, y, w, h, selected, hovered, alpha, theme);
            case MODERNA -> ModernaUiRenderer.card(g, x, y, w, h, selected, hovered, alpha, theme);
            case CYBERPUNK -> CyberpunkUiRenderer.card(g, x, y, w, h, selected, hovered, alpha, theme);
            case WYRMHEART -> WyrmheartUiRenderer.card(g, x, y, w, h, selected, hovered, alpha, theme);
            default -> throw new IllegalStateException("DEFAULT uses the original renderer");
        }
    }
    public static void field(GuiGraphics g, int x, int y, int w, int h, boolean focused, float alpha, int theme) {
        switch (style()) {
            case ORE -> OreUiRenderer.field(g, x, y, w, h, focused, alpha, theme);
            case MODERNA -> ModernaUiRenderer.field(g, x, y, w, h, focused, alpha, theme);
            case CYBERPUNK -> CyberpunkUiRenderer.field(g, x, y, w, h, focused, alpha, theme);
            case WYRMHEART -> WyrmheartUiRenderer.field(g, x, y, w, h, focused, alpha, theme);
            default -> throw new IllegalStateException("DEFAULT uses the original renderer");
        }
    }
    public static int button(GuiGraphics g, int x, int y, int w, int h, Action action, boolean hovered, boolean enabled, float alpha, int theme) {
        if (style() == HudArtStyle.ORE) return OreUiRenderer.button(g, x, y, w, h, oreAction(action), hovered, enabled, alpha, theme);
        if (w <= 0 || h <= 0 || HudStylePalette.unit(alpha) == 0) return 0;
        boolean pressed = enabled && hovered && mouseDown();
        int offset = pressed && h >= 8 ? 1 : 0;
        if (style() == HudArtStyle.MODERNA) ModernaUiRenderer.button(g, x, y + offset, w, h - offset, action, hovered, enabled, pressed, alpha, theme);
        else if (style() == HudArtStyle.CYBERPUNK) CyberpunkUiRenderer.button(g, x, y + offset, w, h - offset, action, hovered, enabled, pressed, alpha, theme);
        else if (style() == HudArtStyle.WYRMHEART) WyrmheartUiRenderer.button(g, x, y + offset, w, h - offset, action, hovered, enabled, pressed, alpha, theme);
        else throw new IllegalStateException("DEFAULT uses the original renderer");
        return offset;
    }
    public static int buttonText(Action action, boolean enabled, float alpha) {
        return style() == HudArtStyle.ORE ? OreUiRenderer.buttonText(oreAction(action), enabled, alpha)
                : HudStylePalette.argb(enabled ? 0xFFFFFF : 0xA5A5A5, alpha);
    }
    public static void labeledButton(GuiGraphics g, Font font, Component text, int x, int y, int w, int h,
                                     Action action, boolean hovered, boolean enabled, float alpha, int theme) {
        if (style() == HudArtStyle.ORE) {
            OreUiRenderer.labeledButton(g, font, text, x, y, w, h, oreAction(action), hovered, enabled, alpha, theme);
            return;
        }
        if (w <= 0 || h <= 0 || HudStylePalette.unit(alpha) <= 3f / 255f) return;
        int offset = button(g, x, y, w, h, action, hovered, enabled, alpha, theme);
        var visible = StyledTextUtil.fitSingleLine(font, text, Math.max(1, w - 12));
        g.drawString(font, visible, x + (w - font.width(visible)) / 2, y + (h - font.lineHeight) / 2 + offset,
                buttonText(action, enabled, alpha), false);
    }
    public static void progress(GuiGraphics g, int x, int y, int w, int h, float fraction, float alpha, int theme) {
        switch (style()) {
            case ORE -> OreUiRenderer.progress(g, x, y, w, h, fraction, alpha, theme);
            case MODERNA -> ModernaUiRenderer.progress(g, x, y, w, h, fraction, alpha, theme);
            case CYBERPUNK -> CyberpunkUiRenderer.progress(g, x, y, w, h, fraction, alpha, theme);
            case WYRMHEART -> WyrmheartUiRenderer.progress(g, x, y, w, h, fraction, alpha, theme);
            default -> throw new IllegalStateException("DEFAULT uses the original renderer");
        }
    }
    public static void marker(GuiGraphics g, int accent, float tier, boolean pointer) {
        if (style() == HudArtStyle.ORE) { OreUiRenderer.marker(g, accent, tier, pointer); return; }
        if (style() == HudArtStyle.MODERNA) ModernaUiRenderer.marker(g, accent, tier, pointer);
        else if (style() == HudArtStyle.CYBERPUNK) CyberpunkUiRenderer.marker(g, accent, tier, pointer);
        else if (style() == HudArtStyle.WYRMHEART) WyrmheartUiRenderer.marker(g, accent, tier, pointer);
        else throw new IllegalStateException("DEFAULT uses the original renderer");
    }
    private static boolean mouseDown() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getWindow() != null
                && GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }
}
