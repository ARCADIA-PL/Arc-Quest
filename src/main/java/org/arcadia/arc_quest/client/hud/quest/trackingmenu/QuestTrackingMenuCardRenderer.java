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
    private static final int MAX_OBJECTIVE_LINES = 3;

    private QuestTrackingMenuCardRenderer() {
    }

    static void render(GuiGraphics graphics, Font font, QuestTrackingMenuEntry entry,
                       int x, int y, int width, int height,
                       float alpha, float detailAlpha, boolean tracked, boolean hovered) {
        int themeColor = entry.definition().getThemeColor();
        QuestSplashCardRenderer.render(graphics, entry.definition(), entry.splashTexture(),
                x, y, width, height, themeColor, alpha);

        int bottomShadeHeight = Math.max(24, height / 4);
        graphics.fillGradient(x, y + height - bottomShadeHeight, x + width, y + height,
                HudAnimUtil.withAlpha(0x000000, 0), HudAnimUtil.withAlpha(0x000000, Math.round(220 * alpha)));

        int titleColor = HudAnimUtil.withAlpha(0xFFFFFF, Math.round(255 * alpha * (1f - detailAlpha)));
        if ((titleColor >>> 24) > 3) {
            drawEllipsized(graphics, font, entry.definition().getDisplayName(),
                    x + CARD_PADDING, y + height - font.lineHeight - 7,
                    width - CARD_PADDING * 2, titleColor);
        }

        if (tracked) {
            int accent = HudAnimUtil.withAlpha(themeColor, Math.round(255 * alpha));
            graphics.fill(x - 2, y - 2, x + width + 2, y, accent);
            graphics.fill(x - 2, y + height, x + width + 2, y + height + 2, accent);
            graphics.fill(x - 2, y, x, y + height, accent);
            graphics.fill(x + width, y, x + width + 2, y + height, accent);
            Component trackedLabel = Component.translatable("arc_quest.gui.tracking_menu.tracked");
            int labelWidth = font.width(trackedLabel) + 8;
            graphics.fill(x + width - labelWidth, y, x + width, y + font.lineHeight + 6,
                    HudAnimUtil.withAlpha(0x000000, Math.round(190 * alpha)));
            graphics.drawString(font, trackedLabel, x + width - labelWidth + 4, y + 3,
                    HudAnimUtil.withAlpha(themeColor, Math.round(255 * alpha)), true);
        }

        if (hovered && detailAlpha < 0.01f) {
            int hoverColor = HudAnimUtil.withAlpha(0xFFFFFF, Math.round(42 * alpha));
            graphics.fill(x, y, x + width, y + height, hoverColor);
        }

        if (detailAlpha > 0.01f) {
            renderPhaseSummary(graphics, font, entry, x, y, width, height,
                    themeColor, alpha * detailAlpha);
        }
    }

    private static void renderPhaseSummary(GuiGraphics graphics, Font font,
                                           QuestTrackingMenuEntry entry,
                                           int x, int y, int width, int height,
                                           int themeColor, float alpha) {
        int overlayAlpha = Math.round(225 * alpha);
        graphics.fill(x, y, x + width, y + height, HudAnimUtil.withAlpha(0x03060B, overlayAlpha));
        graphics.fill(x, y, x + 3, y + height, HudAnimUtil.withAlpha(themeColor, Math.round(255 * alpha)));

        PhaseDefinition phase = entry.phase();
        Component phaseName = phase == null
                ? Component.translatable("arc_quest.gui.tracking_menu.no_phase")
                : phase.getDisplayName();
        Component phaseHeader = Component.translatable("arc_quest.gui.tracking_menu.current_phase", phaseName);
        int textX = x + CARD_PADDING + 3;
        int textY = y + CARD_PADDING;
        int textWidth = width - CARD_PADDING * 2 - 3;
        drawEllipsized(graphics, font, phaseHeader, textX, textY, textWidth,
                HudAnimUtil.withAlpha(themeColor, Math.round(255 * alpha)));
        textY += font.lineHeight + 5;

        if (phase == null || entry.phaseId() == null || phase.getObjectives().isEmpty()) {
            graphics.drawString(font, Component.translatable("arc_quest.gui.tracking_menu.no_objectives"),
                    textX, textY, HudAnimUtil.withAlpha(0xAAAAAA, Math.round(255 * alpha)), true);
            return;
        }

        int renderedLines = 0;
        List<ObjectiveEntry> objectives = phase.getObjectives();
        for (int objectiveIndex = 0;
             objectiveIndex < objectives.size() && renderedLines < MAX_OBJECTIVE_LINES;
             objectiveIndex++) {
            ObjectiveEntry objective = objectives.get(objectiveIndex);
            int progress = entry.runtime().getObjectiveProgress(entry.phaseId(), objectiveIndex);
            int required = objective.getRequiredCount();
            boolean complete = progress >= required;
            Component line = objective.isBooleanProgress()
                    ? Component.literal((complete ? "? " : "? ")).append(objective.getDisplayText())
                    : Component.literal((complete ? "? " : "? "))
                    .append(objective.getDisplayText())
                    .append(Component.literal("  " + progress + "/" + required));
            List<FormattedCharSequence> wrapped = font.split(line, textWidth);
            if (wrapped.isEmpty()) continue;
            int color = complete ? 0x88FF88 : 0xDDDDDD;
            graphics.drawString(font, wrapped.get(0), textX, textY,
                    HudAnimUtil.withAlpha(color, Math.round(255 * alpha)), true);
            textY += font.lineHeight + 3;
            renderedLines++;
            if (textY + font.lineHeight > y + height - CARD_PADDING) break;
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
