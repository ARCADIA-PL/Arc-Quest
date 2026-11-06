package org.arcadia.arc_quest.client.hud.quest.journal.component;

import org.arcadia.arc_quest.client.hud.style.HudStyleRenderer;
import org.arcadia.arc_quest.client.hud.style.HudDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.StyledTextUtil;

public final class JournalButtonRenderer {
    private JournalButtonRenderer() {
    }

    public static void drawUnderlineButton(GuiGraphics graphics, Font font, HudRect bounds,
                                           String label, int themeColor, int alpha,
                                           float hoverProgress, boolean unread) {
        if (alpha <= 8) return;
        if (HudDraw.isStyled()) {
            int offset = HudStyleRenderer.button(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), HudStyleRenderer.Action.SECONDARY, hoverProgress > .5f, true, alpha / 255f, themeColor);
            centeredLabel(graphics, font, Component.literal(label), bounds, .95f, offset,
                    HudStyleRenderer.buttonText(HudStyleRenderer.Action.SECONDARY, true, alpha / 255f), false);
            if (unread) JournalUnreadBadgeRenderer.draw(graphics, bounds.right() - 4, bounds.y() + 4, alpha);
            return;
        }
        if (alpha <= 8) return;
        float hover = HudAnimUtil.easeOutCubic(hoverProgress);
        HudDraw.fill(graphics, bounds.x(), bounds.y() + 2, bounds.right(), bounds.bottom() - 2,
                HudAnimUtil.withAlpha(themeColor, Math.round(20 * hover * alpha / 255f)));
        centeredLabel(graphics, font, Component.literal(label), bounds, .95f, 0,
                HudAnimUtil.withAlpha(HudAnimUtil.lerpColor(0xEEEEEE, 0xFFFFFF, hover), alpha), true);
        HudDraw.fill(graphics, bounds.x(), bounds.bottom() - 2, bounds.right(), bounds.bottom(),
                HudAnimUtil.withAlpha(themeColor, Math.round((205 + 50 * hover) * alpha / 255f)));
        if (unread) JournalUnreadBadgeRenderer.draw(graphics, bounds.right() - 4, bounds.y() + 4, alpha);
    }

    public static void drawCyberButton(GuiGraphics graphics, Font font, HudRect bounds,
                                       String text, int themeColor, float hoverProgress,
                                       boolean hovered, float alphaFactor) {
        if (HudDraw.isStyled()) {
            HudStyleRenderer.Action action = ((themeColor >>> 16) & 255) > 180 && ((themeColor >>> 8) & 255) < 100
                    ? HudStyleRenderer.Action.DANGER : HudStyleRenderer.Action.PRIMARY;
            drawStyledButton(graphics, font, bounds, text, action, hovered, alphaFactor, themeColor);
            return;
        }
        HudCursorManager.requestPointer(hovered && alphaFactor > 0.05f);
        int backgroundAlpha = (int) ((0x33 + 0x44 * hoverProgress) * alphaFactor);
        int borderAlpha = (int) ((0x66 + 0x99 * hoverProgress) * alphaFactor);
        int borderRgb = hovered ? themeColor & 0xFFFFFF : 0xCCCCCC;
        HudDraw.fill(graphics, bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                HudAnimUtil.withAlpha(0x000000, backgroundAlpha));
        drawFrame(graphics, bounds, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        if (alphaFactor <= 0.05f) return;

        centeredLabel(graphics, font, Component.literal(text), bounds, .9f, 0,
                HudAnimUtil.withAlpha(0xFFFFFF, (int) (255 * alphaFactor)), true);
    }

    public static void drawCompactButton(GuiGraphics graphics, Font font, HudRect bounds,
                                         String label, int themeColor, float alphaFactor,
                                         boolean hovered, float textScale) {
        if (HudDraw.isStyled()) { drawStyledButton(graphics, font, bounds, label, HudStyleRenderer.Action.SECONDARY, hovered, alphaFactor, themeColor); return; }
        HudCursorManager.requestPointer(hovered && alphaFactor > 0.05f);
        int backgroundAlpha = hovered
                ? (int) (0.25f * alphaFactor)
                : (int) (0.10f * alphaFactor);
        HudDraw.fill(graphics, bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                HudAnimUtil.withAlpha(themeColor, backgroundAlpha));
        HudDraw.fill(graphics, bounds.x(), bounds.y(), bounds.x() + 1, bounds.bottom(),
                HudAnimUtil.withAlpha(themeColor, (int) (0.5f * alphaFactor)));
        centeredLabel(graphics, font, Component.literal(label), bounds, textScale, 0,
                HudAnimUtil.withAlpha(0xFFFFFF, (int) (255 * alphaFactor)), false);
    }

    public static void drawFilterTab(GuiGraphics graphics, Font font, HudRect bounds,
                                     String label, int themeColor, int alpha,
                                     boolean active, boolean hovered, float textScale) {
        if (HudDraw.isStyled()) { drawStyledButton(graphics, font, bounds, label, active ? HudStyleRenderer.Action.PRIMARY : HudStyleRenderer.Action.SECONDARY, hovered, alpha / 255f, themeColor); return; }
        HudCursorManager.requestPointer(hovered && alpha > 8);
        int background = active
                ? HudAnimUtil.withAlpha(themeColor, Math.round(alpha * 0.15f))
                : hovered ? HudAnimUtil.withAlpha(0x334455, alpha) : 0;
        if (background != 0) HudDraw.fill(graphics, bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), background);
        int lineColor = active ? themeColor : 0x334455;
        HudDraw.fill(graphics, bounds.x(), bounds.bottom() - 1, bounds.right(), bounds.bottom(),
                HudAnimUtil.withAlpha(lineColor, alpha));
        int textColor = active ? 0xFFFFFF : hovered ? 0xCCCCCC : 0x778899;
        centeredLabel(graphics, font, Component.literal(label), bounds, textScale, 0,
                HudAnimUtil.withAlpha(textColor, alpha), false);
    }

    public static void drawToggleButton(GuiGraphics graphics, Font font, HudRect bounds,
                                        String label, int themeColor, int alpha,
                                        boolean active, float textScale) {
        drawToggleButton(graphics, font, bounds, Component.literal(label), themeColor, alpha, active, textScale);
    }

    public static void drawToggleButton(GuiGraphics graphics, Font font, HudRect bounds,
                                        Component label, int themeColor, int alpha,
                                        boolean active, float textScale) {
        if (HudDraw.isStyled()) { drawStyledButton(graphics, font, bounds, label, active ? HudStyleRenderer.Action.PRIMARY : HudStyleRenderer.Action.SECONDARY, false, alpha / 255f, themeColor); return; }
        int background = active ? 0x182026 : 0x05060A;
        HudDraw.fill(graphics, bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                HudAnimUtil.withAlpha(background, (int) ((active ? 0xAA : 0x66) * (alpha / 255f))));
        HudDraw.fill(graphics, bounds.x(), bounds.bottom() - 1, bounds.right(), bounds.bottom(),
                HudAnimUtil.withAlpha(active ? themeColor : 0x555555, alpha));
        centeredLabel(graphics, font, label, bounds, textScale, 0,
                HudAnimUtil.withAlpha(active ? 0xFFFFFF : 0xAAB4C0, alpha), false);
    }

    private static void drawStyledButton(GuiGraphics g, Font font, HudRect bounds, String text,
                                      HudStyleRenderer.Action action, boolean hovered, float alpha, int theme) {
        drawStyledButton(g, font, bounds, Component.literal(text), action, hovered, alpha, theme);
    }

    private static void drawStyledButton(GuiGraphics g, Font font, HudRect bounds, Component text,
                                      HudStyleRenderer.Action action, boolean hovered, float alpha, int theme) {
        if (alpha <= .02f) return;
        HudCursorManager.requestPointer(hovered);
        int offset = HudStyleRenderer.button(g, bounds.x(), bounds.y(), bounds.width(), bounds.height(), action, hovered, true, alpha, theme);
        centeredLabel(g, font, text, bounds, .9f, offset,
                HudStyleRenderer.buttonText(action, true, alpha), action != HudStyleRenderer.Action.SECONDARY);
    }

    private static void centeredLabel(GuiGraphics graphics, Font font, Component label, HudRect bounds,
                                      float requestedScale, int offset, int color, boolean shadow) {
        float scale = JournalScaledTextRenderer.readableScale(graphics, requestedScale);
        var visible = StyledTextUtil.fitSingleLine(font, label, Math.max(1, (int) ((bounds.width() - 8) / scale)));
        JournalScaledTextRenderer.centered(graphics, font, visible,
                new HudRect(bounds.x(), bounds.y() + offset, bounds.width(), bounds.height() - offset), scale, color, shadow);
    }

    private static void drawFrame(GuiGraphics graphics, HudRect bounds, int color) {
        HudDraw.fill(graphics, bounds.x(), bounds.y(), bounds.right(), bounds.y() + 1, color);
        HudDraw.fill(graphics, bounds.x(), bounds.bottom() - 1, bounds.right(), bounds.bottom(), color);
        HudDraw.fill(graphics, bounds.x(), bounds.y(), bounds.x() + 1, bounds.bottom(), color);
        HudDraw.fill(graphics, bounds.right() - 1, bounds.y(), bounds.right(), bounds.bottom(), color);
    }
}
