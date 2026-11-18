package org.arcadia.arc_quest.client.hud.component;

import org.arcadia.arc_quest.client.hud.style.HudDraw;
import net.minecraft.client.gui.GuiGraphics;

public final class HudScrollbarRenderer {
    private HudScrollbarRenderer() {
    }

    public static void draw(GuiGraphics graphics, HudRect viewport, int contentHeight,
                            int scrollOffset, int accentColor) {
        if (contentHeight <= viewport.height() || viewport.height() <= 0) return;
        int trackX = viewport.right() - 3;
        HudDraw.fill(graphics, trackX, viewport.y(), trackX + 2, viewport.bottom(), 0x664B5562);
        int thumbHeight = Math.max(12, viewport.height() * viewport.height() / contentHeight);
        int maxScroll = contentHeight - viewport.height();
        int travel = viewport.height() - thumbHeight;
        int thumbY = viewport.y() + Math.round(travel * (scrollOffset / (float) maxScroll));
        HudDraw.fill(graphics, trackX - 1, thumbY, trackX + 2, thumbY + thumbHeight, accentColor);
    }
}
