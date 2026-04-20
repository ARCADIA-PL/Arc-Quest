package org.com.arc_quest.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.com.arc_quest.client.gui.render.QuestIconRenderer;
import org.com.arc_quest.quest.api.*;
import org.com.arc_quest.quest.capability.QuestRuntimeData;
import org.com.arc_quest.quest.network.ClientQuestCache;
import org.com.arc_quest.quest.registry.QuestRegistry;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class QuestTrackerPanel {

    // 布局常量
    private static final int PANEL_WIDTH = 175;
    private static final int MARGIN_RIGHT = 6;
    private static final int MARGIN_TOP = 30; // 基础高度
    private static final int ACCENT_WIDTH = 3;
    private static final int TITLE_HEIGHT = 14;
    private static final int OBJ_ROW_HEIGHT = 11;
    private static final int PROGRESS_BAR_H = 3;
    private static final int PADDING = 5;
    private static final int GAP_AFTER_TITLE = 2;
    private static final int COLOR_ACCENT_DEFAULT = 0xFF4FC3F7;
    private static final float DISMISS_DELAY = 2000f;
    private static final float DISMISS_SLIDE_TIME = 500f;
    private static final float TIME_WIPE_OUT = 250f;
    private static final float TIME_WIPE_IN = 350f;

    // 动画状态
    private float panelReveal = 0f;
    private float panelSlide = 1f;
    private float currentPanelH = -1f;

    // Y轴动态避让
    private float currentPanelY = MARGIN_TOP;

    private long lastRenderTime = 0;
    private float dt = 0f;

    private String trackedQuestId = null;

    private String targetPhaseId = null;
    private String displayedPhaseId = null;
    private boolean isPhaseTransitioning = false;
    private boolean phaseWipingOut = false;
    private long phaseTransitionStart = 0;

    private float[] objReveal = new float[0];
    private int[] lastKnownProgress = new int[0];
    private float[] objPulse = new float[0];
    private boolean[] objCompletedFlag = new boolean[0];
    private float[] objCompleteAnim = new float[0];
    private float[] animProgressRatio = new float[0];
    private long completionDismissStart = 0;

    private int currentThemeColor = COLOR_ACCENT_DEFAULT;



    private static float lerp(float current, float target, float speed, float dt) {
        return current + (target - current) * Math.min(1f, speed * dt * 60f);
    }

    private static float easeOutCubic(float t) {
        float u = 1f - Math.min(1f, Math.max(0f, t));
        return 1f - u * u * u;
    }

    private static float easeInCubic(float t) {
        float v = Math.min(1f, Math.max(0f, t));
        return v * v * v;
    }

    public void setTrackedQuest(String questId) {
        if (!Objects.equals(this.trackedQuestId, questId)) {
            this.trackedQuestId = questId;
            this.displayedPhaseId = null;
            this.targetPhaseId = null;
            this.currentPanelH = -1f;
            resetObjectiveAnimations();
        }
    }

    public String getTrackedQuestId() {
        return trackedQuestId;
    }

    public void resetPanelAnimation() {
        this.panelReveal = 0f;
        this.panelSlide = 1f;
        this.currentPanelH = -1f;
        this.currentPanelY = MARGIN_TOP;
    }

    public void render(GuiGraphics g, int screenWidth, int screenHeight, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        boolean isBlockingScreen = mc.screen instanceof QuestJournalScreen ||
                (mc.screen != null && mc.screen.getClass().getSimpleName().equals("DialogueScreen"));

        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        dt = Math.min((now - lastRenderTime) / 1000f, 0.1f);
        lastRenderTime = now;

        Map<String, QuestRuntimeData> active = ClientQuestCache.INSTANCE.getAllActiveQuests();
        QuestRuntimeData tracked = resolveTrackedQuest(active);

        ResourceLocation questRl = tracked != null ? ResourceLocation.tryParse(tracked.getQuestId()) : null;
        QuestDefinition def = questRl != null ? QuestRegistry.get(questRl) : null;
        currentThemeColor = (def != null && def.getThemeColor() != 0xFFFFFFFF) ? def.getThemeColor() : COLOR_ACCENT_DEFAULT;

        boolean isActive = tracked != null && tracked.getState() == QuestState.ACTIVE;
        boolean shouldShow = isActive && !isBlockingScreen;

        handleDismiss(tracked, now, isActive);

        if (isActive) {
            String actualPhaseId = tracked.getCurrentPhaseId();
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
        }

        float wipeReveal = 1f, wipeDrift = 0f, wipeAlpha = 1f;
        if (isPhaseTransitioning) {
            long elapsed = now - phaseTransitionStart;
            if (phaseWipingOut) {
                float t = elapsed / TIME_WIPE_OUT;
                if (t >= 1f) {
                    t = 1f;
                    phaseWipingOut = false;
                    displayedPhaseId = targetPhaseId;
                    resetObjectiveAnimations();
                    phaseTransitionStart = now;
                }
                float ease = (float) Math.pow(t, 4.0);
                wipeReveal = 1f - ease;
                wipeDrift = ease * 30f;
                wipeAlpha = 1f - ease;
            } else {
                float t = elapsed / TIME_WIPE_IN;
                if (t >= 1f) {
                    t = 1f;
                    isPhaseTransitioning = false;
                }
                float ease = (float) (1.0 - Math.pow(1.0 - t, 5.0));
                wipeReveal = ease;
                wipeDrift = -(1f - ease) * 30f;
                wipeAlpha = ease;
            }
        }

        float targetReveal = shouldShow ? 1f : 0f;
        panelReveal = lerp(panelReveal, targetReveal, 0.15f, dt);

        float targetSlide = shouldShow ? 0f : 1f;
        if (completionDismissStart == 0) {
            panelSlide = lerp(panelSlide, targetSlide, 0.15f, dt);
        }

        if (panelReveal < 0.01f && !shouldShow) return;

        if (def == null) return;
        PhaseDefinition phase = def.getPhase(displayedPhaseId);
        if (phase == null) return;

        List<ObjectiveEntry> objectives = phase.getObjectives();
        int objCount = objectives.size();
        ensureArraySize(objCount);

        int targetH = PADDING + TITLE_HEIGHT + GAP_AFTER_TITLE + 18 + (objCount * (OBJ_ROW_HEIGHT + PROGRESS_BAR_H + 6)) + PADDING;
        if (currentPanelH < 0) currentPanelH = targetH;
        currentPanelH = lerp(currentPanelH, targetH, 0.15f, dt);

        // 动态避让Toast队列
        float targetY = MARGIN_TOP + QuestToastManager.getPushDownOffset();
        currentPanelY = lerp(currentPanelY, targetY, 0.12f, dt); // 使用缓动极其丝滑地下移或上移

        int panelH = (int) currentPanelH;
        float slideOffset = panelSlide * (PANEL_WIDTH + MARGIN_RIGHT + 20);
        int panelX = (int) (screenWidth - PANEL_WIDTH - MARGIN_RIGHT + slideOffset);
        int panelY = (int) currentPanelY;

        float alpha = panelReveal;
        Font font = mc.font;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        int scX1 = panelX - 5;
        int scX2 = panelX + Math.max(ACCENT_WIDTH + 1, (int) (PANEL_WIDTH * wipeReveal));
        g.enableScissor(scX1, panelY - 5, scX2, panelY + panelH + 5);

        int bgAlpha = (int) (0x55 * alpha);
        int accentAlpha = (int) (0xFF * alpha);

        QuestAnimUtil.drawAccentPanel(g, panelX, panelY, PANEL_WIDTH, panelH, bgAlpha << 24, (accentAlpha << 24) | (currentThemeColor & 0x00FFFFFF), ACCENT_WIDTH);

        int textX = panelX + ACCENT_WIDTH + PADDING;
        int textY = panelY + PADDING;

        renderTitle(g, def, textX, textY, alpha, wipeAlpha, font);
        textY += TITLE_HEIGHT + GAP_AFTER_TITLE;

        renderPhaseName(g, def, phase, textX + (int) wipeDrift, textY, alpha, wipeAlpha, font);
        textY += 16;

        renderObjectives(g, font, tracked, objectives, objCount, alpha, wipeAlpha, wipeDrift, panelX, textX, textY);

        g.disableScissor();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private void renderTitle(GuiGraphics g, QuestDefinition def, int textX, int textY, float alpha, float wipeAlpha, Font font) {
        int titleA = (int) (255 * alpha * wipeAlpha);
        if (titleA > 8) {
            int iconOffset = 0;
            if (def.getVisualConfig().getIcon(IconPosition.HUD_TRACKER).isPresent()) {
                int finalTextY = textY;
                def.getVisualConfig().getIcon(IconPosition.HUD_TRACKER).ifPresent(icon -> {
                    RenderSystem.setShaderColor(1f, 1f, 1f, alpha * wipeAlpha);
                    QuestIconRenderer.renderIcon(g, icon, textX, finalTextY + 1, 12, 12);
                    RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                });
                iconOffset = 16;
            }

            String title = font.plainSubstrByWidth(def.getDisplayName().getString(), PANEL_WIDTH - ACCENT_WIDTH - PADDING * 2 - 4 - iconOffset);
            g.drawString(font, title, textX + iconOffset, textY, QuestAnimUtil.withAlpha(0xFFFFFF, titleA), true);
        }
    }

    private void renderPhaseName(GuiGraphics g, QuestDefinition def, PhaseDefinition phase, int textX, int textY, float alpha, float wipeAlpha, Font font) {
        int subA = (int) (255 * alpha * wipeAlpha);
        if (subA > 5) {
            g.fill(textX, textY + 1, textX + 2, textY + 10, QuestAnimUtil.withAlpha(currentThemeColor, subA));

            g.pose().pushPose();
            g.pose().translate(textX + 7, textY + 1, 0);
            g.pose().scale(0.95f, 0.95f, 1f);

            String phaseName = displayedPhaseId;
            if (phase != null && phase.getDisplayName() != null && !phase.getDisplayName().getString().isEmpty()) {
                phaseName = phase.getDisplayName().getString();
            }

            String phasePrefix = Component.translatable("arc_quest.hud.phase_prefix", phaseName).getString();
            g.drawString(font, phasePrefix, 0, 0, QuestAnimUtil.withAlpha(0xEEEEEE, subA), true);
            g.pose().popPose();
        }
    }

    private void renderObjectives(GuiGraphics g, Font font, QuestRuntimeData tracked, List<ObjectiveEntry> objectives, int objCount, float alpha, float wipeAlpha, float wipeDrift, int panelX, int textX, int textY) {
        for (int i = 0; i < objCount; i++) {
            ObjectiveEntry obj = objectives.get(i);
            int progress = tracked.getObjectiveProgress(i);
            int required = obj.getRequiredCount();
            boolean complete = progress >= required;

            objReveal[i] = lerp(objReveal[i], 1f, 0.12f + i * 0.02f, dt);
            float objAlpha = alpha * wipeAlpha * easeOutCubic(Math.min(1f, objReveal[i]));

            if (progress != lastKnownProgress[i] && lastKnownProgress[i] >= 0) objPulse[i] = 1f;
            lastKnownProgress[i] = progress;
            objPulse[i] = lerp(objPulse[i], 0f, 0.12f, dt);

            boolean was = objCompletedFlag[i];
            objCompletedFlag[i] = complete;
            if (!was && complete) objCompleteAnim[i] = 1f;
            objCompleteAnim[i] = lerp(objCompleteAnim[i], 0f, 0.08f, dt);

            float targetRatio = required > 0 ? (float) progress / required : 0f;
            float diff = targetRatio - animProgressRatio[i];
            if (Math.abs(diff) > 0.001f) {
                float rate = targetRatio > animProgressRatio[i] ? 12.0f : 15.0f;
                float lerpFactor = 1.0f - (float) Math.exp(-rate * dt);
                animProgressRatio[i] += diff * lerpFactor;
            } else {
                animProgressRatio[i] = targetRatio;
            }
            float displayRatio = animProgressRatio[i];

            if (objAlpha < 0.02f) {
                textY += OBJ_ROW_HEIGHT + PROGRESS_BAR_H + 6;
                continue;
            }

            float rowSlide = (1f - easeOutCubic(Math.min(1f, objReveal[i]))) * 30f;
            int rowX = textX + (int) rowSlide + (int) wipeDrift;
            int aInt = (int) (255 * objAlpha);

            float cScale = 1f;
            int cGlow = 0;
            if (objCompleteAnim[i] > 0.05f) {
                float t = objCompleteAnim[i];
                cScale = 1f + 0.15f * easeOutCubic(t) * (float) Math.sin(t * Math.PI);
                cGlow = (int) (255 * t * objAlpha);
            }

            String prefix = complete ? Component.translatable("arc_quest.hud.objective_complete_prefix").getString() : Component.translatable("arc_quest.hud.objective_active_prefix").getString();
            String objText = prefix + obj.getDisplayText().getString();
            String progressText = progress + "/" + required;

            int textColor = complete ? QuestAnimUtil.withAlpha(0x88FF88, aInt) : QuestAnimUtil.withAlpha(0xCCCCCC, aInt);
            if (objPulse[i] > 0.05f)
                textColor = QuestAnimUtil.lerpColor(textColor, QuestAnimUtil.withAlpha(0xFFFFFF, (int) (255 * objPulse[i] * objAlpha)), objPulse[i]);
            if (cGlow > 0)
                textColor = QuestAnimUtil.lerpColor(textColor, QuestAnimUtil.withAlpha(0xFFFFFF, cGlow), objCompleteAnim[i] * 0.7f);

            int numW = (int) (font.width(progressText) * 0.8f);
            int numX = panelX + PANEL_WIDTH - PADDING - numW + (int) wipeDrift;

            int maxObjTextWidth = (int) ((numX - rowX - 8) / 0.85f);
            String safeObjText = font.plainSubstrByWidth(objText, Math.max(10, maxObjTextWidth));

            g.pose().pushPose();
            g.pose().translate(rowX, textY, 0);
            g.pose().scale(0.85f * cScale, 0.85f * cScale, 1f);
            g.drawString(font, safeObjText, 0, 0, textColor, true);
            g.pose().popPose();

            g.pose().pushPose();
            g.pose().translate(numX, textY + 1, 0);
            g.pose().scale(0.8f, 0.8f, 1f);
            g.drawString(font, progressText, 0, 0, textColor, true);
            g.pose().popPose();

            textY += OBJ_ROW_HEIGHT;

            int barW = PANEL_WIDTH - ACCENT_WIDTH - PADDING * 2 - (int) rowSlide;
            int barBg = ((int) (0x40 * objAlpha) << 24) | 0xFFFFFF;
            int barFill = complete ? QuestAnimUtil.withAlpha(0x66FF66, (int) (0xCC * objAlpha)) : QuestAnimUtil.withAlpha(currentThemeColor, (int) (0xCC * objAlpha));
            int barGlow = QuestAnimUtil.withAlpha(0xFFFFFF, (int) (0xFF * objAlpha));

            if (objPulse[i] > 0.05f)
                barFill = QuestAnimUtil.lerpColor(barFill, QuestAnimUtil.withAlpha(0xFFFFFF, (int) (200 * objPulse[i] * objAlpha)), objPulse[i] * 0.5f);

            QuestAnimUtil.drawProgressBarGlow(g, rowX, textY, barW, PROGRESS_BAR_H, displayRatio, barBg, barFill, barGlow);
            textY += PROGRESS_BAR_H + 6;
        }
    }

    private QuestRuntimeData resolveTrackedQuest(Map<String, QuestRuntimeData> active) {
        if (trackedQuestId != null) {
            QuestRuntimeData data = active.get(trackedQuestId);
            if (data != null) return data;
            trackedQuestId = null;
            displayedPhaseId = null;
            targetPhaseId = null;
            currentPanelH = -1f;
            resetObjectiveAnimations();
        }
        if (!active.isEmpty()) {
            var first = active.entrySet().iterator().next();
            trackedQuestId = first.getKey();
            displayedPhaseId = null;
            targetPhaseId = null;
            currentPanelH = -1f;
            resetObjectiveAnimations();
            return first.getValue();
        }
        trackedQuestId = null;
        return null;
    }

    private void handleDismiss(QuestRuntimeData tracked, long now, boolean shouldShow) {
        if (tracked != null && (tracked.getState() == QuestState.COMPLETED || tracked.getState() == QuestState.FAILED)) {
            if (completionDismissStart == 0) completionDismissStart = now;
            float elapsed = now - completionDismissStart;
            if (elapsed >= DISMISS_DELAY) {
                float slideT = Math.min(1f, (elapsed - DISMISS_DELAY) / DISMISS_SLIDE_TIME);
                panelSlide = easeInCubic(slideT);
            }
        } else {
            completionDismissStart = 0;
        }
    }

    private void ensureArraySize(int size) {
        if (objReveal.length != size) {
            float[] nr = new float[size], np = new float[size], nc = new float[size], nRatio = new float[size];
            int[] ni = new int[size];
            boolean[] nb = new boolean[size];
            Arrays.fill(ni, -1);
            int c = Math.min(objReveal.length, size);
            System.arraycopy(objReveal, 0, nr, 0, c);
            System.arraycopy(objPulse, 0, np, 0, c);
            System.arraycopy(lastKnownProgress, 0, ni, 0, Math.min(lastKnownProgress.length, size));
            System.arraycopy(objCompletedFlag, 0, nb, 0, Math.min(objCompletedFlag.length, size));
            System.arraycopy(objCompleteAnim, 0, nc, 0, Math.min(objCompleteAnim.length, size));
            System.arraycopy(animProgressRatio, 0, nRatio, 0, Math.min(animProgressRatio.length, size));
            objReveal = nr;
            objPulse = np;
            lastKnownProgress = ni;
            objCompletedFlag = nb;
            objCompleteAnim = nc;
            animProgressRatio = nRatio;
        }
    }

    private void resetObjectiveAnimations() {
        objReveal = new float[0];
        objPulse = new float[0];
        lastKnownProgress = new int[0];
        objCompletedFlag = new boolean[0];
        objCompleteAnim = new float[0];
        animProgressRatio = new float[0];
        panelSlide = 1f;
        completionDismissStart = 0;
    }
}