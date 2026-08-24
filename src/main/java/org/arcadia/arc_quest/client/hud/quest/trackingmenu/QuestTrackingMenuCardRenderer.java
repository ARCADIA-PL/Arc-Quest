package org.arcadia.arc_quest.client.hud.quest.trackingmenu;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.quest.splash.QuestSplashCardRenderer;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;

import java.util.List;

final class QuestTrackingMenuCardRenderer {
    private static final int CARD_PADDING = 9;
    private static final float HEADER_SCALE = 0.86f;
    private static final float DESCRIPTION_SCALE = 0.72f;
    private static final float PROGRESS_SCALE = 0.74f;
    private static final float OBJECTIVE_SCALE = 0.72f;
    private static final float FOOTER_SCALE = 0.70f;

    private QuestTrackingMenuCardRenderer() {
    }

    static void render(GuiGraphics graphics, Font font, QuestTrackingMenuEntry entry,
                       QuestTrackingMenuPhaseEntry selectedPhase,
                       int x, int y, int width, int height,
                       float alpha, float detailAlpha, boolean hovered) {
        int themeColor = entry.definition().getThemeColor();
        float detailProgress = Math.max(0f, Math.min(1f, detailAlpha));
        QuestSplashCardRenderer.render(graphics, entry.definition(), entry.splashTexture(),
                x, y, width, height, themeColor, alpha);

        int bottomShadeHeight = Math.max(24, height / 4);
        graphics.fillGradient(x, y + height - bottomShadeHeight, x + width, y + height,
                HudAnimUtil.withAlpha(0x000000, 0), HudAnimUtil.withAlpha(0x000000, Math.round(220 * alpha)));

        float contentScale = QuestTrackingMenuLayout.contentScale(width, height);
        int logicalWidth = Math.max(1, Math.round(width / contentScale));
        int logicalHeight = Math.max(1, Math.round(height / contentScale));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0f);
        graphics.pose().scale(contentScale, contentScale, 1f);

        int titleColor = HudAnimUtil.withAlpha(0xFFFFFF,
                Math.round(255 * alpha * (1f - detailProgress)));
        if ((titleColor >>> 24) > 3) {
            drawEllipsized(graphics, font, entry.definition().getDisplayName(),
                    CARD_PADDING,
                    logicalHeight - font.lineHeight - 7,
                    logicalWidth - CARD_PADDING * 2, titleColor);
        }

        if (detailProgress > 0.02f) {
            renderPhaseSummary(graphics, font, entry, selectedPhase, 0, 0, logicalWidth, logicalHeight,
                    themeColor, alpha * detailProgress);
        }
        graphics.pose().popPose();
    }

    private static void renderPhaseSummary(GuiGraphics graphics, Font font,
                                           QuestTrackingMenuEntry entry,
                                           QuestTrackingMenuPhaseEntry selectedPhase,
                                           int x, int y, int width, int height,
                                           int themeColor, float alpha) {
        int overlayAlpha = Math.round(225 * alpha);
        graphics.fill(x, y, x + width, y + height, HudAnimUtil.withAlpha(0x03060B, overlayAlpha));
        graphics.fill(x, y, x + 3, y + height, HudAnimUtil.withAlpha(themeColor, Math.round(255 * alpha)));

        PhaseDefinition phase = selectedPhase == null ? null : selectedPhase.definition();
        String phaseId = selectedPhase == null ? null : selectedPhase.phaseId();
        Component phaseName = phase == null
                ? Component.translatable("arc_quest.gui.tracking_menu.no_phase")
                : phase.getDisplayName();
        Component phaseHeader = Component.translatable("arc_quest.gui.tracking_menu.current_phase", phaseName);
        int textX = x + CARD_PADDING + 3;
        int textY = y + CARD_PADDING;
        int textWidth = width - CARD_PADDING * 2 - 3;
        int bottomY = y + height - CARD_PADDING;

        drawScaledEllipsized(graphics, font, phaseHeader, textX, textY, textWidth,
                HudAnimUtil.withAlpha(themeColor, Math.round(255 * alpha)), HEADER_SCALE, false);
        textY += scaledLineHeight(font, HEADER_SCALE) + 4;
        graphics.fill(textX, textY, textX + textWidth, textY + 1,
                HudAnimUtil.withAlpha(themeColor, Math.round(90 * alpha)));
        textY += 4;

        if (phase == null || phaseId == null || phase.getObjectives().isEmpty()) {
            drawScaled(graphics, font,
                    Component.translatable("arc_quest.gui.tracking_menu.no_objectives"),
                    textX, textY, HudAnimUtil.withAlpha(0xAAAAAA, Math.round(255 * alpha)),
                    DESCRIPTION_SCALE, false);
            return;
        }

        Component description = phase.getDescription();
        if (!description.getString().isBlank()) {
            int logicalWidth = logicalWidth(textWidth, DESCRIPTION_SCALE);
            List<FormattedCharSequence> descriptionLines = font.split(description, logicalWidth);
            int descriptionLineCount = Math.min(height >= 120 ? 3 : 2, descriptionLines.size());
            int lineAdvance = scaledLineHeight(font, DESCRIPTION_SCALE) + 2;
            for (int lineIndex = 0; lineIndex < descriptionLineCount; lineIndex++) {
                if (textY + lineAdvance > bottomY) break;
                drawScaled(graphics, font, descriptionLines.get(lineIndex), textX, textY,
                        HudAnimUtil.withAlpha(0xAAB1BC, Math.round(235 * alpha)),
                        DESCRIPTION_SCALE, false);
                textY += lineAdvance;
            }
            textY += 2;
        }

        List<ObjectiveEntry> objectives = phase.getObjectives();
        int completedObjectives = 0;
        int visibleObjectives = 0;
        for (int objectiveIndex = 0; objectiveIndex < objectives.size(); objectiveIndex++) {
            ObjectiveEntry objective = objectives.get(objectiveIndex);
            if (objective.isHidden()) continue;
            int required = Math.max(1, objective.getRequiredCount());
            int progress = entry.runtime().getObjectiveProgress(phaseId, objectiveIndex);
            visibleObjectives++;
            if (progress >= required) completedObjectives++;
        }

        Component phaseProgress = Component.translatable("arc_quest.gui.tracking_menu.phase_progress",
                completedObjectives, visibleObjectives);
        drawScaled(graphics, font, phaseProgress, textX, textY,
                HudAnimUtil.withAlpha(themeColor, Math.round(245 * alpha)), PROGRESS_SCALE, false);
        textY += scaledLineHeight(font, PROGRESS_SCALE) + 4;

        int renderedObjectives = 0;
        int objectiveLineAdvance = scaledLineHeight(font, OBJECTIVE_SCALE) + 2;
        int logicalObjectiveWidth = logicalWidth(textWidth, OBJECTIVE_SCALE);
        for (int objectiveIndex = 0; objectiveIndex < objectives.size(); objectiveIndex++) {
            ObjectiveEntry objective = objectives.get(objectiveIndex);
            if (objective.isHidden()) continue;
            int progress = entry.runtime().getObjectiveProgress(phaseId, objectiveIndex);
            int required = Math.max(1, objective.getRequiredCount());
            boolean complete = progress >= required;
            Component line = objective.isBooleanProgress()
                    ? Component.literal(complete ? "[x] " : "[ ] ").append(objective.getDisplayText())
                    : Component.literal(complete ? "[x] " : "[ ] ")
                    .append(objective.getDisplayText())
                    .append(Component.literal("  " + progress + "/" + required));
            if (objective.isOptional()) {
                line = line.copy().append(Component.translatable("arc_quest.gui.tracking_menu.optional"));
            }

            List<FormattedCharSequence> wrapped = font.split(line, logicalObjectiveWidth);
            if (wrapped.isEmpty()) continue;
            int objectiveLineCount = Math.min(2, wrapped.size());
            int requiredHeight = objectiveLineCount * objectiveLineAdvance;
            if (textY + requiredHeight > bottomY) break;
            int color = complete ? 0x88FF88 : 0xDDDDDD;
            for (int lineIndex = 0; lineIndex < objectiveLineCount; lineIndex++) {
                drawScaled(graphics, font, wrapped.get(lineIndex), textX, textY,
                        HudAnimUtil.withAlpha(color, Math.round(255 * alpha)), OBJECTIVE_SCALE, false);
                textY += objectiveLineAdvance;
            }
            renderedObjectives++;
        }

        int remainingObjectives = visibleObjectives - renderedObjectives;
        if (remainingObjectives > 0
                && textY + scaledLineHeight(font, FOOTER_SCALE) <= bottomY) {
            drawScaled(graphics, font,
                    Component.translatable("arc_quest.gui.tracking_menu.more_objectives", remainingObjectives),
                    textX, textY, HudAnimUtil.withAlpha(0x8E96A3, Math.round(230 * alpha)),
                    FOOTER_SCALE, false);
        }
    }

    private static int logicalWidth(int physicalWidth, float scale) {
        return Math.max(1, (int) Math.floor(physicalWidth / scale));
    }

    private static int scaledLineHeight(Font font, float scale) {
        return Math.max(1, Math.round(font.lineHeight * scale));
    }

    private static void drawScaled(GuiGraphics graphics, Font font, Component text,
                                   int x, int y, int color, float scale, boolean shadow) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0f);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, text, 0, 0, color, shadow);
        graphics.pose().popPose();
    }

    private static void drawScaled(GuiGraphics graphics, Font font, FormattedCharSequence text,
                                   int x, int y, int color, float scale, boolean shadow) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0f);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, text, 0, 0, color, shadow);
        graphics.pose().popPose();
    }

    private static void drawScaledEllipsized(GuiGraphics graphics, Font font, Component text,
                                             int x, int y, int maxWidth, int color,
                                             float scale, boolean shadow) {
        int logicalMaxWidth = logicalWidth(maxWidth, scale);
        Component rendered = text;
        if (font.width(text) > logicalMaxWidth) {
            String ellipsis = "...";
            int allowed = Math.max(0, logicalMaxWidth - font.width(ellipsis));
            rendered = Component.literal(font.plainSubstrByWidth(text.getString(), allowed) + ellipsis);
        }
        drawScaled(graphics, font, rendered, x, y, color, scale, shadow);
    }

    private static void drawEllipsized(GuiGraphics graphics, Font font, Component text,
                                       int x, int y, int maxWidth, int color) {
        if (font.width(text) <= maxWidth) {
            graphics.drawString(font, text, x, y, color, true);
            return;
        }
        String ellipsis = "...";
        int allowed = Math.max(0, maxWidth - font.width(ellipsis));
        graphics.drawString(font, font.plainSubstrByWidth(text.getString(), allowed) + ellipsis,
                x, y, color, true);
    }
}
