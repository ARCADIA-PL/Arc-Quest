package org.arcadia.arc_quest.client.hud.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;

public final class HudPanelRenderer {
    private HudPanelRenderer() {
    }

    public static void drawPanel(GuiGraphics graphics, HudRect bounds, int backgroundColor,
                                 int borderColor, int accentColor) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        graphics.fill(bounds.x() + 2, bounds.y() + 2, bounds.right() + 2, bounds.bottom() + 2, 0x55000000);
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), backgroundColor);
        graphics.renderOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), borderColor);
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + 2, bounds.bottom(), accentColor);
    }

    public static void drawHeaderDivider(GuiGraphics graphics, HudRect bounds, int headerHeight, int color) {
        int dividerY = Math.min(bounds.bottom(), bounds.y() + headerHeight);
        graphics.fill(bounds.x() + 2, dividerY, bounds.right(), dividerY + 1, color);
    }

    public static void drawDivider(GuiGraphics graphics, int x, int y, int width, int color) {
        if (width > 0) graphics.fill(x, y, x + width, y + 1, color);
    }

    public static void drawJournalPanel(GuiGraphics graphics, HudRect bounds, int themeColor,
                                        int backgroundAlpha, int borderAlpha) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        graphics.fill(bounds.x() + 2, bounds.y() + 2, bounds.right() + 3, bounds.bottom() + 3,
                HudAnimUtil.withAlpha(0x000000, Math.min(120, backgroundAlpha)));
        HudAnimUtil.drawFrame(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                HudAnimUtil.withAlpha(0x000000, backgroundAlpha),
                HudAnimUtil.withAlpha(themeColor, borderAlpha));
        HudRenderUtil.drawCyberneticEdge(graphics, bounds.x(), bounds.y(), bounds.height(),
                themeColor, Math.min(255, borderAlpha * 3));
    }

    public static void drawJournalHeader(GuiGraphics graphics, Font font, HudRect bounds,
                                         int headerHeight, String title, String trailingText,
                                         int themeColor, int alpha) {
        int safeHeaderHeight = Math.min(bounds.height(), Math.max(font.lineHeight + 8, headerHeight));
        int textY = bounds.y() + Math.max(3, (safeHeaderHeight - font.lineHeight) / 2);
        graphics.drawString(font, title, bounds.x() + 10, textY,
                HudAnimUtil.withAlpha(0xFFFFFF, alpha), true);
        if (trailingText != null && !trailingText.isBlank()) {
            int trailingWidth = font.width(trailingText);
            graphics.drawString(font, trailingText, bounds.right() - 10 - trailingWidth, textY,
                    HudAnimUtil.withAlpha(0x8899AA, alpha), false);
        }
        graphics.fill(bounds.x() + 4, bounds.y() + safeHeaderHeight - 1,
                bounds.right() - 2, bounds.y() + safeHeaderHeight,
                HudAnimUtil.withAlpha(themeColor, Math.min(alpha, 96)));
    }
}
