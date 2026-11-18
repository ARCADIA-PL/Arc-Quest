package org.arcadia.arc_quest.client.hud.component;

import org.arcadia.arc_quest.client.hud.style.HudStyleRenderer;
import org.arcadia.arc_quest.client.hud.style.HudDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;

public final class HudListItemRenderer {
    private HudListItemRenderer() {
    }

    public static void draw(GuiGraphics graphics, Font font, HudRect bounds, String text,
                            boolean selected, boolean hovered, int accentColor) {
        if (HudDraw.isStyled()) {
            HudStyleRenderer.card(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), selected, hovered, 1f, accentColor);
        } else if (selected) {
            HudDraw.fill(graphics, bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), 0xCC3A3020);
            HudDraw.fill(graphics, bounds.x(), bounds.y(), bounds.x() + 2, bounds.bottom(), accentColor);
        } else if (hovered) {
            HudDraw.fill(graphics, bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), 0x553C4653);
        }
        String visibleText = HudTextUtil.ellipsize(font, text, Math.max(0, bounds.width() - 16));
        int textY = bounds.y() + Math.max(0, (bounds.height() - font.lineHeight) / 2);
        HudDraw.drawString(graphics, font, visibleText, bounds.x() + 8, textY,
                selected ? 0xFFF2D487 : hovered ? 0xFFFFFFFF : 0xFFD6DCE4, false);
    }

    public static void drawJournal(GuiGraphics graphics, Font font, HudRect bounds,
                                   String title, String subtitle, boolean selected,
                                   boolean hovered, int themeColor, int alpha) {
        if (bounds.width() <= 0 || bounds.height() <= 0 || alpha <= 8) return;
        int backgroundAlpha = selected ? 42 : hovered ? 24 : 8;
        if (HudDraw.isStyled()) HudStyleRenderer.card(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), selected, hovered, alpha / 255f, themeColor);
        else HudDraw.fill(graphics, bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                HudAnimUtil.withAlpha(selected ? themeColor : 0xFFFFFF,
                        Math.round(backgroundAlpha * alpha / 255f)));
        if (selected || hovered) {
            HudDraw.fill(graphics, bounds.x(), bounds.y(), bounds.x() + (selected ? 2 : 1), bounds.bottom(),
                    HudAnimUtil.withAlpha(themeColor, Math.round((selected ? 220 : 100) * alpha / 255f)));
        }

        int textX = bounds.x() + 8;
        int availableWidth = Math.max(0, bounds.width() - 14);
        boolean hasSubtitle = subtitle != null && !subtitle.isBlank() && bounds.height() >= 25;
        int titleY = hasSubtitle ? bounds.y() + 4 : bounds.y() + Math.max(0, (bounds.height() - font.lineHeight) / 2);
        HudDraw.drawString(graphics, font, HudTextUtil.ellipsize(font, title, availableWidth), textX, titleY,
                HudAnimUtil.withAlpha(selected ? 0xFFFFFF : hovered ? 0xEEEEEE : 0xD6DCE4, alpha), false);
        if (hasSubtitle) {
            HudDraw.drawString(graphics, font, HudTextUtil.ellipsize(font, subtitle, availableWidth), textX,
                    titleY + font.lineHeight + 1,
                    HudAnimUtil.withAlpha(selected ? themeColor : 0x778899, Math.round(alpha * 0.9f)), false);
        }
    }
}
