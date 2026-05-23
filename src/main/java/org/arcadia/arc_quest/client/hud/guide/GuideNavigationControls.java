package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;

final class GuideNavigationControls {
    private GuideNavigationControls() {}

    static void drawCyberButton(GuiGraphics g, Font font, int x, int y, int w, int h,
                                String text, int themeColor, float effectiveAlpha, float hoverEase, boolean hovered) {
        int bgAlpha = (int) ((0x33 + 0x44 * hoverEase) * effectiveAlpha);
        int borderAlpha = (int) ((0x66 + 0x99 * hoverEase) * effectiveAlpha);
        int borderRgb = hovered ? (themeColor & 0xFFFFFF) : 0xCCCCCC;
        g.fill(x, y, x + w, y + h, HudAnimUtil.withAlpha(0x000000, bgAlpha));
        g.fill(x, y, x + w, y + 1, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        g.fill(x, y + h - 1, x + w, y + h, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        g.fill(x, y, x + 1, y + h, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        g.fill(x + w - 1, y, x + w, y + h, HudAnimUtil.withAlpha(borderRgb, borderAlpha));

        if (effectiveAlpha > 0.05f) {
            int textW = font.width(text);
            float baseScale = 0.85f;
            if (textW * baseScale > w - 4) baseScale = Math.max(0.5f, (w - 6) / (float) textW);
            g.pose().pushPose();
            g.pose().translate(x + w / 2f, y + h / 2f - (font.lineHeight * baseScale) / 2f + 1, 0);
            g.pose().scale(baseScale, baseScale, 1f);
            g.drawCenteredString(font, text, 0, 0, HudAnimUtil.withAlpha(0xFFFFFF, (int) (255 * effectiveAlpha)));
            g.pose().popPose();
        }
    }
}
