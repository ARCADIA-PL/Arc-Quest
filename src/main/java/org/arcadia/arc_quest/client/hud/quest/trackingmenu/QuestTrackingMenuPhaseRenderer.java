package org.arcadia.arc_quest.client.hud.quest.trackingmenu;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class QuestTrackingMenuPhaseRenderer {
    private static final int ROW_GAP = 4;
    private static final int CARD_GAP = 9;

    private QuestTrackingMenuPhaseRenderer() {
    }

    static Layout layout(QuestTrackingMenuEntry entry,
                         QuestTrackingMenuPhaseEntry selectedPhase,
                         int cardX, int cardY, int cardWidth, int cardHeight,
                         float alpha) {
        List<QuestTrackingMenuPhaseEntry> phases = entry.activePhases();
        if (phases.size() <= 1 || selectedPhase == null || alpha <= 0.02f) return Layout.EMPTY;

        int selectedIndex = phases.indexOf(selectedPhase);
        if (selectedIndex < 0) selectedIndex = 0;
        int rowHeight = Math.max(14, Math.min(26, cardHeight / 5));
        int desiredRowWidth = Math.max(56, Math.min(168, Math.round(cardWidth * 0.66f)));
        int rowWidth = Math.min(desiredRowWidth, Math.max(48, cardX - CARD_GAP - 4));
        int centerY = cardY + cardHeight / 2;
        int slideOffset = Math.round((1f - alpha) * 18f);
        int baseRight = cardX - CARD_GAP + slideOffset;

        Set<Integer> visibleIndices = new LinkedHashSet<>();
        visibleIndices.add(Math.floorMod(selectedIndex - 1, phases.size()));
        visibleIndices.add(selectedIndex);
        if (phases.size() > 2) visibleIndices.add(Math.floorMod(selectedIndex + 1, phases.size()));

        List<Row> rows = new ArrayList<>();
        for (int phaseIndex : visibleIndices) {
            int relative = circularDistance(phaseIndex, selectedIndex, phases.size());
            boolean selected = phaseIndex == selectedIndex;
            int width = selected ? rowWidth : Math.round(rowWidth * 0.92f);
            int x = Math.max(4, baseRight - width);
            int y = centerY + relative * (rowHeight + ROW_GAP) - rowHeight / 2;
            float rowAlpha = alpha * (selected ? 1f : 0.68f);
            rows.add(new Row(entry.questId(), phases.get(phaseIndex),
                    x, y, width, rowHeight, rowAlpha, selected));
        }
        rows.sort(java.util.Comparator.comparingInt(Row::y));

        int left = rows.stream().mapToInt(Row::x).min().orElse(cardX);
        int top = rows.stream().mapToInt(Row::y).min().orElse(cardY);
        int right = rows.stream().mapToInt(row -> row.x() + row.width()).max().orElse(cardX);
        int bottom = rows.stream().mapToInt(row -> row.y() + row.height()).max().orElse(cardY);
        return new Layout(List.copyOf(rows), left, top, right, bottom, alpha);
    }

    static void render(GuiGraphics graphics, Font font, QuestTrackingMenuEntry entry,
                       Layout layout, String trackedPhaseId, PhaseHit hovered) {
        if (layout.rows().isEmpty()) return;
        int themeColor = entry.definition().getThemeColor();
        Component heading = Component.translatable("arc_quest.gui.tracking_menu.parallel_phases");
        float headingScale = Math.max(0.65f, Math.min(1f, layout.rows().getFirst().width() / 168f));
        drawScaled(graphics, font, heading, layout.left(),
                layout.top() - Math.round(font.lineHeight * headingScale) - 3,
                HudAnimUtil.withAlpha(0xB9C0CC, Math.round(220 * layout.alpha())), headingScale, true);

        for (Row row : layout.rows()) {
            boolean isHovered = hovered != null && hovered.phaseId().equals(row.phase().phaseId());
            boolean isTracked = row.phase().phaseId().equals(trackedPhaseId);
            int backgroundAlpha = Math.round((row.selected() ? 185 : 115) * row.alpha());
            if (isHovered) backgroundAlpha = Math.min(225, backgroundAlpha + 38);
            graphics.fill(row.x() + 2, row.y() + 2,
                    row.x() + row.width() + 2, row.y() + row.height() + 2,
                    HudAnimUtil.withAlpha(0x000000, Math.round(95 * row.alpha())));
            graphics.fill(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(),
                    HudAnimUtil.withAlpha(0x080C13, backgroundAlpha));
            graphics.fill(row.x(), row.y(), row.x() + (row.selected() ? 3 : 2), row.y() + row.height(),
                    HudAnimUtil.withAlpha(themeColor, Math.round((row.selected() ? 255 : 150) * row.alpha())));

            float textScale = Math.max(0.65f, Math.min(1f, row.width() / 168f));
            int textX = row.x() + 7;
            int textY = row.y() + Math.max(1, (row.height() - Math.round(font.lineHeight * textScale)) / 2);
            drawEllipsizedScaled(graphics, font, row.phase().definition().getDisplayName(),
                    textX, textY, row.width() - 13, textScale,
                    HudAnimUtil.withAlpha(row.selected() ? 0xFFFFFF : 0xC3C8D0,
                            Math.round(255 * row.alpha())));

            if (isTracked) {
                int markerX = row.x() - 6;
                int markerY = row.y() + row.height() / 2;
                graphics.fill(markerX, markerY - 2, markerX + 3, markerY + 3,
                        HudAnimUtil.withAlpha(themeColor, Math.round(255 * row.alpha())));
                graphics.fill(markerX + 3, markerY - 1, markerX + 5, markerY + 2,
                        HudAnimUtil.withAlpha(themeColor, Math.round(255 * row.alpha())));
            }
        }
    }

    private static int circularDistance(int index, int selectedIndex, int size) {
        int distance = index - selectedIndex;
        if (distance > size / 2) distance -= size;
        if (distance < -size / 2) distance += size;
        return Integer.compare(distance, 0);
    }

    private static void drawEllipsized(GuiGraphics graphics, Font font, Component text,
                                       int x, int y, int maxWidth, int color) {
        if (font.width(text) <= maxWidth) {
            graphics.drawString(font, text, x, y, color, false);
            return;
        }
        String ellipsis = "...";
        int allowed = Math.max(0, maxWidth - font.width(ellipsis));
        graphics.drawString(font, font.plainSubstrByWidth(text.getString(), allowed) + ellipsis,
                x, y, color, false);
    }

    private static void drawEllipsizedScaled(GuiGraphics graphics, Font font, Component text,
                                             int x, int y, int maxWidth, float scale, int color) {
        int logicalMaxWidth = Math.max(1, Math.round(maxWidth / scale));
        Component rendered = text;
        if (font.width(text) > logicalMaxWidth) {
            String ellipsis = "...";
            int allowed = Math.max(0, logicalMaxWidth - font.width(ellipsis));
            rendered = Component.literal(font.plainSubstrByWidth(text.getString(), allowed) + ellipsis);
        }
        drawScaled(graphics, font, rendered, x, y, color, scale, false);
    }

    private static void drawScaled(GuiGraphics graphics, Font font, Component text,
                                   int x, int y, int color, float scale, boolean shadow) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0f);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, text, 0, 0, color, shadow);
        graphics.pose().popPose();
    }

    record Layout(List<Row> rows, int left, int top, int right, int bottom, float alpha) {
        static final Layout EMPTY = new Layout(List.of(), 0, 0, 0, 0, 0f);

        PhaseHit find(double mouseX, double mouseY) {
            for (Row row : rows) {
                if (mouseX >= row.x() && mouseX < row.x() + row.width()
                        && mouseY >= row.y() && mouseY < row.y() + row.height()) {
                    return new PhaseHit(row.questId(), row.phase().phaseId(),
                            row.x(), row.y(), row.width(), row.height());
                }
            }
            return null;
        }

        boolean contains(double mouseX, double mouseY) {
            return !rows.isEmpty() && mouseX >= left && mouseX < right
                    && mouseY >= top && mouseY < bottom;
        }

        String questId() {
            return rows.isEmpty() ? null : rows.getFirst().questId();
        }
    }

    record PhaseHit(String questId, String phaseId, int x, int y, int width, int height) {
    }

    private record Row(String questId, QuestTrackingMenuPhaseEntry phase,
                       int x, int y, int width, int height, float alpha, boolean selected) {
    }
}
