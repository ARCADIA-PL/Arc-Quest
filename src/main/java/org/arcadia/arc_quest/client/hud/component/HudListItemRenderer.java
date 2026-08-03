package org.arcadia.arc_quest.client.hud.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class HudListItemRenderer {
    private HudListItemRenderer() {
    }

    public static void draw(GuiGraphics graphics, Font font, HudRect bounds, String text,
                            boolean selected, boolean hovered, int accentColor) {
        if (selected) {
            graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), 0xCC3A3020);
            graphics.fill(bounds.x(), bounds.y(), bounds.x() + 2, bounds.bottom(), accentColor);
        } else if (hovered) {
            graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), 0x553C4653);
        }
        String visibleText = HudTextUtil.ellipsize(font, text, Math.max(0, bounds.width() - 16));
        int textY = bounds.y() + Math.max(0, (bounds.height() - font.lineHeight) / 2);
        graphics.drawString(font, visibleText, bounds.x() + 8, textY,
                selected ? 0xFFF2D487 : hovered ? 0xFFFFFFFF : 0xFFD6DCE4, false);
    }
}
