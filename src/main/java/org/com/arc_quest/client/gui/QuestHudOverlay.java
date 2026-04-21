package org.com.arc_quest.client.gui;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.com.arc_quest.client.gui.render.QuestSplashRenderer;
import org.com.arc_quest.quest.api.PhaseDefinition;
import org.com.arc_quest.quest.api.QuestDefinition;
import org.com.arc_quest.quest.capability.QuestRuntimeData;
import org.com.arc_quest.quest.network.ClientQuestCache;
import org.com.arc_quest.quest.registry.QuestRegistry;

import java.util.Map;

public class QuestHudOverlay implements IGuiOverlay {

    public static final QuestHudOverlay INSTANCE = new QuestHudOverlay();
    private static final int POPUP_H = 36;
    private final QuestTrackerPanel trackerPanel = new QuestTrackerPanel();
    private long lastRenderTime = 0;
    private float dt = 0f;
    private String lastKnownPhaseId = null;
    private PhaseUpdateToast phaseUpdateToast = null;
    private BranchChoiceToast branchChoiceToast = null;
    private float currentPhasePopupY = -1;
    private float currentBranchToastY = -1;

    private QuestHudOverlay() {
    }

    public void setTrackedQuest(String questId) {
        trackerPanel.setTrackedQuest(questId);
    }

    public String getTrackedQuestId() {
        return trackerPanel.getTrackedQuestId();
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (QuestSplashRenderer.isActive()) return;

        // 模态界面检测（时空冻结）
        boolean isBlockingScreen = mc.screen instanceof QuestJournalScreen || mc.screen instanceof DialogueScreen;

        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        dt = Math.min((now - lastRenderTime) / 1000f, 0.1f);
        lastRenderTime = now;

        Map<String, QuestRuntimeData> active = ClientQuestCache.INSTANCE.getAllActiveQuests();
        QuestRuntimeData tracked = resolveTrackedQuest(active);

        if (tracked == null) return;

        String questId = tracked.getQuestId();
        int currentThemeColor = ClientQuestCache.INSTANCE.getQuestThemeColor(questId, 0xFF4FC3F7);
        QuestDefinition def = ClientQuestCache.INSTANCE.getCurrentPhase(questId) != null 
            ? QuestRegistry.get(ResourceLocation.tryParse(questId)) : null;

        updatePhasePopup(tracked, def, currentThemeColor);

        int leftBaseX = 20;
        int centerY = screenHeight / 2;
        int gap = 8;

        boolean isPhaseActive = (phaseUpdateToast != null);
        boolean isBranchActive = (branchChoiceToast != null);

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
            currentPhasePopupY = QuestAnimUtil.lerp(currentPhasePopupY, targetPhaseY, 0.15f, dt);
            currentBranchToastY = QuestAnimUtil.lerp(currentBranchToastY, targetBranchY, 0.15f, dt);
        }

        // 渲染左侧Toast（保持不透明度，暂停动画）
        if (isPhaseActive) {
            if (!phaseUpdateToast.render(g, mc.font, leftBaseX, (int) currentPhasePopupY, 1f, isBlockingScreen)) {
                phaseUpdateToast = null;
            }
        }
        if (isBranchActive) {
            if (!branchChoiceToast.render(g, leftBaseX, (int) currentBranchToastY, 1f, partialTick, isBlockingScreen)) {
                branchChoiceToast = null;
                currentBranchToastY = -1;
            }
        }

        trackerPanel.render(g, screenWidth, screenHeight, partialTick);
        QuestToastManager.render(g, screenWidth, screenHeight, isBlockingScreen);
    }

    private void updatePhasePopup(QuestRuntimeData tracked, QuestDefinition def, int themeColor) {
        if (tracked != null) {
            String curPhaseId = tracked.getCurrentPhaseId();
            if (lastKnownPhaseId != null && !lastKnownPhaseId.equals(curPhaseId)) {
                // 使用缓存层提供的便捷方法获取阶段名称
                String phaseName = ClientQuestCache.INSTANCE.getPhaseDisplayName(tracked.getQuestId(), curPhaseId);
                this.phaseUpdateToast = new PhaseUpdateToast(phaseName, themeColor);
            }
            lastKnownPhaseId = curPhaseId;
        } else {
            lastKnownPhaseId = null;
        }
    }

    private QuestRuntimeData resolveTrackedQuest(Map<String, QuestRuntimeData> active) {
        String trackedQuestId = trackerPanel.getTrackedQuestId();
        QuestRuntimeData data = ClientQuestCache.INSTANCE.resolveTrackedQuest(trackedQuestId);
        
        // 如果返回的任务与追踪 ID 不匹配，清除追踪
        if (data != null && trackedQuestId != null && !data.getQuestId().equals(trackedQuestId)) {
            trackerPanel.setTrackedQuest(null);
        } else if (data == null && trackedQuestId != null) {
            trackerPanel.setTrackedQuest(null);
        } else if (data != null && trackedQuestId == null) {
            trackerPanel.setTrackedQuest(data.getQuestId());
        }
        
        return data;
    }

    public void showBranchChoiceToast(String questId) {
        this.branchChoiceToast = new BranchChoiceToast(questId);
    }

    public void clearBranchChoiceToast() {
        if (this.branchChoiceToast != null) this.branchChoiceToast.dismiss();
    }

    public String getBranchChoiceQuestId() {
        return branchChoiceToast != null ? branchChoiceToast.getQuestId() : null;
    }
}