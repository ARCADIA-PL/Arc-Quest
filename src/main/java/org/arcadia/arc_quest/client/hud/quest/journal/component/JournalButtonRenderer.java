package org.arcadia.arc_quest.client.hud.quest.journal.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudRect;

public final class JournalButtonRenderer {
    private JournalButtonRenderer() {
    }

    public static void drawUnderlineButton(GuiGraphics graphics, Font font, HudRect bounds,
                                           String label, int themeColor, int alpha,
                                           float hoverProgress, boolean unread) {
        if (alpha <= 8) return;
        float hover = HudAnimUtil.easeOutCubic(hoverProgress);
        int textX = bounds.x() + 8 + Math.round(2f * hover);
        int textY = bounds.y() + (bounds.height() - font.lineHeight) / 2;
        graphics.fill(bounds.x(), bounds.y() + 2, bounds.right(), bounds.bottom() - 2,
                HudAnimUtil.withAlpha(themeColor, Math.round(20 * hover * alpha / 255f)));
        graphics.drawString(font, label, textX, textY,
                HudAnimUtil.withAlpha(HudAnimUtil.lerpColor(0xEEEEEE, 0xFFFFFF, hover), alpha), true);
        graphics.fill(bounds.x(), bounds.bottom() - 2, bounds.right(), bounds.bottom(),
                HudAnimUtil.withAlpha(themeColor, Math.round((205 + 50 * hover) * alpha / 255f)));
        if (unread) JournalUnreadBadgeRenderer.draw(graphics, bounds.right() - 4, bounds.y() + 4, alpha);
    }

    public static void drawCyberButton(GuiGraphics graphics, Font font, HudRect bounds,
                                       String text, int themeColor, float hoverProgress,
                                       boolean hovered, float alphaFactor) {
        int backgroundAlpha = (int) ((0x33 + 0x44 * hoverProgress) * alphaFactor);
        int borderAlpha = (int) ((0x66 + 0x99 * hoverProgress) * alphaFactor);
        int borderRgb = hovered ? themeColor & 0xFFFFFF : 0xCCCCCC;
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                HudAnimUtil.withAlpha(0x000000, backgroundAlpha));
        drawFrame(graphics, bounds, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        if (alphaFactor <= 0.05f) return;

        int textWidth = font.width(text);
        float scale = 0.85f;
        if (textWidth * scale > bounds.width() - 4) {
            scale = Math.max(0.5f, (bounds.width() - 6) / (float) Math.max(1, textWidth));
        }
        graphics.pose().pushPose();
        graphics.pose().translate(bounds.x() + bounds.width() / 2f,
                bounds.y() + bounds.height() / 2f - font.lineHeight * scale / 2f + 1, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawCenteredString(font, text, 0, 0,
                HudAnimUtil.withAlpha(0xFFFFFF, (int) (255 * alphaFactor)));
        graphics.pose().popPose();
    }

    public static void drawCompactButton(GuiGraphics graphics, Font font, HudRect bounds,
                                         String label, int themeColor, float alphaFactor,
                                         boolean hovered, float textScale) {
        int backgroundAlpha = hovered
                ? (int) (0.25f * alphaFactor)
                : (int) (0.10f * alphaFactor);
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                HudAnimUtil.withAlpha(themeColor, backgroundAlpha));
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + 1, bounds.bottom(),
                HudAnimUtil.withAlpha(themeColor, (int) (0.5f * alphaFactor)));
        JournalScaledTextRenderer.draw(graphics, font, label, bounds.x() + 4, bounds.y() + 3,
                textScale, HudAnimUtil.withAlpha(0xFFFFFF, (int) (255 * alphaFactor)), false);
    }

    public static void drawFilterTab(GuiGraphics graphics, Font font, HudRect bounds,
                                     String label, int themeColor, int alpha,
                                     boolean active, boolean hovered, float textScale) {
        int background = active
                ? HudAnimUtil.withAlpha(themeColor, Math.round(alpha * 0.15f))
                : hovered ? HudAnimUtil.withAlpha(0x334455, alpha) : 0;
        if (background != 0) graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), background);
        int lineColor = active ? themeColor : 0x334455;
        graphics.fill(bounds.x(), bounds.bottom() - 1, bounds.right(), bounds.bottom(),
                HudAnimUtil.withAlpha(lineColor, alpha));
        int textColor = active ? 0xFFFFFF : hovered ? 0xCCCCCC : 0x778899;
        JournalScaledTextRenderer.draw(graphics, font, label, bounds.x() + 8, bounds.y() + 6,
                textScale, HudAnimUtil.withAlpha(textColor, alpha), false);
    }

    public static void drawToggleButton(GuiGraphics graphics, Font font, HudRect bounds,
                                        String label, int themeColor, int alpha,
                                        boolean active, float textScale) {
        int background = active ? 0x182026 : 0x05060A;
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                HudAnimUtil.withAlpha(background, (int) ((active ? 0xAA : 0x66) * (alpha / 255f))));
        graphics.fill(bounds.x(), bounds.bottom() - 1, bounds.right(), bounds.bottom(),
                HudAnimUtil.withAlpha(active ? themeColor : 0x555555, alpha));
        JournalScaledTextRenderer.draw(graphics, font, label, bounds.x() + 6,
                bounds.y() + Math.max(3, (bounds.height() - 7) / 2), textScale,
                HudAnimUtil.withAlpha(active ? 0xFFFFFF : 0xAAB4C0, alpha), false);
    }

    private static void drawFrame(GuiGraphics graphics, HudRect bounds, int color) {
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.y() + 1, color);
        graphics.fill(bounds.x(), bounds.bottom() - 1, bounds.right(), bounds.bottom(), color);
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + 1, bounds.bottom(), color);
        graphics.fill(bounds.right() - 1, bounds.y(), bounds.right(), bounds.bottom(), color);
    }
}
