package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.quest.journal.JournalTypes;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

public class JournalDetailHistory {
    private final QuestJournalScreen screen;

    public JournalDetailHistory(QuestJournalScreen screen) {
        this.screen = screen;
    }

    public int render(GuiGraphics g, JournalTypes.QuestListEntry entry, QuestRuntimeData runtime, int scrollAreaW, int activeTheme, float dAlpha, int safeA, int localY) {
        if (runtime == null) return localY;

        Font font = screen.getFont();

        g.fill(0, localY, scrollAreaW - 24, localY + 1, HudAnimUtil.withAlpha(activeTheme, (int) (80 * dAlpha)));
        localY += 10;

        g.pose().pushPose(); g.pose().translate(0, localY, 0); g.pose().scale(0.8f, 0.8f, 1f);
        g.drawString(font, Component.translatable("arc_quest.gui.journal.section.completed_phases").getString(), 0, 0, HudAnimUtil.withAlpha(0xAAAAAA, safeA), true);
        g.pose().popPose();
        localY += 14;

        int completedCount = 0;
        for (String cPhaseId : runtime.getCompletedPhaseIds()) {
            String completedPhaseName = ClientQuestCache.INSTANCE.getPhaseDisplayName(entry.questId(), cPhaseId);
            g.pose().pushPose(); g.pose().translate(8, localY, 0); g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, "§a>" + completedPhaseName, 0, 0, HudAnimUtil.withAlpha(0x88FF88, (int) (200 * dAlpha)), false);
            g.pose().popPose();
            localY += 12;
            completedCount++;
        }
        if (completedCount == 0) {
            g.pose().pushPose(); g.pose().translate(8, localY, 0); g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, Component.translatable("arc_quest.gui.journal.label.no_phases_completed").getString(), 0, 0, HudAnimUtil.withAlpha(0x666666, safeA), false);
            g.pose().popPose();
            localY += 12;
        }

        localY += 6;
        return localY;
    }
}