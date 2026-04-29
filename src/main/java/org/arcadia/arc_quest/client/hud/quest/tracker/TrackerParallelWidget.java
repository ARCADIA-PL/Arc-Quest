package org.arcadia.arc_quest.client.hud.quest.tracker;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

import java.util.List;
import java.util.Objects;

public class TrackerParallelWidget {

    public static int computeHeight(List<String> activePhaseOrder) {
        if (activePhaseOrder.size() <= 1) return 0;
        int rows = Math.min(TrackerConstants.LANE_SUMMARY_MAX_ROWS, activePhaseOrder.size());
        int h = 2 + TrackerConstants.LANE_HEADER_H + rows * (TrackerConstants.LANE_ROW_H + TrackerConstants.LANE_ROW_GAP) + 4;
        if (activePhaseOrder.size() > rows) h += TrackerConstants.LANE_ROW_H;
        return h;
    }

    public static int render(GuiGraphics g, Font font, QuestRuntimeData tracked, QuestDefinition def, List<String> activePhaseOrder, String displayedPhaseId, int themeColor, int textX, int textY, float alpha, float wipeAlpha) {
        if (activePhaseOrder.size() <= 1) return textY;

        int a = (int) (255 * alpha * wipeAlpha);
        if (a <= 8) return textY;

        textY += 2;
        g.pose().pushPose();
        g.pose().translate(textX, textY, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, Component.translatable("arc_quest.hud.parallel_lanes").getString(), 0, 0, HudAnimUtil.withAlpha(0x90A4AE, a), false);
        g.pose().popPose();
        textY += TrackerConstants.LANE_HEADER_H;

        int rows = Math.min(TrackerConstants.LANE_SUMMARY_MAX_ROWS, activePhaseOrder.size());

        int treeLineX = textX + 4;
        int treeLineY1 = textY;
        int treeLineY2 = textY + (rows - 1) * (TrackerConstants.LANE_ROW_H + TrackerConstants.LANE_ROW_GAP) + 4;

        g.fill(treeLineX, treeLineY1, treeLineX + 1, treeLineY2, HudAnimUtil.withAlpha(0xFFFFFF, (int)(0x22 * alpha * wipeAlpha)));

        for (int i = 0; i < rows; i++) {
            String pid = activePhaseOrder.get(i);
            PhaseDefinition p = def.getPhase(pid);
            if (p == null) continue;

            boolean isFocused = Objects.equals(pid, displayedPhaseId);

            int done = 0;
            int total = p.getObjectives().size();
            for (int j = 0; j < total; j++) {
                if (tracked.getObjectiveProgress(pid, j) >= Math.max(1, p.getObjectives().get(j).getRequiredCount())) done++;
            }
            boolean isComplete = total > 0 && done >= total;

            String laneName = ClientQuestCache.INSTANCE.getPhaseDisplayName(tracked.getQuestId(), pid);
            if (isComplete) laneName += " ✔";

            int nodeY = textY + 4;
            if (isFocused) {
                g.fill(treeLineX - 1, nodeY - 1, treeLineX + 2, nodeY + 2, HudAnimUtil.withAlpha(themeColor, a));
            } else if (isComplete) {
                g.fill(treeLineX - 1, nodeY - 1, treeLineX + 2, nodeY + 2, HudAnimUtil.withAlpha(0x66FF66, a));
            } else {
                g.fill(treeLineX, nodeY, treeLineX + 1, nodeY + 1, HudAnimUtil.withAlpha(0x90A4AE, a));
            }

            int textColor = isFocused ? 0xFFFFFF : (isComplete ? 0x99FF99 : 0xAAB4C0);
            String displayTxt = font.plainSubstrByWidth(laneName, TrackerConstants.PANEL_WIDTH - TrackerConstants.ACCENT_WIDTH - TrackerConstants.PADDING * 2 - 14);

            g.drawString(font, displayTxt, textX + 12, textY, HudAnimUtil.withAlpha(textColor, a), false);

            textY += TrackerConstants.LANE_ROW_H + TrackerConstants.LANE_ROW_GAP;
        }

        int more = activePhaseOrder.size() - rows;
        if (more > 0) {
            g.pose().pushPose();
            g.pose().translate(textX + 12, textY, 0);
            g.pose().scale(0.85f, 0.85f, 1f);
            g.drawString(font, Component.translatable("arc_quest.hud.parallel_more", more).getString(), 0, 0, HudAnimUtil.withAlpha(0x888888, a), false);
            g.pose().popPose();
            textY += TrackerConstants.LANE_ROW_H;
        }

        textY += 4;
        return textY;
    }
}