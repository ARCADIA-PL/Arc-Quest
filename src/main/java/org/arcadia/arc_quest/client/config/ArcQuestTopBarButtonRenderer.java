package org.arcadia.arc_quest.client.config;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;

final class ArcQuestTopBarButtonRenderer {
    static final int Y = 7;
    static final int MARGIN = 9;
    static final int GAP = 7;
    static final int HEIGHT = 18;
    private static final int HORIZONTAL_PADDING = 7;
    private static final float TEXT_SCALE = 0.86f;

    private ArcQuestTopBarButtonRenderer() {
    }

    static int width(Font font, Component text) {
        return Math.max(28,
                (int) Math.ceil(font.width(text) * TEXT_SCALE) + HORIZONTAL_PADDING * 2);
    }

    static Bounds bounds(Font font, Component text, int x) {
        return new Bounds(x, Y, width(font, text), HEIGHT);
    }

    static void render(GuiGraphics graphics, Font font, Component text, int x,
                       int mouseX, int mouseY, int accentColor, int textColor,
                       boolean emphasized) {
        Bounds bounds = bounds(font, text, x);
        boolean hovered = bounds.contains(mouseX, mouseY);
        int border = hovered || emphasized
                ? withAlpha(hovered ? 0xFFFFFF : accentColor, 235)
                : withAlpha(accentColor, 150);
        int background = hovered ? 0xDD18222E : 0xC010151D;
        HudAnimUtil.drawFrame(graphics, bounds.x(), bounds.y(), bounds.width(),
                bounds.height(), background, border);
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + 2,
                bounds.y() + bounds.height(), withAlpha(accentColor, 230));

        float scaledWidth = font.width(text) * TEXT_SCALE;
        float scaledHeight = font.lineHeight * TEXT_SCALE;
        float textX = bounds.x() + (bounds.width() - scaledWidth) / 2f;
        float textY = bounds.y() + (bounds.height() - scaledHeight) / 2f;
        graphics.pose().pushPose();
        graphics.pose().translate(textX, textY, 0);
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1f);
        graphics.drawString(font, text, 0, 0,
                hovered ? 0xFFFFFFFF : textColor, true);
        graphics.pose().popPose();
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
