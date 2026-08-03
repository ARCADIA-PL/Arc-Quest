package org.arcadia.arc_quest.client.hud.component;

import net.minecraft.client.gui.GuiGraphics;

public final class HudScrollbarRenderer {
    private HudScrollbarRenderer() {
    }

    public static void draw(GuiGraphics graphics, HudRect viewport, int contentHeight,
                            int scrollOffset, int accentColor) {
        if (contentHeight <= viewport.height() || viewport.height() <= 0) return;
        int trackX = viewport.right() - 3;
        graphics.fill(trackX, viewport.y(), trackX + 2, viewport.bottom(), 0x664B5562);
        int thumbHeight = Math.max(12, viewport.height() * viewport.height() / contentHeight);
        int maxScroll = contentHeight - viewport.height();
        int travel = viewport.height() - thumbHeight;
        int thumbY = viewport.y() + Math.round(travel * (scrollOffset / (float) maxScroll));
        graphics.fill(trackX - 1, thumbY, trackX + 2, thumbY + thumbHeight, accentColor);
    }
}
