package org.com.arc_quest.client.gui.quest;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.client.gui.dialogue.DialogueScreen;
import org.com.arc_quest.client.gui.quest.journal.QuestJournalScreen;
import org.com.arc_quest.client.gui.quest.tracker.TrackerConstants;
import org.com.arc_quest.client.gui.quest.tracker.TrackerObjectiveWidget;
import org.com.arc_quest.client.gui.quest.tracker.TrackerParallelWidget;
import org.com.arc_quest.client.gui.quest.tracker.TrackerTitleWidget;
import org.com.arc_quest.quest.api.ObjectiveEntry;
import org.com.arc_quest.quest.api.PhaseDefinition;
import org.com.arc_quest.quest.api.QuestDefinition;
import org.com.arc_quest.quest.api.QuestState;
import org.com.arc_quest.quest.capability.QuestRuntimeData;
import org.com.arc_quest.quest.network.ClientQuestCache;
import org.com.arc_quest.quest.registry.QuestRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class QuestTrackerPanel {

    // 状态机与动画数据
    private float panelReveal = 0f;
    private float panelSlide = 1f;
    private float currentPanelH = -1f;
    private float currentPanelY = TrackerConstants.MARGIN_TOP;
    private long lastRenderTime = 0;
    private float dt = 0f;

    private String trackedQuestId = null;
    private String trackedPhaseId = null;
    private String targetPhaseId = null;
    private String displayedPhaseId = null;

    private boolean isPhaseTransitioning = false;
    private boolean phaseWipingOut = false;
    private long phaseTransitionStart = 0;
    private long completionDismissStart = 0;

    private List<String> activePhaseOrder = new ArrayList<>();
    private int currentThemeColor = TrackerConstants.COLOR_ACCENT_DEFAULT;

    // 分离出去的组件管理器
    private final TrackerObjectiveWidget objectiveWidget = new TrackerObjectiveWidget();

    public void setTrackedQuest(String questId) {
        if (!Objects.equals(this.trackedQuestId, questId)) {
            this.trackedQuestId = questId;
            this.trackedPhaseId = null;
            this.displayedPhaseId = null;
            this.targetPhaseId = null;
            this.currentPanelH = -1f;
            this.activePhaseOrder.clear();
            resetObjectiveAnimations();
        }
    }

    public void setTrackedFocus(String questId, String phaseId) {
        if (Objects.equals(this.trackedQuestId, questId) && Objects.equals(this.trackedPhaseId, phaseId)) return;
        this.trackedQuestId = questId;
        this.trackedPhaseId = phaseId;
        this.displayedPhaseId = null;
        this.targetPhaseId = null;
        this.currentPanelH = -1f;
        this.activePhaseOrder.clear();
        resetObjectiveAnimations();
    }

    public String getTrackedPhaseId() { return trackedPhaseId; }
    public String getTrackedQuestId() { return trackedQuestId; }

    public void resetPanelAnimation() {
        this.panelReveal = 0f;
        this.panelSlide = 1f;
        this.currentPanelH = -1f;
        this.currentPanelY = TrackerConstants.MARGIN_TOP;
    }

    public void render(GuiGraphics g, int screenWidth, int screenHeight, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        boolean isBlockingScreen = mc.screen instanceof QuestJournalScreen || mc.screen instanceof DialogueScreen;

        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        dt = Math.min((now - lastRenderTime) / 1000f, 0.1f);
        lastRenderTime = now;

        Map<String, QuestRuntimeData> active = ClientQuestCache.INSTANCE.getAllActiveQuests();
        QuestRuntimeData tracked = resolveTrackedQuest(active);
        if (tracked == null) return;

        String questId = tracked.getQuestId();
        currentThemeColor = ClientQuestCache.INSTANCE.getQuestThemeColor(questId, TrackerConstants.COLOR_ACCENT_DEFAULT);

        QuestDefinition def = QuestRegistry.get(ResourceLocation.tryParse(questId));
        if (def == null) return;

        syncActivePhaseOrder(tracked, def);
        boolean isActive = tracked.getState() == QuestState.ACTIVE;
        boolean shouldShow = isActive && !isBlockingScreen;
        handleDismiss(tracked, now, isActive);

        if (isActive) {
            String actualPhaseId = resolvePreferredPhaseId(tracked, def);
            if (shouldShow) {
                if (displayedPhaseId == null) {
                    displayedPhaseId = actualPhaseId;
                    targetPhaseId = actualPhaseId;
                } else if (!Objects.equals(actualPhaseId, targetPhaseId)) {
                    targetPhaseId = actualPhaseId;
                    if (panelReveal > 0.5f) {
                        isPhaseTransitioning = true;
                        phaseWipingOut = true;
                        phaseTransitionStart = now;
                    } else {
                        displayedPhaseId = actualPhaseId;
                        resetObjectiveAnimations();
                    }
                }
            }
        } else {
            displayedPhaseId = null;
            targetPhaseId = null;
            isPhaseTransitioning = false;
            activePhaseOrder.clear();
        }

        float wipeReveal = 1f, wipeDrift = 0f, wipeAlpha = 1f;
        if (isPhaseTransitioning) {
            long elapsed = now - phaseTransitionStart;
            if (phaseWipingOut) {
                float t = elapsed / TrackerConstants.TIME_WIPE_OUT;
                if (t >= 1f) {
                    t = 1f; phaseWipingOut = false; displayedPhaseId = targetPhaseId; resetObjectiveAnimations(); phaseTransitionStart = now;
                }
                float ease = (float) Math.pow(t, 4.0);
                wipeReveal = 1f - ease; wipeDrift = ease * 30f; wipeAlpha = 1f - ease;
            } else {
                float t = elapsed / TrackerConstants.TIME_WIPE_IN;
                if (t >= 1f) { t = 1f; isPhaseTransitioning = false; }
                float ease = (float) (1.0 - Math.pow(1.0 - t, 5.0));
                wipeReveal = ease; wipeDrift = -(1f - ease) * 30f; wipeAlpha = ease;
            }
        }

        panelReveal = TrackerConstants.lerp(panelReveal, shouldShow ? 1f : 0f, 0.15f, dt);
        if (completionDismissStart == 0) panelSlide = TrackerConstants.lerp(panelSlide, shouldShow ? 0f : 1f, 0.15f, dt);

        if (panelReveal < 0.01f && !shouldShow) return;

        PhaseDefinition phase = resolveDisplayedPhase(def, tracked);
        if (phase == null) return;

        List<ObjectiveEntry> objectives = phase.getObjectives();
        Font font = mc.font;

        // 模块化调用测算高度
        int targetH = TrackerConstants.PADDING + TrackerConstants.TITLE_HEIGHT + TrackerConstants.GAP_AFTER_TITLE;
        if (activePhaseOrder.size() > 1) targetH += TrackerParallelWidget.computeHeight(activePhaseOrder);
        else targetH += 16;
        targetH += TrackerTitleWidget.computeDescriptionHeight(phase, font);
        targetH += objectives.size() * (TrackerConstants.OBJ_ROW_HEIGHT + TrackerConstants.PROGRESS_BAR_H + 6);
        targetH += TrackerConstants.PADDING;

        if (currentPanelH < 0) currentPanelH = targetH;
        currentPanelH = TrackerConstants.lerp(currentPanelH, targetH, 0.15f, dt);
        currentPanelY = TrackerConstants.lerp(currentPanelY, TrackerConstants.MARGIN_TOP + QuestToastManager.getPushDownOffset(), 0.12f, dt);

        int panelH = (int) currentPanelH;
        float slideOffset = panelSlide * (TrackerConstants.PANEL_WIDTH + TrackerConstants.MARGIN_RIGHT + 20);
        int panelX = (int) (screenWidth - TrackerConstants.PANEL_WIDTH - TrackerConstants.MARGIN_RIGHT + slideOffset);
        int panelY = (int) currentPanelY;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        int scX1 = panelX - 5;
        int scX2 = panelX + Math.max(TrackerConstants.ACCENT_WIDTH + 1, (int) (TrackerConstants.PANEL_WIDTH * wipeReveal));
        g.enableScissor(scX1, panelY - 5, scX2, panelY + panelH + 5);

        int bgAlpha = (int) (0x55 * panelReveal);
        int accentAlpha = (int) (0xFF * panelReveal);
        HudAnimUtil.drawAccentPanel(g, panelX, panelY, TrackerConstants.PANEL_WIDTH, panelH, bgAlpha << 24, (accentAlpha << 24) | (currentThemeColor & 0x00FFFFFF), TrackerConstants.ACCENT_WIDTH);

        int textX = panelX + TrackerConstants.ACCENT_WIDTH + TrackerConstants.PADDING;
        int textY = panelY + TrackerConstants.PADDING;

        // 像拼积木一样调用分离出的渲染组件
        TrackerTitleWidget.renderTitle(g, tracked, textX, textY, panelReveal, wipeAlpha, font);
        textY += TrackerConstants.TITLE_HEIGHT + TrackerConstants.GAP_AFTER_TITLE;

        if (activePhaseOrder.size() > 1) {
            textY = TrackerParallelWidget.render(g, font, tracked, def, activePhaseOrder, displayedPhaseId, currentThemeColor, textX + (int) wipeDrift, textY, panelReveal, wipeAlpha);
        } else {
            TrackerTitleWidget.renderPhaseName(g, tracked, displayedPhaseId, currentThemeColor, textX + (int) wipeDrift, textY, panelReveal, wipeAlpha, font);
            textY += 16;
        }

        textY = TrackerTitleWidget.renderDescription(g, phase, textX + (int) wipeDrift, textY, panelReveal, wipeAlpha, font);
        objectiveWidget.render(g, font, tracked, displayedPhaseId, objectives, currentThemeColor, dt, panelReveal, wipeAlpha, wipeDrift, panelX, textX, textY);

        g.disableScissor();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private void syncActivePhaseOrder(QuestRuntimeData tracked, QuestDefinition def) {
        List<String> next = new ArrayList<>();
        for (String pid : def.getPhaseIds()) if (tracked.isPhaseActive(pid)) next.add(pid);
        if (next.isEmpty()) next.addAll(tracked.getActivePhaseIds());
        activePhaseOrder = next;
    }

    private String resolvePreferredPhaseId(QuestRuntimeData tracked, QuestDefinition def) {
        if (trackedPhaseId != null && !trackedPhaseId.isEmpty() && tracked.isPhaseActive(trackedPhaseId) && def.getPhase(trackedPhaseId) != null) return trackedPhaseId;
        String current = tracked.getCurrentPhaseId();
        if (current != null && !current.isEmpty() && tracked.isPhaseActive(current) && def.getPhase(current) != null) return current;
        for (String pid : activePhaseOrder) if (def.getPhase(pid) != null) return pid;
        return "";
    }

    private PhaseDefinition resolveDisplayedPhase(QuestDefinition def, QuestRuntimeData tracked) {
        if (displayedPhaseId != null && !displayedPhaseId.isEmpty()) {
            PhaseDefinition p = def.getPhase(displayedPhaseId);
            if (p != null) return p;
        }
        String fallback = resolvePreferredPhaseId(tracked, def);
        if (!fallback.isEmpty()) {
            displayedPhaseId = fallback;
            return def.getPhase(fallback);
        }
        return null;
    }

    private QuestRuntimeData resolveTrackedQuest(Map<String, QuestRuntimeData> active) {
        QuestRuntimeData data = ClientQuestCache.INSTANCE.resolveTrackedQuest(trackedQuestId);
        if (data != null) {
            if (trackedQuestId == null) { trackedQuestId = data.getQuestId(); resetObjectiveAnimations(); }
            return data;
        }
        if (trackedQuestId != null) {
            trackedQuestId = null; trackedPhaseId = null; displayedPhaseId = null; targetPhaseId = null; currentPanelH = -1f; activePhaseOrder.clear();
            resetObjectiveAnimations();
        }
        return null;
    }

    private void handleDismiss(QuestRuntimeData tracked, long now, boolean shouldShow) {
        if (tracked != null && (tracked.getState() == QuestState.COMPLETED || tracked.getState() == QuestState.FAILED)) {
            if (completionDismissStart == 0) completionDismissStart = now;
            float elapsed = now - completionDismissStart;
            if (elapsed >= TrackerConstants.DISMISS_DELAY) panelSlide = TrackerConstants.easeInCubic(Math.min(1f, (elapsed - TrackerConstants.DISMISS_DELAY) / TrackerConstants.DISMISS_SLIDE_TIME));
        } else completionDismissStart = 0;
    }

    private void resetObjectiveAnimations() {
        objectiveWidget.reset();
        panelSlide = 1f;
        completionDismissStart = 0;
    }
}