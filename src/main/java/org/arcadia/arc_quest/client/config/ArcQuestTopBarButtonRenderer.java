package org.arcadia.arc_quest.client.config;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class ArcQuestTopBarButtonRenderer {
    static final int Y = 7;
    static final int MARGIN = 9;
    static final int GAP = 7;
    static final int HEIGHT = 18;
    private static final int HORIZONTAL_PADDING = 8;

    private ArcQuestTopBarButtonRenderer() {
    }

    static int width(Font font, Component text) {
        return Math.max(28, font.width(text) + HORIZONTAL_PADDING * 2);
    }

    static Bounds bounds(Font font, Component text, int x) {
        return new Bounds(x, Y, width(font, text), HEIGHT);
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
        if (hovered || emphasized) {
            graphics.fill(bounds.x(), bounds.y() + 2,
                    bounds.x() + bounds.width(), bounds.y() + bounds.height() - 2,
                    fade(withAlpha(accentColor, hovered ? 20 : 12), opacity));
        }
        int textX = bounds.x() + HORIZONTAL_PADDING + (hovered ? 2 : 0);
        int textY = bounds.y() + (bounds.height() - font.lineHeight) / 2;
        int ink = fade(hovered ? 0xFFFFFFFF : textColor, opacity);
        // Minecraft treats text alpha 0..3 as an omitted alpha and makes the glyph opaque.
        if ((ink >>> 24) > 3) graphics.drawString(font, text, textX, textY, ink, true);
        graphics.fill(bounds.x(), bounds.y() + bounds.height() - 2,
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
