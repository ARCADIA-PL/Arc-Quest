package org.com.arc_quest.client.gui.quest.journal.detail;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.client.gui.HudRenderUtil;
import org.com.arc_quest.client.gui.quest.journal.JournalTypes;
import org.com.arc_quest.client.gui.quest.journal.QuestJournalScreen;
import org.com.arc_quest.client.gui.render.QuestIconRenderer;
import org.com.arc_quest.quest.api.IconPosition;
import org.com.arc_quest.quest.api.PhaseDefinition;
import org.com.arc_quest.quest.api.QuestDefinition;
import org.com.arc_quest.quest.api.QuestState;
import org.com.arc_quest.quest.api.SplashType;
import org.com.arc_quest.quest.capability.QuestRuntimeData;
import org.com.arc_quest.quest.network.ClientQuestCache;

import java.util.ArrayList;
import java.util.List;

public class JournalDetailPanel {
    private final QuestJournalScreen screen;

    public final JournalDetailSinglePhase singlePhaseRenderer;
    public final JournalDetailParallelPhase parallelPhaseRenderer;
    public final JournalDetailRewards rewardsRenderer;
    public final JournalDetailControls controlsRenderer;

    private double detailScrollOffset = 0;
    private double detailTargetScroll = 0;
    private boolean isDraggingDetailScrollbar = false;
    private double dragDetailYOffset = 0;
    private int detailContentHeight = 0;

    private float detailReveal = 0f;

    public JournalDetailPanel(QuestJournalScreen screen) {
        this.screen = screen;
        this.singlePhaseRenderer = new JournalDetailSinglePhase(screen, this);
        this.parallelPhaseRenderer = new JournalDetailParallelPhase(screen, this);
        this.rewardsRenderer = new JournalDetailRewards(screen, this);
        this.controlsRenderer = new JournalDetailControls(screen, this);
    }

    public double getDetailScrollOffset() { return detailScrollOffset; }

    public void resetState() {
        detailReveal = 0f;
        detailTargetScroll = 0;
        detailScrollOffset = 0;
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
        g.enableScissor(x, scrollAreaY, x + w - 8, scrollAreaY + scrollAreaH);

        // Header Background Watermark
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

        g.pose().pushPose();
        g.pose().translate(titleIconOffset, localY, 0); g.pose().scale(1.2f, 1.2f, 1f);
        g.drawString(screen.getFont(), def.getDisplayName().getString(), 0, 0, HudAnimUtil.withAlpha(0xFFFFFF, safeA), true);
        g.pose().popPose();
        localY += 18;

        if (!def.getDescription().getString().isEmpty()) {
            g.pose().pushPose(); g.pose().translate(0, localY, 0); g.pose().scale(0.85f, 0.85f, 1f);
            List<String> descLines = HudRenderUtil.wrapText(def.getDescription().getString(), (int) ((scrollAreaW - 24) / 0.85f), screen.getFont());
            for (String line : descLines) {
                g.drawString(screen.getFont(), line, 0, 0, HudAnimUtil.withAlpha(0xAAAAAA, safeA), false);
                g.pose().translate(0, screen.getFont().lineHeight + 1, 0);
            }
            g.pose().popPose();
            localY += descLines.size() * (int) (screen.getFont().lineHeight * 0.85f + 1) + 8;
        }

        g.fill(0, localY, scrollAreaW - 24, localY + 1, HudAnimUtil.withAlpha(activeTheme, (int) (120 * dAlpha)));
        localY += 10;

        QuestRuntimeData runtime = ClientQuestCache.INSTANCE.getActiveQuest(entry.questId());

        if (entry.state() == QuestState.ACTIVE && runtime != null) {
            List<String> activePhaseIds = new ArrayList<>();
            for (String pid : def.getPhaseIds()) if (runtime.isPhaseActive(pid)) activePhaseIds.add(pid);
            if (activePhaseIds.isEmpty()) activePhaseIds.addAll(runtime.getActivePhaseIds());

            if (activePhaseIds.isEmpty()) {
                g.drawString(screen.getFont(), "No active phase.", 0, localY, HudAnimUtil.withAlpha(0x888888, safeA), false);
                localY += 16;
            } else if (activePhaseIds.size() == 1) {
                localY = singlePhaseRenderer.render(g, entry, def, runtime, activePhaseIds.get(0), x, scrollAreaY, scrollAreaW, scrollAreaH, mx, my, dt, activeTheme, dAlpha, safeA, localY);
            } else {
                localY = parallelPhaseRenderer.render(g, entry, def, runtime, activePhaseIds, x, scrollAreaY, scrollAreaW, scrollAreaH, mx, my, dt, activeTheme, dAlpha, safeA, localY);
            }
        } else if (entry.state() == QuestState.COMPLETED) {
            g.drawString(screen.getFont(), Component.translatable("arc_quest.gui.journal.label.quest_completed").getString(), 0, localY, HudAnimUtil.withAlpha(0x88FF88, safeA), true);
            localY += 16;
        } else if (entry.state() == QuestState.FAILED) {
            g.drawString(screen.getFont(), Component.translatable("arc_quest.gui.journal.label.quest_failed").getString(), 0, localY, HudAnimUtil.withAlpha(0xFF6666, safeA), true);
            localY += 16;
        }

        localY = rewardsRenderer.render(g, def, x, scrollAreaY, scrollAreaW, scrollAreaH, mx, my, activeTheme, dAlpha, safeA, localY);

        detailContentHeight = localY + 12;
        g.pose().popPose();
        g.disableScissor();

        renderScrollbar(g, x + w - 6, scrollAreaY + 2, scrollAreaH - 4, detailContentHeight, Math.max(0, detailContentHeight - scrollAreaH));

        controlsRenderer.render(g, entry, def, runtime, x, y, w, h, mx, my, dt, activeTheme);
    }

    private void renderEmptyDetail(GuiGraphics g, int x, int y, int w, int h) {
        if (screen.getEffectiveAlpha() > 0.05f) {
            g.drawCenteredString(screen.getFont(), Component.translatable("arc_quest.gui.journal.label.select_quest").getString(), x + w / 2, y + h / 2, HudAnimUtil.withAlpha(0x666666, (int) (120 * screen.getEffectiveAlpha())));
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

        if (maxDetailScroll > 0 && mx >= x + w - 6 && mx <= x + w && my >= y && my <= y + scrollAreaH) {
            isDraggingDetailScrollbar = true;
            int thumbH = Math.max(16, (int) (((float) scrollAreaH / detailContentHeight) * scrollAreaH));
            int thumbY = y + (int) ((detailScrollOffset / maxDetailScroll) * (scrollAreaH - thumbH));
            if (my >= thumbY && my <= thumbY + thumbH) dragDetailYOffset = my - thumbY;
            else { dragDetailYOffset = thumbH / 2.0; updateScrollFromMouse(my, y, scrollAreaH, maxDetailScroll); }
            return true;
        }

        if (controlsRenderer.mouseClicked(mx, my, x, y, w, h)) return true;

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
        if (isDraggingDetailScrollbar) {
            updateScrollFromMouse(my, y, h - 40, Math.max(0, detailContentHeight - (h - 40)));
            return true;
        }
        if (parallelPhaseRenderer.mouseDragged(mx)) return true;
        return false;
    }

    public boolean mouseReleased(int button) {
        if (button == 0) {
            isDraggingDetailScrollbar = false;
            parallelPhaseRenderer.onMouseReleased();
        }
        return isDraggingDetailScrollbar;
    }

    public boolean mouseScrolled(double mx, double my, double delta, int x, int y, int w, int h) {
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

    // Shared Helper Methods for Sub-Panels
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
            g.pose().pushPose(); g.pose().translate(x + w / 2f, y + h / 2f - (screen.getFont().lineHeight * baseScale) / 2f + 1, 0); g.pose().scale(baseScale, baseScale, 1f);
            g.drawCenteredString(screen.getFont(), text, 0, 0, HudAnimUtil.withAlpha(0xFFFFFF, (int) (255 * screen.getEffectiveAlpha())));
            g.pose().popPose();
        }
    }

    public static boolean shouldShowBranchChoices(QuestDefinition def, QuestRuntimeData runtime, String phaseId) {
        if (def == null || runtime == null || phaseId == null || phaseId.isEmpty()) return false;
        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null || !phase.hasChoices()) return false;
        int[] progress = runtime.getAllProgress(phaseId);
        for (int i = 0; i < phase.getObjectives().size(); i++) if (i >= progress.length || progress[i] < phase.getObjectives().get(i).getRequiredCount()) return false;
        return true;
    }

    public static boolean isPhaseObjectivesDone(QuestRuntimeData runtime, PhaseDefinition phase, String phaseId) {
        int[] progress = runtime.getAllProgress(phaseId);
        for (int i = 0; i < phase.getObjectives().size(); i++) if (i >= progress.length || progress[i] < phase.getObjectives().get(i).getRequiredCount()) return false;
        return true;
    }
}