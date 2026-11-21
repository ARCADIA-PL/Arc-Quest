package org.arcadia.arc_quest.client.hud.style;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Native pixel geometry inspired by OreUI: inset faces, top light and a two-pixel button foot. */
public final class OreUiRenderer {
    public enum Action { PRIMARY, SECONDARY, DANGER }
    private static final int CARD_FOOT_DEPTH = 3;

    private OreUiRenderer() {}

    public static void panel(GuiGraphics g, int x, int y, int w, int h, float alpha, int theme) {
        panelBase(g, x, y, w, h, alpha, theme, panelFace(theme), 4);
        panelRivets(g, x, y, w, h, alpha, theme, 28);
    }

    private static void panelBase(GuiGraphics g, int x, int y, int w, int h, float alpha, int theme, int face, int footDepth) {
        int trim = accentColor(theme);
        bevel(g, x, y, w, h, face,
                OrePalette.tintSurface(0xA5A6A5, trim, .40f), OrePalette.tintSurface(0x171819, trim, .19f), alpha, footDepth);
    }

    private static void panelRivets(GuiGraphics g, int x, int y, int w, int h, float alpha, int theme, int minimumHeight) {
        if (w >= 38 && h >= minimumHeight && HudStylePalette.unit(alpha) > 0) {
            int dark = OrePalette.argb(0x101112, alpha);
            int metal = OrePalette.argb(OrePalette.tintSurface(0x777A7A, accentColor(theme), .3f), alpha);
            g.fill(x + 4, y + 4, x + 7, y + 7, dark);
            g.fill(x + w - 7, y + 4, x + w - 4, y + 7, dark);
            g.fill(x + 4, y + 4, x + 6, y + 5, metal);
            g.fill(x + w - 7, y + 4, x + w - 5, y + 5, metal);
        }
    }

    public static int panelFace(int theme) {
        return OrePalette.tintSurface(0x292B2D, accentColor(theme), .12f);
    }

    public static void accentPanel(GuiGraphics g, int x, int y, int w, int h, float alpha, int theme) {
        panel(g, x, y, w, h, alpha, theme);
        accent(g, x, y, w, h, alpha, theme);
    }

    /** Scene panels use a solid material at full animation opacity. Zero background remains an outline. */
    public static void glassPanel(GuiGraphics g, int x, int y, int w, int h, int background, float edgeOpacity, int theme) {
        if ((background >>> 24) == 0) outline(g, x, y, w, h, edgeOpacity, theme);
        else panel(g, x, y, w, h, Math.max((background >>> 24) / 255f, HudStylePalette.unit(edgeOpacity)), theme);
    }

    public static void glassAccentPanel(GuiGraphics g, int x, int y, int w, int h, int background, float edgeOpacity, int theme) {
        glassPanel(g, x, y, w, h, background, edgeOpacity, theme);
        accent(g, x, y, w, h, edgeOpacity, theme);
    }

    private static int accentColor(int theme) {
        return OrePalette.isChromatic(theme) ? theme & 0xFFFFFF : OrePalette.PRIMARY;
    }

    private static void accent(GuiGraphics g, int x, int y, int w, int h, float alpha, int theme) {
        if (w < 14 || h < 7 || HudStylePalette.unit(alpha) <= 0) return;
        int length = Math.min(42, w / 2);
        int start = x + (w - length) / 2;
        g.fill(start, y + 1, start + length, y + 3, OrePalette.argb(accentColor(theme), alpha));
        g.fill(start + 2, y + 3, start + length - 2, y + 4, OrePalette.argb(OrePalette.shade(accentColor(theme), .55f), alpha));
    }

    public static void card(GuiGraphics g, int x, int y, int w, int h, boolean selected, boolean hovered, float alpha, int theme) {
        int trim = accentColor(theme);
        int face = OrePalette.tintSurface(hovered ? 0x404345 : 0x303233, trim, selected ? .27f : .12f);
        int light = selected ? trim : OrePalette.tintSurface(0x818382, trim, .28f);
        drawCard(g, x, y, w, h, face, light, hovered, alpha);
    }

    /** Raised journal controls use a lighter face than the surrounding scene panels. */
    public static void journalControl(GuiGraphics g, int x, int y, int w, int h, boolean active, boolean hovered,
                                      float alpha, int theme) {
        int trim = accentColor(theme);
        int face = OrePalette.tintSurface(active ? 0x606365 : 0x535658, trim, active ? .27f : .16f);
        if (hovered) face = HudAnimUtil.lerpColor(face, OrePalette.TEXT, .08f);
        int light = active ? HudAnimUtil.lerpColor(trim, OrePalette.TEXT, .24f)
                : OrePalette.tintSurface(0xA5A6A5, trim, .28f);
        drawCard(g, x, y, w, h, face, light, hovered, alpha);
    }

    private static void drawCard(GuiGraphics g, int x, int y, int w, int h, int face, int light, boolean hovered, float alpha) {
        if (HudStylePalette.unit(alpha) <= 0) return;
        bevel(g, x, y, w, h, face, light, 0x171819, alpha, CARD_FOOT_DEPTH);
        if (hovered && w > 4 && h > 4) g.renderOutline(x, y, w, h, OrePalette.argb(OrePalette.FOCUS, alpha));
    }

    public record TaskHighlight(int top, int bottom, float light) {}

    /** Highlight either state across the same row slice, behind the panel rivets. */
    public static void journalTaskPanel(GuiGraphics g, int x, int y, int w, int h, float alpha, int theme,
                                        List<TaskHighlight> highlights) {
        int surface = OrePalette.tintSurface(0x474A4C, accentColor(theme), .17f);
        panelBase(g, x, y, w, h, alpha, theme, surface, CARD_FOOT_DEPTH);
        if (w >= 5 && HudStylePalette.unit(alpha) > 0) {
            for (TaskHighlight highlight : highlights) {
                int top = Math.max(y + cardTextTop(h), highlight.top());
                int bottom = Math.min(y + cardTextBottom(h), highlight.bottom());
                if (bottom > top) {
                    int face = HudAnimUtil.lerpColor(surface, OrePalette.TEXT, highlight.light());
                    g.fill(x + 2, top, x + w - 2, bottom, OrePalette.argb(face, alpha));
                }
            }
        }
        panelRivets(g, x, y, w, h, alpha, theme, 14);
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, float alpha, int accent) {
        if (w <= 0 || h <= 0 || HudStylePalette.unit(alpha) <= 0) return;
        g.renderOutline(x, y, w, h, OrePalette.argb(OrePalette.BORDER, alpha));
        if (w > 3 && h > 3) {
            g.fill(x + 1, y + 1, x + w - 1, y + 2, OrePalette.argb(OrePalette.isChromatic(accent) ? accent : OrePalette.SPECULAR, alpha));
            g.fill(x + 1, y + 1, x + 2, y + h - 1, OrePalette.argb(OrePalette.isChromatic(accent) ? accent : OrePalette.SPECULAR, alpha));
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, OrePalette.argb(OrePalette.BEVEL_SHADOW, alpha));
        }
    }
    public static void field(GuiGraphics g, int x, int y, int w, int h, boolean focused, float alpha, int theme) {
        if (HudStylePalette.unit(alpha) <= 0) return;
        bevel(g, x, y, w, h, OrePalette.tintSurface(0x151617, theme, .09f),
                OrePalette.BORDER, OrePalette.tintSurface(0x747776, theme, .34f), alpha, 2);
        if (focused && w > 1 && h > 1) g.renderOutline(x, y, w, h, OrePalette.argb(OrePalette.FOCUS, alpha));
    }
    /** Returns the pressed text offset; the input bounds remain unchanged. */
    public static int button(GuiGraphics g, int x, int y, int w, int h, Action action,
                             boolean hovered, boolean enabled, float alpha, int theme) {
        if (w <= 0 || h <= 0 || HudStylePalette.unit(alpha) <= 0) return 0;
        boolean pressed = enabled && hovered && mouseDown();
        int offset = pressed ? Math.min(2, Math.max(0, h / 5)) : 0;
        int face, light, foot;
        if (!enabled) { face = OrePalette.SECONDARY; light = face; foot = OrePalette.SECONDARY_SHADOW; }
        else if (action == Action.SECONDARY) {
            int trim = accentColor(theme);
            face = OrePalette.tintSurface(pressed ? OrePalette.SECONDARY_PRESSED : hovered ? OrePalette.SECONDARY_HOVER : OrePalette.SECONDARY, trim, .2f);
            light = OrePalette.tintSurface(OrePalette.TEXT, trim, .12f);
            foot = OrePalette.tintSurface(OrePalette.SECONDARY_SHADOW, trim, .38f);
        } else {
            int trim = action == Action.DANGER ? OrePalette.DANGER : accentColor(theme);
            face = OrePalette.actionFace(trim, pressed ? .65f : hovered ? 1f : .84f);
            light = OrePalette.tintSurface(OrePalette.TEXT, trim, .68f);
            foot = OrePalette.shade(trim, .38f);
        }
        bevel(g, x, y + offset, w, h - offset, face, light, foot, alpha, buttonFoot(h - offset, offset));
        if (hovered && enabled && w > 4 && h - offset > 4)
            g.renderOutline(x, y + offset, w, h - offset, OrePalette.argb(OrePalette.FOCUS, alpha));
        return offset;
    }
    public static int buttonText(Action action, boolean enabled, float alpha) {
        return OrePalette.argb(!enabled ? OrePalette.SURFACE : action == Action.SECONDARY ? OrePalette.BORDER : OrePalette.TEXT, alpha);
    }
    public static void labeledButton(GuiGraphics g, net.minecraft.client.gui.Font font,
                                      net.minecraft.network.chat.Component text, int x, int y, int w, int h,
                                      Action action, boolean hovered, boolean enabled, float alpha, int theme) {
        if (w <= 0 || h <= 0 || HudStylePalette.unit(alpha) <= 3f / 255f) return;
        int offset = button(g, x, y, w, h, action, hovered, enabled, alpha, theme);
        var visible = org.arcadia.arc_quest.client.hud.StyledTextUtil.fitSingleLine(font, text, Math.max(1, w - 8));
        boolean shadow = enabled && action != Action.SECONDARY;
        int textWidth = font.width(visible) + (shadow ? 1 : 0);
        g.drawString(font, visible, x + (w - textWidth) / 2,
                y + Math.round(labelTop(h, offset, font.lineHeight, shadow ? 1 : 0)),
                buttonText(action, enabled, alpha), shadow);
    }

    private static int buttonFoot(int height, int offset) {
        return Math.clamp(height - 5, 0, offset > 0 ? 2 : 4);
    }

    static float labelTop(int height, int offset, float lineHeight, float shadow) {
        return offset + Math.max(0, (height - offset - buttonFoot(height - offset, offset) - lineHeight - shadow) / 2f);
    }

    // Original pixel silhouettes. The waypoint is a faceted pin and the edge indicator is an open chevron.
    private static final String[] PIN = {
            "....#####....", "..##HHHHH##..", ".#HHHHHHHFF#.", "#HH#######FF#",
            "#HF#.....#FF#", "#FF#..H..#FF#", "#FF#.....#FS#", "#FFF#####FSS#",
            ".#FFFFFFFSS#.", "..#FFFFFSS#..", "...#FFFSS#...", "....#FSS#....",
            ".....#S#.....", "......#......"
    };
    private static final String[] CHEVRON = {
            "......#......", ".....#H#.....", "....#HHF#....", "...#HHFFF#...",
            "..#HH###FF#..", ".#HF#...#FS#.", "#FF#.....#SS#", "###.......###"
    };

    private static float markerScale(float tier) {
        return 1.45f - .37f * Math.clamp(tier, 0f, 2f);
    }

    public static float markerHalfHeight(float tier) {
        return PIN.length / 2f * markerScale(tier);
    }

    public static void marker(GuiGraphics g, int accent, float tier, boolean pointer) {
        int alpha = accent >>> 24;
        if (alpha == 0) return;
        int rgb = accent & 0xFFFFFF;
        String[] sprite = pointer ? CHEVRON : PIN;
        float scale = pointer ? 1f : markerScale(tier);
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        for (int row = 0; row < sprite.length; row++) {
            String pixels = sprite[row];
            for (int start = 0; start < pixels.length();) {
                char pixel = pixels.charAt(start);
                int end = start + 1;
                while (end < pixels.length() && pixels.charAt(end) == pixel) end++;
                int color = switch (pixel) {
                    case '#' -> 0x101112;
                    case 'H' -> OrePalette.tintSurface(0xF4F5F5, rgb, .39f);
                    case 'F' -> rgb;
                    case 'S' -> OrePalette.shade(rgb, .55f);
                    default -> 0;
                };
                int y = row - sprite.length / 2;
                if (pixel != '.') g.fill(start - 6, y, end - 6, y + 1, alpha << 24 | color);
                start = end;
            }
        }
        g.pose().popPose();
    }
    public static void progress(GuiGraphics g, int x, int y, int w, int h, float fraction, float alpha, int theme) {
        if (w <= 0 || h <= 0 || HudStylePalette.unit(alpha) <= 0) return;
        int fill = theme & 0xFFFFFF;
        int inset = Math.min(w, h) >= 5 ? 2 : Math.min(w, h) >= 3 ? 1 : 0;
        int filled = Math.round((w - 2 * inset) * HudStylePalette.unit(fraction));
        if (inset > 0) {
            g.renderOutline(x, y, w, h, OrePalette.argb(OrePalette.tintSurface(0xB1B4B4, fill, .5f), alpha));
            x++; y++; w -= 2; h -= 2;
        }
        int gap = Math.max(0, inset - 1);
        int fillX = x + gap, fillEnd = fillX + filled;
        int fillY = y + gap, fillBottom = y + h - gap;
        int track = OrePalette.argb(0x151719, alpha);
        if (filled == 0) {
            g.fill(x, y, x + w, y + h, track);
            return;
        }
        if (gap > 0) {
            g.fill(x, y, x + w, fillY, track);
            g.fill(x, fillBottom, x + w, y + h, track);
            g.fill(x, fillY, fillX, fillBottom, track);
        }
        if (fillEnd < x + w) g.fill(fillEnd, fillY, x + w, fillBottom, track);
        g.fill(fillX, fillY, fillEnd, fillBottom, OrePalette.argb(fill, alpha));
    }

    public static void scrollbar(GuiGraphics g, int x, int y, int w, int h,
                                 int thumbStart, int thumbSize, boolean horizontal, boolean dragging, float alpha, int theme) {
        if (w <= 0 || h <= 0 || HudStylePalette.unit(alpha) <= 0) return;
        int length = horizontal ? w : h;
        int start = Math.clamp(thumbStart, 0, length);
        int end = start + Math.clamp(thumbSize, 0, length - start);
        int railColor = OrePalette.argb(0x111315, alpha);
        if (horizontal) {
            int railY = y + (h - Math.min(h, 2)) / 2;
            if (start > 0) g.fill(x, railY, x + start, railY + Math.min(h, 2), railColor);
            if (end < length) g.fill(x + end, railY, x + w, railY + Math.min(h, 2), railColor);
        } else {
            int railX = x + (w - Math.min(w, 2)) / 2;
            if (start > 0) g.fill(railX, y, railX + Math.min(w, 2), y + start, railColor);
            if (end < length) g.fill(railX, y + end, railX + Math.min(w, 2), y + h, railColor);
        }
        if (end <= start) return;
        int bx = horizontal ? x + start : x, by = horizontal ? y : y + start;
        int bw = horizontal ? end - start : w, bh = horizontal ? h : end - start;
        int face = OrePalette.tintSurface(dragging ? OrePalette.SECONDARY_HOVER : OrePalette.SECONDARY, theme, .2f);
        if (bw >= 4 && bh >= 5) bevel(g, bx, by, bw, bh, face, OrePalette.TEXT, OrePalette.SECONDARY_SHADOW, alpha, 2);
        else g.fill(bx, by, bx + bw, by + bh, OrePalette.argb(face, alpha));
    }

    /** Center text in the recessed field, above its raised bottom lip. */
    public static int fieldTextTop(int height, int lineHeight) {
        return height >= 6 ? Math.max(2, Math.round((height - 1 - lineHeight) / 2f))
                : Math.max(0, (height - lineHeight) / 2);
    }

    public static int cardTextTop(int cardHeight) { return cardHeight >= 6 ? 2 : 0; }

    public static int cardTextBottom(int cardHeight) {
        return cardHeight >= 6 ? cardHeight - 1 - Math.min(CARD_FOOT_DEPTH, cardHeight - 3) : 0;
    }

    private static void bevel(GuiGraphics g, int x, int y, int w, int h, int face, int light, int foot, float alpha, int depth) {
        if (w <= 0 || h <= 0 || HudStylePalette.unit(alpha) <= 0) return;
        int border = OrePalette.argb(0x101112, alpha);
        if (w < 4 || h < 4) { g.fill(x, y, x + w, y + h, border); return; }
        g.renderOutline(x, y, w, h, border);
        int footY = y + h - 1 - Math.min(depth, h - 3);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, OrePalette.argb(light, alpha));
        if (footY > y + 2) {
            g.fill(x + 1, y + 2, x + 2, footY, OrePalette.argb(light, alpha));
            g.fill(x + 2, y + 2, x + w - 2, footY, OrePalette.argb(face, alpha));
            g.fill(x + w - 2, y + 2, x + w - 1, footY, OrePalette.argb(foot, alpha));
        }
        g.fill(x + 1, footY, x + w - 1, y + h - 1, OrePalette.argb(foot, alpha));
    }
    private static boolean mouseDown() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getWindow() != null
                && GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }
}
