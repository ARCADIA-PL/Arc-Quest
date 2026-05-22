package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

final class GuideNavigationControls {
    private GuideNavigationControls() {
    }

    static void drawScaledText(GuideScreen screen, GuiGraphics g, int x, int y, float scale, String text, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(Minecraft.getInstance().font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    static void drawButton(GuideScreen screen, GuiGraphics g, int x, int y, int w, int h,
                           String label, boolean enabled, boolean hovered, int themeColor,
                           int textColor, int disabledColor, int alpha) {
        int border = enabled ? (hovered ? withAlpha(0xFFFFFF, alpha) : withAlpha(themeColor, (int) (alpha * .86f))) : withAlpha(0x43505D, alpha);
        int fill = enabled ? (hovered ? withAlpha(0x304050, (int) (alpha * .55f)) : withAlpha(0x101820, (int) (alpha * .4f))) : withAlpha(0x101010, (int) (alpha * .24f));
        int text = enabled ? withAlpha(textColor, alpha) : withAlpha(disabledColor, alpha);
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);
        drawScaledText(screen, g, x + 6, y + 5, .8f, label, text);
    }

    private static int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }
}
