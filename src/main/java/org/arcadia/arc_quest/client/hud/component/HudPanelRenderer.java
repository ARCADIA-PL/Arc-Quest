package org.arcadia.arc_quest.client.hud.component;

import net.minecraft.client.gui.GuiGraphics;

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
}
