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

    private QuestTrackingMenuCardRenderer() {
    }

    static void render(GuiGraphics graphics, Font font, QuestTrackingMenuEntry entry,
                       int x, int y, int width, int height,
                       float alpha, float detailAlpha, boolean hovered) {
        int themeColor = entry.definition().getThemeColor();
        float detailProgress = HudAnimUtil.smoothStep(Math.max(0f, Math.min(1f, detailAlpha)));
        QuestSplashCardRenderer.render(graphics, entry.definition(), entry.splashTexture(),
                x, y, width, height, themeColor, alpha);

        int bottomShadeHeight = Math.max(24, height / 4);
        graphics.fillGradient(x, y + height - bottomShadeHeight, x + width, y + height,
                HudAnimUtil.withAlpha(0x000000, 0), HudAnimUtil.withAlpha(0x000000, Math.round(220 * alpha)));

        int titleColor = HudAnimUtil.withAlpha(0xFFFFFF,
                Math.round(255 * alpha * (1f - detailProgress)));
        if ((titleColor >>> 24) > 3) {
            drawEllipsized(graphics, font, entry.definition().getDisplayName(),
                    x + CARD_PADDING,
                    y + height - font.lineHeight - 7 + Math.round(detailProgress * 4f),
                    width - CARD_PADDING * 2, titleColor);
        }

        if (hovered && detailProgress < 0.99f) {
            int hoverColor = HudAnimUtil.withAlpha(0xFFFFFF,
                    Math.round(42 * alpha * (1f - detailProgress)));
            graphics.fill(x, y, x + width, y + height, hoverColor);
        }

        if (detailProgress > 0.01f) {
            renderPhaseSummary(graphics, font, entry, x, y, width, height,
                    themeColor, alpha * detailProgress, detailProgress);
        }
    }

    private static void renderPhaseSummary(GuiGraphics graphics, Font font,
                                           QuestTrackingMenuEntry entry,
                                           int x, int y, int width, int height,
                                           int themeColor, float alpha, float revealProgress) {
        int overlayAlpha = Math.round(225 * alpha);
        graphics.fill(x, y, x + width, y + height, HudAnimUtil.withAlpha(0x03060B, overlayAlpha));
        graphics.fill(x, y, x + 3, y + height, HudAnimUtil.withAlpha(themeColor, Math.round(255 * alpha)));

        PhaseDefinition phase = entry.phase();
        Component phaseName = phase == null
                ? Component.translatable("arc_quest.gui.tracking_menu.no_phase")
                : phase.getDisplayName();
        Component phaseHeader = Component.translatable("arc_quest.gui.tracking_menu.current_phase", phaseName);
        int textX = x + CARD_PADDING + 3;
        int textY = y + CARD_PADDING + Math.round((1f - revealProgress) * 7f);
        int textWidth = width - CARD_PADDING * 2 - 3;
        drawEllipsized(graphics, font, phaseHeader, textX, textY, textWidth,
                HudAnimUtil.withAlpha(themeColor, Math.round(255 * alpha)));
        textY += font.lineHeight + 5;

        if (phase == null || entry.phaseId() == null || phase.getObjectives().isEmpty()) {
            graphics.drawString(font, Component.translatable("arc_quest.gui.tracking_menu.no_objectives"),
                    textX, textY, HudAnimUtil.withAlpha(0xAAAAAA, Math.round(255 * alpha)), true);
            return;
        }

        List<ObjectiveEntry> objectives = phase.getObjectives();
        Component description = phase.getDescription();
        if (!description.getString().isBlank()) {
            List<FormattedCharSequence> descriptionLines = font.split(description, textWidth);
            int descriptionLineCount = Math.min(2, descriptionLines.size());
            for (int lineIndex = 0; lineIndex < descriptionLineCount; lineIndex++) {
                graphics.drawString(font, descriptionLines.get(lineIndex), textX, textY,
                        HudAnimUtil.withAlpha(0xAAB1BC, Math.round(235 * alpha)), true);
                textY += font.lineHeight + 2;
            }
            textY += 2;
        }

        int completedObjectives = 0;
        int visibleObjectives = 0;
        for (int objectiveIndex = 0; objectiveIndex < objectives.size(); objectiveIndex++) {
            ObjectiveEntry objective = objectives.get(objectiveIndex);
            int required = Math.max(1, objective.getRequiredCount());
            int progress = entry.runtime().getObjectiveProgress(entry.phaseId(), objectiveIndex);
            if (!objective.isHidden()) {
                visibleObjectives++;
                if (progress >= required) completedObjectives++;
            }
        }
        Component phaseProgress = Component.translatable("arc_quest.gui.tracking_menu.phase_progress",
                completedObjectives, visibleObjectives);
        graphics.drawString(font, phaseProgress, textX, textY,
                HudAnimUtil.withAlpha(themeColor, Math.round(240 * alpha)), true);
        textY += font.lineHeight + 5;

        int renderedObjectives = 0;
        int bottomY = y + height - CARD_PADDING;
        for (int objectiveIndex = 0; objectiveIndex < objectives.size(); objectiveIndex++) {
            ObjectiveEntry objective = objectives.get(objectiveIndex);
            if (objective.isHidden()) continue;
            int progress = entry.runtime().getObjectiveProgress(entry.phaseId(), objectiveIndex);
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
            List<FormattedCharSequence> wrapped = font.split(line, textWidth);
            if (wrapped.isEmpty()) continue;
            int color = complete ? 0x88FF88 : 0xDDDDDD;
            int objectiveLineCount = Math.min(2, wrapped.size());
            if (textY + objectiveLineCount * (font.lineHeight + 2) > bottomY) break;
            for (int lineIndex = 0; lineIndex < objectiveLineCount; lineIndex++) {
                graphics.drawString(font, wrapped.get(lineIndex), textX, textY,
                        HudAnimUtil.withAlpha(color, Math.round(255 * alpha)), true);
                textY += font.lineHeight + 2;
            }
            renderedObjectives++;
        }

        int remainingObjectives = visibleObjectives - renderedObjectives;
        if (remainingObjectives > 0 && textY + font.lineHeight <= bottomY) {
            graphics.drawString(font,
                    Component.translatable("arc_quest.gui.tracking_menu.more_objectives", remainingObjectives),
                    textX, textY, HudAnimUtil.withAlpha(0x8E96A3, Math.round(230 * alpha)), true);
        }
    }

    private static void drawEllipsized(GuiGraphics graphics, Font font, Component text,
                                       int x, int y, int maxWidth, int color) {
        if (font.width(text) <= maxWidth) {
            graphics.drawString(font, text, x, y, color, true);
            return;
        }
        String value = text.getString();
        String ellipsis = "...";
        int allowed = Math.max(0, maxWidth - font.width(ellipsis));
        graphics.drawString(font, font.plainSubstrByWidth(value, allowed) + ellipsis,
                x, y, color, true);
    }
}
