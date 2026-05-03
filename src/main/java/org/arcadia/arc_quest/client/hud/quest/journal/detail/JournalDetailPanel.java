package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.quest.QuestIconRenderer;
import org.arcadia.arc_quest.client.hud.quest.history.QuestHistoryPanel;
import org.arcadia.arc_quest.client.hud.quest.journal.JournalTypes;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.offer.QuestOfferPanel;
import org.arcadia.arc_quest.client.hud.quest.ponder.QuestIntelPanel;
import org.arcadia.arc_quest.client.hud.quest.story.QuestStoryPanel;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

import java.util.ArrayList;
import java.util.List;

public class JournalDetailPanel {
    public final JournalDetailSinglePhase singlePhaseRenderer;
    public final JournalDetailParallelPhase parallelPhaseRenderer;
    public final JournalDetailRewards rewardsRenderer;
    public final JournalDetailControls controlsRenderer;
    private final QuestJournalScreen screen;
    private final int[] historyBtnRect = new int[]{0, 0, 0, 0};
    private double detailScrollOffset = 0;
    private double detailTargetScroll = 0;
    private boolean isDraggingDetailScrollbar = false;
    private double dragDetailYOffset = 0;
    private int detailContentHeight = 0;
    private float detailReveal = 0f;
    private float historyBtnHoverAnim = 0f;
    private final DetailHeaderCache headerCache = new DetailHeaderCache();
    private final String questCompletedText = Component.translatable("arc_quest.gui.journal.label.quest_completed").getString();
    private final String questFailedText = Component.translatable("arc_quest.gui.journal.label.quest_failed").getString();
    private final String selectQuestText = Component.translatable("arc_quest.gui.journal.label.select_quest").getString();

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

    public JournalDetailPanel(QuestJournalScreen screen) {
        this.screen = screen;
        this.singlePhaseRenderer = new JournalDetailSinglePhase(screen, this);
        this.parallelPhaseRenderer = new JournalDetailParallelPhase(screen, this);
        this.rewardsRenderer = new JournalDetailRewards(screen, this);
        this.controlsRenderer = new JournalDetailControls(screen, this);
    }

    public static void drawCyberButton(GuiGraphics g, QuestJournalScreen screen, int x, int y, int w, int h, String text, int themeColor, float hoverEase, boolean hovered) {
        int bgAlpha = (int) ((0x33 + 0x44 * hoverEase) * screen.getEffectiveAlpha());
        int borderAlpha = (int) ((0x66 + 0x99 * hoverEase) * screen.getEffectiveAlpha());
        int borderRgb = hovered ? (themeColor & 0xFFFFFF) : 0xCCCCCC;

        g.fill(x, y, x + w, y + h, HudAnimUtil.withAlpha(0x000000, bgAlpha));
        g.fill(x, y, x + w, y + 1, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        g.fill(x, y + h - 1, x + w, y + h, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        g.fill(x, y, x + 1, y + h, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        g.fill(x + w - 1, y, x + w, y + h, HudAnimUtil.withAlpha(borderRgb, borderAlpha));

        if (screen.getEffectiveAlpha() > 0.05f) {
            int textW = screen.getFont().width(text);
            float baseScale = 0.85f;
            if (textW * baseScale > w - 4) baseScale = Math.max(0.5f, (w - 6) / (float) textW);
            g.pose().pushPose();
            g.pose().translate(x + w / 2f, y + h / 2f - (screen.getFont().lineHeight * baseScale) / 2f + 1, 0);
            g.pose().scale(baseScale, baseScale, 1f);
            g.drawCenteredString(screen.getFont(), text, 0, 0, HudAnimUtil.withAlpha(0xFFFFFF, (int) (255 * screen.getEffectiveAlpha())));
            g.pose().popPose();
        }
    }

    public static boolean shouldShowBranchChoices(QuestDefinition def, QuestRuntimeData runtime, String phaseId) {
        if (def == null || runtime == null || phaseId == null || phaseId.isEmpty()) return false;
        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null || !phase.hasChoices()) return false;
        int[] progress = runtime.getAllProgress(phaseId);
        for (int i = 0; i < phase.getObjectives().size(); i++)
            if (i >= progress.length || progress[i] < phase.getObjectives().get(i).getRequiredCount()) return false;
        return true;
    }

    public static boolean isPhaseObjectivesDone(QuestRuntimeData runtime, PhaseDefinition phase, String phaseId) {
        int[] progress = runtime.getAllProgress(phaseId);
        for (int i = 0; i < phase.getObjectives().size(); i++)
            if (i >= progress.length || progress[i] < phase.getObjectives().get(i).getRequiredCount()) return false;
        return true;
    }

    public double getDetailScrollOffset() {
        return detailScrollOffset;
    }

    public void resetState() {
        detailReveal = 0f;
        detailTargetScroll = 0;
        detailScrollOffset = 0;
        headerCache.clear();
        singlePhaseRenderer.reset();
        parallelPhaseRenderer.reset();
    }

    public void render(GuiGraphics g, int x, int y, int w, int h, int mx, int my, int theme, float dt) {
        clampScroll(h - 40);
        detailScrollOffset += Math.abs(detailTargetScroll - detailScrollOffset) > 0.5 ? (detailTargetScroll - detailScrollOffset) * Math.min(1.0, dt * 14.0) : (detailTargetScroll - detailScrollOffset);

        int selectedIndex = screen.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= screen.getCurrentEntries().size()) {
            renderEmptyDetail(g, x, y, w, h);
            return;
        }

        JournalTypes.QuestListEntry entry = screen.getCurrentEntries().get(selectedIndex);
        QuestDefinition def = entry.def();
        if (def == null) return;

        int activeTheme = ClientQuestCache.INSTANCE.getQuestThemeColor(entry.questId(), theme);
        screen.setCurrentThemeColor(activeTheme);

        detailReveal = HudAnimUtil.lerp(detailReveal, 1f, 0.15f, dt);
        float dAlpha = screen.getEffectiveAlpha() * HudAnimUtil.easeOutCubic(Math.min(1f, detailReveal));
        int safeA = (int) (255 * dAlpha);
        if (safeA <= 8) return;

        int scrollAreaY = y, scrollAreaH = h - 40, scrollAreaW = w - 8;
        screen.enableScissor(g, x, scrollAreaY, x + w - 8, scrollAreaY + scrollAreaH);

        def.getSplashConfig(SplashType.QUEST_DETAIL).ifPresent(asset -> {
            RenderSystem.enableBlend();
            float watermarkAlpha = 0.15f * dAlpha;
            int rw = (int) (w * 0.7f), rh = rw;
            int rx = x + w / 2 - rw / 2 + (int) ((1f - detailReveal) * 50f);
            int ry = scrollAreaY + scrollAreaH / 2 - rh / 2;
            RenderSystem.setShaderColor(1f, 1f, 1f, watermarkAlpha);
            QuestIconRenderer.renderIcon(g, asset, rx, ry, rw, rh);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        });

        g.pose().pushPose();
        g.pose().translate(x + 12, scrollAreaY + 12 - detailScrollOffset, 0);

        int localY = 0, titleIconOffset = 0;
        if (def.getVisualConfig().getIcon(IconPosition.QUEST_TITLE).isPresent()) {
            int finalLocalY = localY;
            def.getVisualConfig().getIcon(IconPosition.QUEST_TITLE).ifPresent(icon -> {
                RenderSystem.setShaderColor(1f, 1f, 1f, dAlpha);
                QuestIconRenderer.renderIcon(g, icon, 0, finalLocalY - 2, 16, 16);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            });
            titleIconOffset = 22;
        }

        DetailHeaderCache header = getHeaderCache(entry, def, scrollAreaW);
        String titleText = header.titleText;

        // 渲染主标题
        g.pose().pushPose();
        g.pose().translate(titleIconOffset, localY, 0);
        g.pose().scale(1.2f, 1.2f, 1f);
        g.drawString(screen.getFont(), titleText, 0, 0, HudAnimUtil.withAlpha(0xFFFFFF, safeA), true);
        g.pose().popPose();

        // 计算标题和历史按钮的位置 (先排版历史按钮)
        int titleW = (int) (header.titleWidth * 1.2f);
        int hBtnX = titleIconOffset + titleW + 10;
        int hBtnY = localY + 5;
        int hBtnR = 3;

        int absBtnX = x + 12 + hBtnX;
        int absBtnY = (int) (scrollAreaY + 12 - detailScrollOffset + hBtnY);
        boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestHistoryPanel.isActive() || QuestStoryPanel.isActive();
        boolean hHover = !panelsActive && mx >= absBtnX - hBtnR - 4 && mx <= absBtnX + hBtnR + 4 && my >= absBtnY - hBtnR - 4 && my <= absBtnY + hBtnR + 4;

        historyBtnHoverAnim = HudAnimUtil.step(historyBtnHoverAnim, hHover ? 1f : 0f, 15f, dt);

        g.pose().pushPose();
        g.pose().translate(hBtnX, hBtnY, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(45));

        int idleGlow = 35 + (int) (25 * Math.sin(Util.getMillis() / 300.0));
        int glowA = (int) ((hHover ? 180 : idleGlow) * dAlpha);

        if (glowA > 0) g.fill(-hBtnR - 2, -hBtnR - 2, hBtnR + 2, hBtnR + 2, HudAnimUtil.withAlpha(activeTheme, glowA));
        g.fill(-hBtnR, -hBtnR, hBtnR, hBtnR, HudAnimUtil.withAlpha(0x222222, safeA));
        g.fill(-hBtnR + 1, -hBtnR + 1, hBtnR - 1, hBtnR - 1, HudAnimUtil.withAlpha(activeTheme, (int) ((150 + 105 * historyBtnHoverAnim) * dAlpha)));
        g.pose().popPose();

        historyBtnRect[0] = absBtnX - hBtnR - 4;
        historyBtnRect[1] = absBtnY - hBtnR - 4;
        historyBtnRect[2] = hBtnR * 2 + 8;
        historyBtnRect[3] = hBtnR * 2 + 8;

        if (hHover && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH && !panelsActive) {
            screen.setHoveredCustomTooltip(List.of(
                    Component.literal("Topology MAP").withStyle(Style.EMPTY.withColor(activeTheme).withBold(true)),
                    Component.literal("View node graph & history").withStyle(Style.EMPTY.withColor(0xAAAAAA))
            ));
        }

        QuestRuntimeData runtime = ClientQuestCache.INSTANCE.getActiveQuest(entry.questId());

        // ==========================================================
        // 极简机能风排版：绝对靠右倒计时，丢弃多余前缀
        // ==========================================================
        long remainSec = getQuestRemainSeconds(def, runtime);
        if (remainSec >= 0) {
            String timeStr = formatAsClock(remainSec);
            float timeScale = 1.0f; // 大方干脆的等比例字体
            int timerRenderW = (int) (screen.getFont().width(timeStr) * timeScale);

            // 危机感红脉冲
            float pulse = 1.0f;
            int activeTimerColor = activeTheme;
            if (remainSec <= 60) {
                pulse = 0.6f + 0.4f * (float) Math.sin(Util.getMillis() / (remainSec <= 10 ? 80.0 : 200.0));
                if (remainSec <= 10) {
                    activeTimerColor = 0xFF4444;
                }
            }

            int timeColor = HudAnimUtil.withAlpha(activeTimerColor, (int)(safeA * pulse));

            // 精准靠右锚定：刚好对齐下方的分割线右边缘 (scrollAreaW - 24)
            int timeX = (scrollAreaW - 24) - timerRenderW;

            // 保护机制：如果标题过长，不要让倒计时和按钮重叠，最少距离按钮 16 像素
            int minTimerX = hBtnX + hBtnR + 16;
            if (timeX < minTimerX) {
                timeX = minTimerX;
            }

            // Y轴基线对齐，因为标题是 1.2 比例，这里是 1.0 比例，略微下沉 2px 对齐底部
            int timeY = localY + 2;

            g.pose().pushPose();
            g.pose().translate(timeX, timeY, 0);
            g.pose().scale(timeScale, timeScale, 1f);
            g.drawString(screen.getFont(), timeStr, 0, 0, timeColor, true);
            g.pose().popPose();
        }
        // ==========================================================

        localY += 18;

        if (!header.descriptionText.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(0, localY, 0);
            g.pose().scale(0.85f, 0.85f, 1f);
            for (String line : header.descriptionLines) {
                g.drawString(screen.getFont(), line, 0, 0, HudAnimUtil.withAlpha(0xAAAAAA, safeA), false);
                g.pose().translate(0, screen.getFont().lineHeight + 1, 0);
            }
            g.pose().popPose();
            localY += header.descriptionLines.size() * (int) (screen.getFont().lineHeight * 0.85f + 1) + 8;
        }

        g.fill(0, localY, scrollAreaW - 24, localY + 1, HudAnimUtil.withAlpha(activeTheme, (int) (120 * dAlpha)));
        localY += 10;

        String selectedPhaseIdForRewards = null;

        if (entry.state() == QuestState.ACTIVE && runtime != null) {
            List<String> activePhaseIds = new ArrayList<>();
            for (String pid : def.getPhaseIds()) if (runtime.isPhaseActive(pid)) activePhaseIds.add(pid);
            if (activePhaseIds.isEmpty()) activePhaseIds.addAll(runtime.getActivePhaseIds());

            if (activePhaseIds.isEmpty()) {
                g.drawString(screen.getFont(), "No active phase.", 0, localY, HudAnimUtil.withAlpha(0x888888, safeA), false);
                localY += 16;
            } else if (activePhaseIds.size() == 1) {
                selectedPhaseIdForRewards = activePhaseIds.get(0);
                localY = singlePhaseRenderer.render(g, entry, def, runtime, activePhaseIds.get(0), x, scrollAreaY, scrollAreaW, scrollAreaH, mx, my, dt, activeTheme, dAlpha, safeA, localY);
            } else {
                localY = parallelPhaseRenderer.render(g, entry, def, runtime, activePhaseIds, x, scrollAreaY, scrollAreaW, scrollAreaH, mx, my, dt, activeTheme, dAlpha, safeA, localY);
                selectedPhaseIdForRewards = parallelPhaseRenderer.getSelectedPhaseId();
            }
        } else if (entry.state() == QuestState.COMPLETED) {
            g.drawString(screen.getFont(), questCompletedText, 0, localY, HudAnimUtil.withAlpha(0x88FF88, safeA), false);
            localY += 16;
        } else if (entry.state() == QuestState.FAILED) {
            g.drawString(screen.getFont(), questFailedText, 0, localY, HudAnimUtil.withAlpha(0xFF6666, safeA), false);
            localY += 16;
        }

        localY = rewardsRenderer.render(g, def, selectedPhaseIdForRewards, x, scrollAreaY, scrollAreaW, scrollAreaH, mx, my, activeTheme, dAlpha, safeA, localY, dt);

        detailContentHeight = localY + 12;
        g.pose().popPose();
        g.disableScissor();

        renderScrollbar(g, x + w - 6, scrollAreaY + 2, scrollAreaH - 4, detailContentHeight, Math.max(0, detailContentHeight - scrollAreaH));

        controlsRenderer.render(g, entry, def, runtime, x, y, w, h, mx, my, dt, activeTheme);
    }

    private DetailHeaderCache getHeaderCache(JournalTypes.QuestListEntry entry, QuestDefinition def, int scrollAreaW) {
        int descWidth = (int) ((scrollAreaW - 24) / 0.85f);
        String description = def.getDescription().getString();
        if (!entry.questId().equals(headerCache.questId)
                || headerCache.descWidth != descWidth
                || !description.equals(headerCache.descriptionText)) {
            headerCache.questId = entry.questId();
            headerCache.descWidth = descWidth;
            headerCache.titleText = def.getDisplayName().getString();
            headerCache.titleWidth = screen.getFont().width(headerCache.titleText);
            headerCache.descriptionText = description;
            headerCache.descriptionLines = description.isEmpty() ? List.of() : HudRenderUtil.wrapText(description, descWidth, screen.getFont());
        }
        return headerCache;
    }

    private void renderEmptyDetail(GuiGraphics g, int x, int y, int w, int h) {
        if (screen.getEffectiveAlpha() > 0.05f) {
            g.drawCenteredString(screen.getFont(), selectQuestText, x + w / 2, y + h / 2, HudAnimUtil.withAlpha(0x666666, (int) (120 * screen.getEffectiveAlpha())));
        }
    }

    private void renderScrollbar(GuiGraphics g, int x, int y, int viewH, int contentH, int maxScroll) {
        if (maxScroll <= 0) return;
        int thumbH = Math.max(16, (int) (((float) viewH / contentH) * viewH));
        int thumbY = y + (int) ((detailScrollOffset / maxScroll) * (viewH - thumbH));
        g.fill(x, y, x + 4, y + viewH, HudAnimUtil.withAlpha(0x000000, (int) (40 * screen.getEffectiveAlpha())));
        g.fill(x, thumbY, x + 4, thumbY + thumbH, HudAnimUtil.withAlpha(0xFFFFFF, (int) ((isDraggingDetailScrollbar ? 180 : 120) * screen.getEffectiveAlpha())));
    }

    public boolean mouseClicked(double mx, double my, int x, int y, int w, int h) {
        int scrollAreaH = h - 40;
        int maxDetailScroll = Math.max(0, detailContentHeight - scrollAreaH);
        boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestHistoryPanel.isActive() || QuestStoryPanel.isActive();

        if (!panelsActive && rewardsRenderer.mouseClicked(mx, my)) return true;

        if (!panelsActive && maxDetailScroll > 0 && mx >= x + w - 6 && mx <= x + w && my >= y && my <= y + scrollAreaH) {
            isDraggingDetailScrollbar = true;
            int thumbH = Math.max(16, (int) (((float) scrollAreaH / detailContentHeight) * scrollAreaH));
            int thumbY = y + (int) ((detailScrollOffset / maxDetailScroll) * (scrollAreaH - thumbH));
            if (my >= thumbY && my <= thumbY + thumbH) dragDetailYOffset = my - thumbY;
            else {
                dragDetailYOffset = thumbH / 2.0;
                updateScrollFromMouse(my, y, scrollAreaH, maxDetailScroll);
            }
            return true;
        }

        if (!panelsActive && controlsRenderer.mouseClicked(mx, my, x, y, w, h)) return true;

        if (!panelsActive && mx >= historyBtnRect[0] && mx <= historyBtnRect[0] + historyBtnRect[2] &&
                my >= historyBtnRect[1] && my <= historyBtnRect[1] + historyBtnRect[3] && my >= y && my <= y + scrollAreaH) {
            if (screen.getSelectedIndex() >= 0 && screen.getSelectedIndex() < screen.getCurrentEntries().size()) {
                QuestHistoryPanel.trigger(screen.getCurrentEntries().get(screen.getSelectedIndex()).questId());
                screen.playClick();
                return true;
            }
        }

        if (screen.getSelectedIndex() >= 0 && screen.getSelectedIndex() < screen.getCurrentEntries().size()) {
            JournalTypes.QuestListEntry entry = screen.getCurrentEntries().get(screen.getSelectedIndex());
            QuestRuntimeData runtime = ClientQuestCache.INSTANCE.getActiveQuest(entry.questId());
            if (entry.state() == QuestState.ACTIVE && runtime != null) {
                List<String> activePhaseIds = new ArrayList<>();
                for (String pid : entry.def().getPhaseIds()) if (runtime.isPhaseActive(pid)) activePhaseIds.add(pid);
                if (activePhaseIds.isEmpty()) activePhaseIds.addAll(runtime.getActivePhaseIds());

                if (activePhaseIds.size() == 1) {
                    if (singlePhaseRenderer.mouseClicked(mx, my, x, y, w, h)) return true;
                } else if (activePhaseIds.size() > 1) {
                    if (parallelPhaseRenderer.mouseClicked(mx, my, x, y, w, h)) return true;
                }
            }
        }
        return false;
    }

    public boolean mouseDragged(double mx, double my, int y, int h) {
        if (rewardsRenderer.mouseDragged(mx, my)) return true;

        if (isDraggingDetailScrollbar) {
            updateScrollFromMouse(my, y, h - 40, Math.max(0, detailContentHeight - (h - 40)));
            return true;
        }
        if (parallelPhaseRenderer.mouseDragged(mx, my)) return true;
        return false;
    }

    public boolean mouseReleased(int button) {
        if (rewardsRenderer.mouseReleased(button)) return true;

        if (button == 0) {
            isDraggingDetailScrollbar = false;
            parallelPhaseRenderer.onMouseReleased();
        }
        return isDraggingDetailScrollbar;
    }

    public boolean mouseScrolled(double mx, double my, double delta, int x, int y, int w, int h) {
        if (rewardsRenderer.mouseScrolled(mx, my, delta)) return true;

        if (parallelPhaseRenderer.mouseScrolled(mx, my, delta, x, y, h - 40)) return true;
        if (mx >= x && mx <= x + w && my >= y && my <= y + h) {
            detailTargetScroll -= delta * 25.0;
            clampScroll(h - 40);
            return true;
        }
        return false;
    }

    public void clampScroll(int scrollAreaH) {
        detailTargetScroll = Math.max(0, Math.min(detailTargetScroll, Math.max(0, detailContentHeight - scrollAreaH)));
    }

    private void updateScrollFromMouse(double my, int y0, int viewH, int maxScroll) {
        if (maxScroll <= 0) return;
        int thumbH = Math.max(16, (int) (((float) viewH / detailContentHeight) * viewH));
        detailTargetScroll = Math.max(0.0, Math.min(1.0, (my - y0 - dragDetailYOffset) / (viewH - thumbH))) * maxScroll;
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
            long nowDay = (screen.getMinecraft().level != null)
                    ? (screen.getMinecraft().level.getDayTime() % 24000L)
                    : acceptedDay;
            long elapsedTicks = (nowDay - acceptedDay + 24000L) % 24000L;
            long remainTicks = Math.max(0L, limit - elapsedTicks);
            return remainTicks / 20L;
        }
        return -1L;
    }

    private String formatAsClock(long totalSeconds) {
        long s = Math.max(0L, totalSeconds);
        long h = s / 3600L;
        long m = (s % 3600L) / 60L;
        long sec = s % 60L;

        if (h > 0L) {
            return String.format("%02d:%02d:%02d", h, m, sec);
        }
        return String.format("%02d:%02d", m, sec);
    }
}