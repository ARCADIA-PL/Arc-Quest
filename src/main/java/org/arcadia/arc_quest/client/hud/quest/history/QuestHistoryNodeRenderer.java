package org.arcadia.arc_quest.client.hud.quest.history;

import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;

import java.util.HashMap;
import java.util.Map;

final class QuestHistoryNodeRenderer {

    static final int CARD_WIDTH = 112;
    static final int CARD_HEIGHT = 63;
    private static final int LABEL_HEIGHT = 16;
    private static final Map<String, Float> HOVER_PROGRESS = new HashMap<>();

    private QuestHistoryNodeRenderer() {
    }

    static void reset() {
        HOVER_PROGRESS.clear();
    }

    static void render(GuiGraphics graphics, Font font, QuestHistoryNodeData node, boolean hovered, boolean selected,
                       int themeColor, int alpha, float alphaFactor, float deltaTime) {
        float hover = HOVER_PROGRESS.getOrDefault(node.id(), 0f);
        hover = HudAnimUtil.smoothExp(hover, hovered || selected ? 1f : 0f, 14f, deltaTime);
        HOVER_PROGRESS.put(node.id(), hover);

        int stateColor = node.completed() ? 0x69E79A : node.active() ? themeColor : 0x68717D;
        float pulse = node.active() ? 0.5f + 0.5f * (float) Math.sin(Util.getMillis() / 420.0) : 0f;
        float scale = 1f + hover * 0.035f + pulse * 0.008f;
        int x = -CARD_WIDTH / 2;
        int y = -CARD_HEIGHT / 2;

        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1f);

        graphics.fill(x + 4, y + 4, x + CARD_WIDTH + 5, y + CARD_HEIGHT + 5,
                HudAnimUtil.withAlpha(0x000000, Math.round(95 * alphaFactor)));
        drawFrame(graphics, x - 3, y - 3, CARD_WIDTH, CARD_HEIGHT, 1,
                HudAnimUtil.withAlpha(0x3D4652, Math.round((80 + hover * 55) * alphaFactor)));
        graphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT,
                HudAnimUtil.withAlpha(0x0A0D12, Math.round(245 * alphaFactor)));

        QuestHistoryImageRenderer.RenderResult imageResult = QuestHistoryImageRenderer.renderCover(
                graphics, node.image(), x + 2, y + 2, CARD_WIDTH - 4, CARD_HEIGHT - 4,
                alphaFactor, !node.reached(), themeColor);
        if (imageResult == QuestHistoryImageRenderer.RenderResult.UNAVAILABLE) {
            renderFallback(graphics, font, node.reached(), x, y, stateColor, alpha);
        } else if (!node.reached()) {
            renderQuestionMark(graphics, font, 0, -5, alpha);
        }

        graphics.fill(x + 2, y + CARD_HEIGHT - LABEL_HEIGHT - 2, x + CARD_WIDTH - 2, y + CARD_HEIGHT - 2,
                HudAnimUtil.withAlpha(0x07090D, Math.round((218 + hover * 20) * alphaFactor)));
        graphics.fill(x + 2, y + CARD_HEIGHT - LABEL_HEIGHT - 2, x + 5, y + CARD_HEIGHT - 2,
                HudAnimUtil.withAlpha(stateColor, alpha));

        String label = font.plainSubstrByWidth(node.displayName().getString(), CARD_WIDTH - 18);
        float textScale = 0.72f;
        graphics.pose().pushPose();
        graphics.pose().translate(x + 9, y + CARD_HEIGHT - LABEL_HEIGHT + 1, 2f);
        graphics.pose().scale(textScale, textScale, 1f);
        graphics.drawString(font, label, 0, 0, HudAnimUtil.withAlpha(0xF0F3F7, alpha), false);
        graphics.pose().popPose();

        int frameColor = selected ? 0xFFFFFF : hovered ? stateColor : 0x89939F;
        int frameAlpha = Math.round((selected ? 255 : 115 + hover * 110) * alphaFactor);
        drawFrame(graphics, x, y, CARD_WIDTH, CARD_HEIGHT, selected ? 2 : 1,
                HudAnimUtil.withAlpha(frameColor, frameAlpha));
        if (selected) drawSelectionCorners(graphics, x - 3, y - 3, CARD_WIDTH + 6, CARD_HEIGHT + 6,
                HudAnimUtil.withAlpha(stateColor, alpha));

        graphics.pose().popPose();
    }

    private static void renderFallback(GuiGraphics graphics, Font font, boolean reached, int x, int y,
                                       int stateColor, int alpha) {
        graphics.fill(x + 2, y + 2, x + CARD_WIDTH - 2, y + CARD_HEIGHT - 2,
                HudAnimUtil.withAlpha(0x11161D, Math.min(alpha, 235)));
        if (!reached) {
            renderQuestionMark(graphics, font, 0, -5, alpha);
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, -6, 1f);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(45f));
        graphics.fill(-9, -9, 9, 9, HudAnimUtil.withAlpha(0x06090D, alpha));
        drawFrame(graphics, -8, -8, 16, 16, 2, HudAnimUtil.withAlpha(stateColor, alpha));
        graphics.pose().popPose();
    }

    private static void renderQuestionMark(GuiGraphics graphics, Font font, int centerX, int centerY, int alpha) {
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 3f);
        graphics.pose().scale(1.65f, 1.65f, 1f);
        graphics.drawCenteredString(font, "?", 0, -font.lineHeight / 2,
                HudAnimUtil.withAlpha(0xFFFFFF, Math.min(alpha, 235)));
        graphics.pose().popPose();
    }

    private static void drawSelectionCorners(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        int length = 9;
        graphics.fill(x, y, x + length, y + 2, color);
        graphics.fill(x, y, x + 2, y + length, color);
        graphics.fill(x + width - length, y, x + width, y + 2, color);
        graphics.fill(x + width - 2, y, x + width, y + length, color);
        graphics.fill(x, y + height - 2, x + length, y + height, color);
        graphics.fill(x, y + height - length, x + 2, y + height, color);
        graphics.fill(x + width - length, y + height - 2, x + width, y + height, color);
        graphics.fill(x + width - 2, y + height - length, x + width, y + height, color);
    }

    private static void drawFrame(GuiGraphics graphics, int x, int y, int width, int height, int thickness, int color) {
        graphics.fill(x, y, x + width, y + thickness, color);
        graphics.fill(x, y + height - thickness, x + width, y + height, color);
        graphics.fill(x, y + thickness, x + thickness, y + height - thickness, color);
        graphics.fill(x + width - thickness, y + thickness, x + width, y + height - thickness, color);
    }
}
