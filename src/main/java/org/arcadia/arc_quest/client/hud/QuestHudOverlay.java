package org.arcadia.arc_quest.client.hud;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.dialogue.DialogueScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.splash.QuestSplashRenderer;
import org.arcadia.arc_quest.client.hud.quest.toast.BranchChoiceToast;
import org.arcadia.arc_quest.client.hud.quest.toast.PhaseUpdateToast;
import org.arcadia.arc_quest.client.hud.quest.toast.QuestToastManager;
import org.arcadia.arc_quest.client.hud.quest.tracker.QuestTrackerPanel;
import org.arcadia.arc_quest.client.hud.quest.trackingmenu.QuestTrackingMenuScreen;
import org.arcadia.arc_quest.client.quest.tracking.ClientQuestTrackingController;
import org.arcadia.arc_quest.client.quest.tracking.ClientQuestTrackingStore;
import org.arcadia.arc_quest.config.ArcQuestToastConfig;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class QuestHudOverlay implements LayeredDraw.Layer {

    public static final QuestHudOverlay INSTANCE = new QuestHudOverlay();

    private static final int POPUP_H = 36;
    private static final int LEFT_BASE_X = 20;
    private final QuestTrackerPanel trackerPanel = new QuestTrackerPanel();

    private long lastRenderTime = 0;
    private float dt = 0f;

    private String lastTrackedQuestId = null;
    private String lastKnownPhaseId = null;
    private Set<String> lastKnownActivePhaseIds = new LinkedHashSet<>();

    private PhaseUpdateToast phaseUpdateToast = null;
    private BranchChoiceToast branchChoiceToast = null;

    private float currentPhasePopupY = -1;
    private float currentBranchToastY = -1;

    private QuestHudOverlay() {
    }

    public void setTrackedQuest(String questId) {
        ClientQuestTrackingController.INSTANCE.requestTrack(questId);
    }

    public void setTrackedFocus(String questId, String phaseId) {
        ClientQuestTrackingController.INSTANCE.requestFocus(questId, phaseId);
    }

    public String getTrackedPhaseId() {
        return ClientQuestTrackingController.INSTANCE.trackedPhaseId();
    }

    public String getTrackedQuestId() {
        return ClientQuestTrackingController.INSTANCE.trackedQuestId();
    }

    /** 相关处理说明。 */
    public void clearClientSession() {
        trackerPanel.setTrackedQuest(null);
        lastTrackedQuestId = null;
        lastKnownPhaseId = null;
        lastKnownActivePhaseIds.clear();
        phaseUpdateToast = null;
        branchChoiceToast = null;
        currentPhasePopupY = -1;
        currentBranchToastY = -1;
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        int screenWidth = g.guiWidth();
        int screenHeight = g.guiHeight();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        refreshToastConfiguration();

        boolean isSplashActive = QuestSplashRenderer.isActive();
        boolean isBlockingScreen = isSplashActive || mc.screen instanceof QuestJournalScreen
                || mc.screen instanceof QuestTrackingMenuScreen || mc.screen instanceof DialogueScreen;

        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        dt = Math.min((now - lastRenderTime) / 1000f, 0.1f);
        lastRenderTime = now;

        Map<String, QuestRuntimeData> active = ClientQuestCache.INSTANCE.getAllActiveQuests();
        QuestRuntimeData tracked = resolveTrackedQuest(active);

        if (tracked == null) {
            resetPhaseTrackingState();
            if (!isSplashActive && !isBlockingScreen) {
                QuestToastManager.render(g, screenWidth, screenHeight);
            }
            return;
        }

        String questId = tracked.getQuestId();
        int currentThemeColor = ClientQuestCache.INSTANCE.getQuestThemeColor(questId, 0xFF4FC3F7);

        updatePhasePopup(tracked, currentThemeColor);

        float popupUiScale = HudRenderUtil.getUniversalUiScale(screenWidth, screenHeight);
        int centerY = (int) (screenHeight / popupUiScale / 2f);
        int gap = 8;

        boolean isPhaseActive = phaseUpdateToast != null;
        boolean isBranchActive = branchChoiceToast != null;

        float targetPhaseY = centerY - POPUP_H / 2f;
        float targetBranchY = centerY - POPUP_H / 2f;

        if (isPhaseActive && isBranchActive) {
            float totalH = POPUP_H * 2 + gap;
            targetPhaseY = centerY - totalH / 2f;
            targetBranchY = targetPhaseY + POPUP_H + gap;
        } else if (isPhaseActive) {
            targetBranchY = targetPhaseY;
        } else if (isBranchActive) {
            targetPhaseY = targetBranchY;
        }

        if (currentPhasePopupY < 0 || !isPhaseActive) currentPhasePopupY = targetPhaseY;
        if (currentBranchToastY < 0 || !isBranchActive) currentBranchToastY = targetBranchY;

        if (isPhaseActive || isBranchActive) {
            currentPhasePopupY = HudAnimUtil.lerp(currentPhasePopupY, targetPhaseY, 0.15f, dt);
            currentBranchToastY = HudAnimUtil.lerp(currentBranchToastY, targetBranchY, 0.15f, dt);
        }

        if (isPhaseActive || isBranchActive) {
            g.pose().pushPose();
            g.pose().scale(popupUiScale, popupUiScale, 1f);
            if (isPhaseActive) {
                if (!phaseUpdateToast.render(g, mc.font, LEFT_BASE_X, (int) currentPhasePopupY,
                        popupUiScale, 1f, isBlockingScreen)) {
                    phaseUpdateToast = null;
                }
            }
            if (isBranchActive) {
                if (!branchChoiceToast.render(g, LEFT_BASE_X, (int) currentBranchToastY,
                        popupUiScale, 1f, partialTick, isBlockingScreen)) {
                    branchChoiceToast = null;
                    currentBranchToastY = -1;
                }
            }
            g.pose().popPose();
        }

        // 解除强行截断，让 TrackerPanel 内部处理状态机，从而触发滑出/滑入动画！
        trackerPanel.render(g, screenWidth, screenHeight, partialTick);

        if (!isSplashActive && !isBlockingScreen) {
            QuestToastManager.render(g, screenWidth, screenHeight);
        }
    }

    private void updatePhasePopup(QuestRuntimeData tracked, int themeColor) {
        String questId = tracked.getQuestId();

        if (!questId.equals(lastTrackedQuestId)) {
            lastTrackedQuestId = questId;
            lastKnownPhaseId = tracked.getCurrentPhaseId();
            lastKnownActivePhaseIds = new LinkedHashSet<>(tracked.getActivePhaseIds());
            return;
        }

        String currentPhaseId = tracked.getCurrentPhaseId();
        Set<String> currentActive = new LinkedHashSet<>(tracked.getActivePhaseIds());

        String addedPhase = null;
        for (String pid : currentActive) {
            if (!lastKnownActivePhaseIds.contains(pid)) {
                addedPhase = pid;
                break;
            }
        }

        String removedPhase = null;
        for (String pid : lastKnownActivePhaseIds) {
            if (!currentActive.contains(pid)) {
                removedPhase = pid;
                break;
            }
        }

        if (addedPhase != null && !addedPhase.isEmpty()) {
            Component phaseName = ClientQuestCache.INSTANCE.getPhaseDisplayComponent(questId, addedPhase);
            phaseUpdateToast = new PhaseUpdateToast(phaseName, themeColor, PhaseUpdateToast.Kind.ADDED);
        } else if (removedPhase != null && !removedPhase.isEmpty()) {
            Component phaseName = ClientQuestCache.INSTANCE.getPhaseDisplayComponent(questId, removedPhase);
            phaseUpdateToast = new PhaseUpdateToast(phaseName, themeColor, PhaseUpdateToast.Kind.COMPLETED);
        } else if (lastKnownPhaseId != null && !lastKnownPhaseId.equals(currentPhaseId) && currentPhaseId != null && !currentPhaseId.isEmpty()) {
            Component phaseName = ClientQuestCache.INSTANCE.getPhaseDisplayComponent(questId, currentPhaseId);
            phaseUpdateToast = new PhaseUpdateToast(phaseName, themeColor, PhaseUpdateToast.Kind.SWITCHED);
        }

        lastKnownPhaseId = currentPhaseId;
        lastKnownActivePhaseIds = currentActive;
    }

    private QuestRuntimeData resolveTrackedQuest(Map<String, QuestRuntimeData> active) {
        if (!ClientQuestCache.INSTANCE.isFullSyncApplied()) return null;
        String authoritativeQuestId = ClientQuestTrackingStore.INSTANCE.trackedQuestId();
        String focusedPhaseId = ClientQuestTrackingController.INSTANCE.trackedPhaseId();
        if (!Objects.equals(trackerPanel.getTrackedQuestId(), authoritativeQuestId)
                || !Objects.equals(trackerPanel.getTrackedPhaseId(), focusedPhaseId)) {
            if (authoritativeQuestId != null && focusedPhaseId != null) {
                trackerPanel.setTrackedFocus(authoritativeQuestId, focusedPhaseId);
            } else {
                trackerPanel.setTrackedQuest(authoritativeQuestId);
            }
        }
        return authoritativeQuestId != null ? active.get(authoritativeQuestId) : null;
    }

    private void resetPhaseTrackingState() {
        lastTrackedQuestId = null;
        lastKnownPhaseId = null;
        lastKnownActivePhaseIds.clear();
    }

    public void showBranchChoiceToast(String questId) {
        showBranchChoiceToast(questId, null);
    }

    public void showBranchChoiceToast(String questId, String phaseId) {
        if (!ArcQuestToastConfig.BRANCH_CHOICE.get()) {
            branchChoiceToast = null;
            currentBranchToastY = -1;
            return;
        }
        if (branchChoiceToast != null && branchChoiceToast.sameTarget(questId, phaseId)) return;
        branchChoiceToast = new BranchChoiceToast(questId, phaseId);
    }

    public void clearBranchChoiceToast() {
        if (branchChoiceToast != null) branchChoiceToast.dismiss();
    }

    public void showPhaseUpdateToast(String phaseName, int themeColor, PhaseUpdateToast.Kind kind) {
        if (phaseName == null || phaseName.isEmpty()) return;
        showPhaseUpdateToast(Component.literal(phaseName), themeColor, kind);
    }

    public void showPhaseUpdateToast(Component phaseName, int themeColor, PhaseUpdateToast.Kind kind) {
        if (phaseName == null || phaseName.getString().isEmpty()) return;
        if (!isPhaseToastEnabled(kind)) {
            phaseUpdateToast = null;
            currentPhasePopupY = -1;
            return;
        }
        phaseUpdateToast = new PhaseUpdateToast(phaseName, themeColor, kind);
    }

    public void refreshToastConfiguration() {
        if (phaseUpdateToast != null && !isPhaseToastEnabled(phaseUpdateToast.getKind())) {
            phaseUpdateToast = null;
            currentPhasePopupY = -1;
        }
        if (branchChoiceToast != null && !ArcQuestToastConfig.BRANCH_CHOICE.get()) {
            branchChoiceToast = null;
            currentBranchToastY = -1;
        }
    }

    private static boolean isPhaseToastEnabled(PhaseUpdateToast.Kind kind) {
        if (!ArcQuestToastConfig.PHASE_ADVANCED.get()) return false;
        if (kind == null) return ArcQuestToastConfig.PHASE_ADDED.get();
        return switch (kind) {
            case ADDED -> ArcQuestToastConfig.PHASE_ADDED.get();
            case SWITCHED -> ArcQuestToastConfig.PHASE_SWITCHED.get();
            case COMPLETED -> ArcQuestToastConfig.PHASE_COMPLETED.get();
            case PENDING_CONFIRM -> ArcQuestToastConfig.PHASE_PENDING_CONFIRM.get();
        };
    }

    public void clearBranchChoiceToast(String questId, String phaseId) {
        if (branchChoiceToast != null && branchChoiceToast.sameTarget(questId, phaseId))
            branchChoiceToast.dismiss();
    }

    public String getBranchChoiceQuestId() {
        return branchChoiceToast != null ? branchChoiceToast.getQuestId() : null;
    }

}
