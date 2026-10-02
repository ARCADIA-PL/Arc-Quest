package org.arcadia.arc_quest.client.hud.quest.history;

import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudText;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconSession;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.CollectionEntryIcons;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiScreenIngredients;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

final class QuestHistoryNodeRenderer {

    static final int CARD_WIDTH = 112;
    static final int CARD_HEIGHT = 63;
    static final int VISUAL_LEFT_OVERHANG = 6;
    static final int VISUAL_RIGHT_OVERHANG = 4;
    static final int VISUAL_BOTTOM_OVERHANG = 8;
    private static final int LABEL_HEIGHT = 12;
    private static final int LABEL_WIDTH = 80;
    private static final int LABEL_ACCENT_WIDTH = 3;
    private static final float LABEL_SCALE = 0.82f;
    private static final Map<String, Float> HOVER_PROGRESS = new HashMap<>();
    private static final ObjectiveIconSession COLLECTION_ICONS = new ObjectiveIconSession();

    private QuestHistoryNodeRenderer() {
    }

    static void reset() {
        HOVER_PROGRESS.clear();
        COLLECTION_ICONS.clear();
    }

    static void beginFrame() { COLLECTION_ICONS.beginFrame(); }
    static void endFrame() { COLLECTION_ICONS.endFrame(); }

    static void render(GuiGraphics graphics, Font font, QuestHistoryNodeData node, boolean hovered, boolean selected,
                       int themeColor, float alphaFactor, float titleVisibility, float deltaTime, String questId,
                       float mouseX, float mouseY, boolean interactive) {
        float hover = updateHover(node.id(), hovered, selected, deltaTime);
        if (node.isBinding() || node.sheet() != null) {
            renderCollection(graphics, font, node, selected, hovered, themeColor, alphaFactor, hover,
                    questId, mouseX, mouseY, interactive);
            return;
        }

        float lockedOpacity = node.reached() ? 1f : Math.max(0.22f, 0.58f - node.depth() * 0.055f);
        float cardAlphaFactor = alphaFactor * lockedOpacity;
        int cardAlpha = Math.round(255 * cardAlphaFactor);
        int stateColor = node.completed() ? 0x69E79A : node.active() ? themeColor : 0x68717D;
        float pulse = node.active() ? 0.5f + 0.5f * (float) Math.sin(Util.getMillis() / 420.0) : 0f;
        float scale = 1f + hover * 0.035f + pulse * 0.008f;
        int x = -CARD_WIDTH / 2;
        int y = -CARD_HEIGHT / 2;

        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1f);

        graphics.fill(x + 4, y + 4, x + CARD_WIDTH + 5, y + CARD_HEIGHT + 5,
                HudAnimUtil.withAlpha(0x000000, Math.round(95 * cardAlphaFactor)));
        drawFrame(graphics, x - 3, y - 3, CARD_WIDTH, CARD_HEIGHT, 1,
                HudAnimUtil.withAlpha(0x3D4652, Math.round((80 + hover * 55) * cardAlphaFactor)));
        graphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT,
                HudAnimUtil.withAlpha(0x0A0D12, Math.round(245 * cardAlphaFactor)));

        QuestHistoryImageRenderer.RenderResult imageResult = QuestHistoryImageRenderer.renderCover(
                graphics, node.image(), x + 2, y + 2, CARD_WIDTH - 4, CARD_HEIGHT - 4,
                cardAlphaFactor, !node.reached(), themeColor);
        if (imageResult == QuestHistoryImageRenderer.RenderResult.UNAVAILABLE) {
            renderFallback(graphics, font, node.reached(), x, y, stateColor, cardAlpha);
        } else if (!node.reached()) {
            renderQuestionMark(graphics, font, 0, -5, cardAlpha);
        }

        int frameColor = selected ? 0xFFFFFF : hovered ? stateColor : 0x89939F;
        int frameAlpha = Math.round((selected ? 255 : 115 + hover * 110) * cardAlphaFactor);
        drawFrame(graphics, x, y, CARD_WIDTH, CARD_HEIGHT, selected ? 2 : 1,
                HudAnimUtil.withAlpha(frameColor, frameAlpha));
        if (selected) drawSelectionCorners(graphics, x - 3, y - 3, CARD_WIDTH + 6, CARD_HEIGHT + 6,
                HudAnimUtil.withAlpha(stateColor, cardAlpha));

        float titleAlphaFactor = cardAlphaFactor * titleVisibility;
        int labelAlpha = Math.round(cardAlpha * titleVisibility);
        if (labelAlpha > 4) {
            int labelX = x - 6;
            int labelY = y + CARD_HEIGHT - 7;
            int backgroundAlpha = Math.round((100 + hover * 16) * titleAlphaFactor);
            graphics.fill(labelX, labelY, labelX + LABEL_WIDTH, labelY + LABEL_HEIGHT,
                    HudAnimUtil.withAlpha(0x07090D, backgroundAlpha));
            int labelBorderColor = HudAnimUtil.withAlpha(stateColor, Math.round(labelAlpha * 0.82f));
            graphics.fill(labelX, labelY, labelX + 1, labelY + LABEL_HEIGHT, labelBorderColor);
            graphics.fill(labelX, labelY + LABEL_HEIGHT - 1,
                    labelX + LABEL_WIDTH, labelY + LABEL_HEIGHT, labelBorderColor);
            graphics.fill(labelX + 2, labelY + 2, labelX + 2 + LABEL_ACCENT_WIDTH, labelY + LABEL_HEIGHT - 2,
                    HudAnimUtil.withAlpha(stateColor, labelAlpha));

            int textX = labelX + LABEL_ACCENT_WIDTH + 8;
            int textRight = labelX + LABEL_WIDTH - 7;
            int maxLabelWidth = Math.round((textRight - textX) / LABEL_SCALE);
            String title = node.reached() ? node.displayName().getString() : "？？？";
            String label = marqueeText(font, title, maxLabelWidth);
            graphics.pose().pushPose();
            graphics.pose().translate(textX,
                    labelY + (LABEL_HEIGHT - font.lineHeight * LABEL_SCALE) / 2f, 3f);
            graphics.pose().scale(LABEL_SCALE, LABEL_SCALE, 1f);
            graphics.drawString(font, label, 0, 0, HudAnimUtil.withAlpha(0xF0F3F7, labelAlpha), false);
            graphics.pose().popPose();
        }

        graphics.pose().popPose();
    }

    private static void renderCollection(GuiGraphics graphics, Font font, QuestHistoryNodeData node,
            boolean selected, boolean hovered, int theme, float parentAlpha, float hover,
            String questId, float mouseX, float mouseY, boolean interactive) {
        float opacity = parentAlpha * (node.reached() ? 1f : .45f);
        int alpha = Math.round(255 * opacity);
        int accent = node.completed() ? 0x69E79A : node.active() ? theme : 0x718091;
        int x = -CARD_WIDTH / 2, y = -CARD_HEIGHT / 2;
        graphics.pose().pushPose();
        float cardScale = 1 + hover * .025f;
        graphics.pose().scale(cardScale, cardScale, 1);
        graphics.fill(x + 3, y + 4, x + CARD_WIDTH + 3, y + CARD_HEIGHT + 4, HudAnimUtil.withAlpha(0x000000, alpha / 3));
        graphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, HudAnimUtil.withAlpha(0x0A0D12, Math.round(235 * opacity)));
        drawFrame(graphics, x, y, CARD_WIDTH, CARD_HEIGHT, 1,
                HudAnimUtil.withAlpha(selected ? 0xFFFFFF : hovered ? accent : 0x65707C, Math.round((120 + hover * 100) * opacity)));
        graphics.fill(x, y, x + (node.isBinding() ? 1 : 3), y + CARD_HEIGHT, HudAnimUtil.withAlpha(accent, alpha));
        graphics.pose().pushPose();
        graphics.pose().translate(x + 7, y + 5, 1);
        graphics.pose().scale(.7f, .7f, 1);
        graphics.drawString(font, HudText.of(node.isBinding() ? "history.collection_binding" : "history.collection_phase"),
                0, 0, HudAnimUtil.withAlpha(accent, alpha), false);
        graphics.pose().popPose();
        int offset = 0;
        if (node.isBinding() && node.entry() != null && node.reached()) {
            var screen = Minecraft.getInstance().screen;
            boolean canInteract = interactive && screen instanceof QuestJournalScreen journal && journal.canQueryJei();
            boolean iconHovered = canInteract && mouseX / cardScale >= x + 4 && mouseX / cardScale <= x + 34
                    && mouseY / cardScale >= y + 17 && mouseY / cardScale <= y + 47;
            var icon = COLLECTION_ICONS.select(CollectionEntryIcons.context(questId, node.phaseId(), node.binding().bindingId(), node.entry()),
                    iconHovered, canInteract);
            if (icon.available()) {
                icon.render(graphics, x + 7, y + 20, 24, opacity); offset = 29;
                if (icon.isItem() && canInteract) {
                    JeiScreenIngredients.collectionItem(screen, graphics, questId, node.phaseId(), node.binding().bindingId(),
                            icon.stack(), x + 4, y + 17, 30, 30);
                    if (iconHovered && screen instanceof QuestJournalScreen journal) {
                        journal.setHoveredRewardTooltip(icon.stack());
                        journal.requestPointerCursor();
                    }
                }
            }
        }
        var title = node.renderTitle();
        var lines = font.split(title, Math.max(12, CARD_WIDTH - 14 - offset));
        for (int i = 0; i < Math.min(2, lines.size()); i++) graphics.drawString(font, lines.get(i),
                x + 7 + offset, y + 19 + i * font.lineHeight, HudAnimUtil.withAlpha(0xDCE2E7, alpha), false);
        String count = node.targetCount() > 0 ? node.completedCount() + "/" + node.targetCount() : "";
        if (!count.isEmpty()) graphics.drawString(font, count, x + CARD_WIDTH - 7 - font.width(count), y + CARD_HEIGHT - 14,
                HudAnimUtil.withAlpha(accent, alpha), false);
        if (node.optional()) graphics.drawString(font, HudText.of("history.collection_optional"), x + 7, y + CARD_HEIGHT - 14,
                HudAnimUtil.withAlpha(0x929DA8, alpha), false);
        graphics.pose().popPose();
    }

    static float updateHover(String nodeId, boolean hovered, boolean selected, float deltaTime) {
        float hover = HOVER_PROGRESS.getOrDefault(nodeId, 0f);
        hover = HudAnimUtil.smoothExp(hover, hovered || selected ? 1f : 0f, 14f, deltaTime);
        HOVER_PROGRESS.put(nodeId, hover);
        return hover;
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
        graphics.pose().translate(0, 0, 1f);
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

    private static String marqueeText(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth || text.isEmpty()) return text;
        String loop = text + "   ";
        int offset = (int) ((Util.getMillis() / 170L) % loop.length());
        String shifted = loop.substring(offset) + loop.substring(0, offset);
        return font.plainSubstrByWidth(shifted, maxWidth);
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
