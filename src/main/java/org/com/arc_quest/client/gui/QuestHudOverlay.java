package org.com.arc_quest.client.gui;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.com.arc_quest.client.gui.dialogue.DialogueScreen;
import org.com.arc_quest.client.gui.quest.BranchChoiceToast;
import org.com.arc_quest.client.gui.quest.PhaseUpdateToast;
import org.com.arc_quest.client.gui.quest.journal.QuestJournalScreen;
import org.com.arc_quest.client.gui.quest.QuestToastManager;
import org.com.arc_quest.client.gui.quest.QuestTrackerPanel;
import org.com.arc_quest.client.gui.render.QuestSplashRenderer;
import org.com.arc_quest.quest.capability.QuestRuntimeData;
import org.com.arc_quest.quest.network.ClientQuestCache;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class QuestHudOverlay implements IGuiOverlay {

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

    private QuestHudOverlay() {}

    public void setTrackedQuest(String questId) { trackerPanel.setTrackedQuest(questId); }
    public void setTrackedFocus(String questId, String phaseId) { trackerPanel.setTrackedFocus(questId, phaseId); }
    public String getTrackedPhaseId() { return trackerPanel.getTrackedPhaseId(); }
    public String getTrackedQuestId() { return trackerPanel.getTrackedQuestId(); }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        // 核心修复：就算 Splash 正在播放，也不能直接 return！我们必须接着计算冻结逻辑
        boolean isSplashActive = QuestSplashRenderer.isActive();
        boolean isBlockingScreen = isSplashActive || mc.screen instanceof QuestJournalScreen || mc.screen instanceof DialogueScreen;

        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        dt = Math.min((now - lastRenderTime) / 1000f, 0.1f);
        lastRenderTime = now;

        Map<String, QuestRuntimeData> active = ClientQuestCache.INSTANCE.getAllActiveQuests();
        QuestRuntimeData tracked = resolveTrackedQuest(active);

        if (tracked == null) {
            resetPhaseTrackingState();
            return;
        }

        String questId = tracked.getQuestId();
        int currentThemeColor = ClientQuestCache.INSTANCE.getQuestThemeColor(questId, 0xFF4FC3F7);

        updatePhasePopup(tracked, currentThemeColor);

        int centerY = screenHeight / 2;
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

        // 左侧 Toast (即使被遮挡也必须调用 render，内部如果收到 isBlockingScreen = true 会自动只计算冻结时间而不渲染)
        if (isPhaseActive) {
            if (!phaseUpdateToast.render(g, mc.font, LEFT_BASE_X, (int) currentPhasePopupY, 1f, isBlockingScreen)) {
                phaseUpdateToast = null;
            }
        }
        if (isBranchActive) {
            if (!branchChoiceToast.render(g, LEFT_BASE_X, (int) currentBranchToastY, 1f, partialTick, isBlockingScreen)) {
                branchChoiceToast = null;
                currentBranchToastY = -1;
            }
        }

        if (!isSplashActive && !isBlockingScreen) {
            trackerPanel.render(g, screenWidth, screenHeight, partialTick);
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
            String phaseName = ClientQuestCache.INSTANCE.getPhaseDisplayName(questId, addedPhase);
            phaseUpdateToast = new PhaseUpdateToast(phaseName, themeColor, PhaseUpdateToast.Kind.ADDED);
        } else if (removedPhase != null && !removedPhase.isEmpty()) {
            String phaseName = ClientQuestCache.INSTANCE.getPhaseDisplayName(questId, removedPhase);
            phaseUpdateToast = new PhaseUpdateToast(phaseName, themeColor, PhaseUpdateToast.Kind.COMPLETED);
        } else if (lastKnownPhaseId != null && !lastKnownPhaseId.equals(currentPhaseId) && currentPhaseId != null && !currentPhaseId.isEmpty()) {
            String phaseName = ClientQuestCache.INSTANCE.getPhaseDisplayName(questId, currentPhaseId);
            phaseUpdateToast = new PhaseUpdateToast(phaseName, themeColor, PhaseUpdateToast.Kind.SWITCHED);
        }

        lastKnownPhaseId = currentPhaseId;
        lastKnownActivePhaseIds = currentActive;
    }

    private QuestRuntimeData resolveTrackedQuest(Map<String, QuestRuntimeData> active) {
        String trackedQuestId = trackerPanel.getTrackedQuestId();
        QuestRuntimeData data = ClientQuestCache.INSTANCE.resolveTrackedQuest(trackedQuestId);

        if (data != null && trackedQuestId != null && !data.getQuestId().equals(trackedQuestId)) {
            trackerPanel.setTrackedQuest(null);
            return null;
        }
        if (data == null && trackedQuestId != null) {
            trackerPanel.setTrackedQuest(null);
            return null;
        }
        if (data != null && trackedQuestId == null) {
            trackerPanel.setTrackedQuest(data.getQuestId());
            return data;
        }
        if (data == null && trackedQuestId == null && !active.isEmpty()) {
            QuestRuntimeData first = active.values().iterator().next();
            if (first != null) {
                trackerPanel.setTrackedQuest(first.getQuestId());
                return first;
            }
        }
        return data;
    }

    private void resetPhaseTrackingState() {
        lastTrackedQuestId = null;
        lastKnownPhaseId = null;
        lastKnownActivePhaseIds.clear();
    }

    public void showBranchChoiceToast(String questId) { showBranchChoiceToast(questId, null); }
    public void showBranchChoiceToast(String questId, String phaseId) {
        if (branchChoiceToast != null && branchChoiceToast.sameTarget(questId, phaseId)) return;
        this.branchChoiceToast = new BranchChoiceToast(questId, phaseId);
    }
    public void clearBranchChoiceToast() { if (this.branchChoiceToast != null) this.branchChoiceToast.dismiss(); }
    public void clearBranchChoiceToast(String questId, String phaseId) {
        if (this.branchChoiceToast != null && this.branchChoiceToast.sameTarget(questId, phaseId)) this.branchChoiceToast.dismiss();
    }
    public String getBranchChoiceQuestId() { return branchChoiceToast != null ? branchChoiceToast.getQuestId() : null; }
}