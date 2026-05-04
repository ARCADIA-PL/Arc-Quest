package org.arcadia.arc_quest.client.hud.quest.journal;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.quest.QuestIconRenderer;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.text.ArcTextLayoutUtil;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;
import org.arcadia.arc_quest.quest.api.IconPosition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.api.QuestTimeLimitType;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;

import java.util.List;

public class ArcQuestJournalHeaderElement extends ArcGuiElement {
    private final QuestJournalScreen screen;
    private final ArcQuestJournalHistoryButtonElement historyButton;
    private final DetailHeaderCache headerCache = new DetailHeaderCache();

    public ArcQuestJournalHeaderElement(QuestJournalScreen screen) {
        super(0, 0, 0, 0);
        this.screen = screen;
        this.historyButton = new ArcQuestJournalHistoryButtonElement(screen);
    }

    public void resetState() {
        headerCache.clear();
    }

    public int render(GuiGraphics graphics, JournalTypes.QuestListEntry entry, QuestDefinition def, QuestRuntimeData runtime, int detailX, int scrollAreaY, int scrollAreaW, int scrollAreaH, int mouseX, int mouseY, int activeTheme, float detailAlpha, int safeAlpha, int localY, double detailScrollOffset, float dt) {
        int titleIconOffset = 0;
        if (def.getVisualConfig().getIcon(IconPosition.QUEST_TITLE).isPresent()) {
            int finalLocalY = localY;
            def.getVisualConfig().getIcon(IconPosition.QUEST_TITLE).ifPresent(icon -> {
                RenderSystem.setShaderColor(1f, 1f, 1f, detailAlpha);
                QuestIconRenderer.renderIcon(graphics, icon, 0, finalLocalY - 2, 16, 16);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            });
            titleIconOffset = 22;
        }

        DetailHeaderCache header = getHeaderCache(entry, def, scrollAreaW);
        graphics.pose().pushPose();
        graphics.pose().translate(titleIconOffset, localY, 0);
        graphics.pose().scale(1.2f, 1.2f, 1f);
        graphics.drawString(screen.getFont(), header.titleText, 0, 0, ArcDrawUtil.withAlpha(0xFFFFFF, safeAlpha), true);
        graphics.pose().popPose();

        int titleW = (int) (header.titleWidth * 1.2f);
        int historyX = titleIconOffset + titleW + 10;
        int historyY = localY + 5;
        int historyRadius = 3;
        historyButton.render(graphics, historyX, historyY, detailX, scrollAreaY, scrollAreaH, detailScrollOffset, mouseX, mouseY, activeTheme, detailAlpha, safeAlpha, dt);

        long remainSec = getQuestRemainSeconds(def, runtime);
        if (remainSec >= 0) {
            renderTimer(graphics, remainSec, scrollAreaW, localY, historyX, historyRadius, activeTheme, safeAlpha);
        }

        localY += 18;
        if (!header.descriptionText.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, localY, 0);
            graphics.pose().scale(0.85f, 0.85f, 1f);
            for (String line : header.descriptionLines) {
                graphics.drawString(screen.getFont(), line, 0, 0, ArcDrawUtil.withAlpha(0xAAAAAA, safeAlpha), false);
                graphics.pose().translate(0, screen.getFont().lineHeight + 1, 0);
            }
            graphics.pose().popPose();
            localY += header.descriptionLines.size() * (int) (screen.getFont().lineHeight * 0.85f + 1) + 8;
        }
        return localY;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int scrollAreaY, int scrollAreaH) {
        return historyButton.mouseClicked(mouseX, mouseY, scrollAreaY, scrollAreaH);
    }

    private DetailHeaderCache getHeaderCache(JournalTypes.QuestListEntry entry, QuestDefinition def, int scrollAreaW) {
        int descWidth = (int) ((scrollAreaW - 24) / 0.85f);
        String description = def.getDescription().getString();
        if (!entry.questId().equals(headerCache.questId) || headerCache.descWidth != descWidth || !description.equals(headerCache.descriptionText)) {
            headerCache.questId = entry.questId();
            headerCache.descWidth = descWidth;
            headerCache.titleText = def.getDisplayName().getString();
            headerCache.titleWidth = screen.getFont().width(headerCache.titleText);
            headerCache.descriptionText = description;
            headerCache.descriptionLines = description.isEmpty() ? List.of() : ArcTextLayoutUtil.wrapPlain(screen.getFont(), description, descWidth);
        }
        return headerCache;
    }

    private void renderTimer(GuiGraphics graphics, long remainSec, int scrollAreaW, int localY, int historyX, int historyRadius, int activeTheme, int safeAlpha) {
        String timeStr = formatAsClock(remainSec);
        float timeScale = 1.0f;
        int timerRenderW = (int) (screen.getFont().width(timeStr) * timeScale);
        float pulse = 1.0f;
        int activeTimerColor = activeTheme;
        if (remainSec <= 60) {
            pulse = 0.6f + 0.4f * (float) Math.sin(Util.getMillis() / (remainSec <= 10 ? 80.0 : 200.0));
            if (remainSec <= 10) activeTimerColor = 0xFF4444;
        }
        int timeColor = ArcDrawUtil.withAlpha(activeTimerColor, (int) (safeAlpha * pulse));
        int timeX = (scrollAreaW - 24) - timerRenderW;
        int minTimerX = historyX + historyRadius + 16;
        if (timeX < minTimerX) timeX = minTimerX;
        int timeY = localY + 2;
        graphics.pose().pushPose();
        graphics.pose().translate(timeX, timeY, 0);
        graphics.pose().scale(timeScale, timeScale, 1f);
        graphics.drawString(screen.getFont(), timeStr, 0, 0, timeColor, true);
        graphics.pose().popPose();
    }

    private long getQuestRemainSeconds(QuestDefinition def, QuestRuntimeData runtime) {
        if (def == null || runtime == null || runtime.getState() != QuestState.ACTIVE) return -1L;
        if (!def.hasTimeLimit()) return -1L;
        QuestTimeLimitType type = def.getTimeLimitType();
        long limit = def.getTimeLimitValue();
        if (type == null || limit <= 0L) return -1L;
        if (type == QuestTimeLimitType.REAL_SECONDS) {
            long accepted = runtime.getAcceptedAtRealMs();
            if (accepted <= 0L) return -1L;
            long elapsedSec = Math.max(0L, (System.currentTimeMillis() - accepted) / 1000L);
            return Math.max(0L, limit - elapsedSec);
        } else if (type == QuestTimeLimitType.GAME_DAY_TIME) {
            long acceptedDay = runtime.getAcceptedAtDayTime() % 24000L;
            long nowDay = (screen.getMinecraft().level != null) ? (screen.getMinecraft().level.getDayTime() % 24000L) : acceptedDay;
            long elapsedTicks = (nowDay - acceptedDay + 24000L) % 24000L;
            long remainTicks = Math.max(0L, limit - elapsedTicks);
            return remainTicks / 20L;
        }
        return -1L;
    }

    private String formatAsClock(long totalSeconds) {
        long s = Math.max(0L, totalSeconds), h = s / 3600L, m = (s % 3600L) / 60L, sec = s % 60L;
        if (h > 0L) return String.format("%02d:%02d:%02d", h, m, sec);
        return String.format("%02d:%02d", m, sec);
    }

    private static class DetailHeaderCache {
        String questId = "";
        int descWidth = -1;
        String titleText = "";
        int titleWidth = 0;
        String descriptionText = "";
        List<String> descriptionLines = List.of();

        void clear() {
            questId = "";
            descWidth = -1;
            titleText = "";
            titleWidth = 0;
            descriptionText = "";
            descriptionLines = List.of();
        }
    }
}
