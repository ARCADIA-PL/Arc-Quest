package org.arcadia.arc_quest.client.hud.quest.journal.component;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;

public final class JournalChevronRenderer {
    private JournalChevronRenderer() {
    }

    public static void drawExpandable(GuiGraphics graphics, int centerX, int centerY,
                                      float expansion, int color) {
        float eased = HudAnimUtil.easeOutCubic(expansion);
        int leftX = Math.round(lerp(centerX - 2, centerX - 4, eased));
        int leftY = Math.round(lerp(centerY - 4, centerY - 2, eased));
        int middleX = Math.round(lerp(centerX + 2, centerX, eased));
        int middleY = Math.round(lerp(centerY, centerY + 2, eased));
        int rightX = Math.round(lerp(centerX - 2, centerX + 4, eased));
        int rightY = Math.round(lerp(centerY + 4, centerY - 2, eased));
        drawPixelLine(graphics, leftX, leftY, middleX, middleY, color);
        drawPixelLine(graphics, middleX, middleY, rightX, rightY, color);
    }

    private static void drawPixelLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int step = 0; step <= steps; step++) {
            float progress = steps == 0 ? 0f : (float) step / steps;
            int x = Math.round(lerp(x1, x2, progress));
            int y = Math.round(lerp(y1, y2, progress));
            graphics.fill(x, y, x + 2, y + 2, color);
        }
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }
}
