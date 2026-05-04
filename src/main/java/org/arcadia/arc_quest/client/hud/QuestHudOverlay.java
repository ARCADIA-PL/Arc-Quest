package org.arcadia.arc_quest.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.QuestArcHudController;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.toast.ArcQuestCenterToastManager;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.toast.ArcQuestPhaseToastViewModel;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class QuestHudOverlay implements IGuiOverlay {

    public static final QuestHudOverlay INSTANCE = new QuestHudOverlay();

    private String lastTrackedQuestId = null;
    private String lastKnownPhaseId = null;
    private Set<String> lastKnownActivePhaseIds = new LinkedHashSet<>();

    private QuestHudOverlay() {
    }

    public void setTrackedQuest(String questId) {
        QuestArcHudController.INSTANCE.setTrackedQuest(questId);
    }

    public void setTrackedFocus(String questId, String phaseId) {
        QuestArcHudController.INSTANCE.setTrackedFocus(questId, phaseId);
    }

    public String getTrackedPhaseId() {
        return QuestArcHudController.INSTANCE.getTrackedPhaseId();
    }

    public String getTrackedQuestId() {
        return QuestArcHudController.INSTANCE.getTrackedQuestId();
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;



        Map<String, QuestRuntimeData> active = ClientQuestCache.INSTANCE.getAllActiveQuests();
        QuestRuntimeData tracked = resolveTrackedQuest(active);

        if (tracked == null) {
            resetPhaseTrackingState();
            QuestArcHudController.INSTANCE.setOverlayPressure(0);
            QuestArcHudController.INSTANCE.render(g, partialTick);
            return;
        }

        String questId = tracked.getQuestId();
        int currentThemeColor = ClientQuestCache.INSTANCE.getQuestThemeColor(questId, 0xFF4FC3F7);

        updatePhasePopup(tracked, currentThemeColor);

        QuestArcHudController.INSTANCE.setOverlayPressure(ArcQuestCenterToastManager.overlayPressure());
        QuestArcHudController.INSTANCE.render(g, partialTick);
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
            ArcQuestCenterToastManager.showPhase(phaseName, themeColor, ArcQuestPhaseToastViewModel.Kind.ADDED);
        } else if (removedPhase != null && !removedPhase.isEmpty()) {
            String phaseName = ClientQuestCache.INSTANCE.getPhaseDisplayName(questId, removedPhase);
            ArcQuestCenterToastManager.showPhase(phaseName, themeColor, ArcQuestPhaseToastViewModel.Kind.COMPLETED);
        } else if (lastKnownPhaseId != null && !lastKnownPhaseId.equals(currentPhaseId) && currentPhaseId != null && !currentPhaseId.isEmpty()) {
            String phaseName = ClientQuestCache.INSTANCE.getPhaseDisplayName(questId, currentPhaseId);
            ArcQuestCenterToastManager.showPhase(phaseName, themeColor, ArcQuestPhaseToastViewModel.Kind.SWITCHED);
        }

        lastKnownPhaseId = currentPhaseId;
        lastKnownActivePhaseIds = currentActive;
    }

    private QuestRuntimeData resolveTrackedQuest(Map<String, QuestRuntimeData> active) {
        String trackedQuestId = QuestArcHudController.INSTANCE.getTrackedQuestId();
        QuestRuntimeData data = ClientQuestCache.INSTANCE.resolveTrackedQuest(trackedQuestId);

        if (data != null && trackedQuestId != null && !data.getQuestId().equals(trackedQuestId)) {
            QuestArcHudController.INSTANCE.setTrackedQuest(null);
            return null;
        }
        if (data == null && trackedQuestId != null) {
            QuestArcHudController.INSTANCE.setTrackedQuest(null);
            return null;
        }
        if (data != null && trackedQuestId == null) {
            QuestArcHudController.INSTANCE.setTrackedQuest(data.getQuestId());
            return data;
        }
        if (data == null && trackedQuestId == null && !active.isEmpty()) {
            QuestRuntimeData first = active.values().iterator().next();
            if (first != null) {
                QuestArcHudController.INSTANCE.setTrackedQuest(first.getQuestId());
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

    public void showBranchChoiceToast(String questId) {
        showBranchChoiceToast(questId, null);
    }

    public void showBranchChoiceToast(String questId, String phaseId) {
        ArcQuestCenterToastManager.showBranchChoice(questId, phaseId);
    }

    public void clearBranchChoiceToast() {
        ArcQuestCenterToastManager.clearBranchChoice();
    }

    public void clearBranchChoiceToast(String questId, String phaseId) {
        ArcQuestCenterToastManager.clearBranchChoice(questId, phaseId);
    }

    public String getBranchChoiceQuestId() {
        return ArcQuestCenterToastManager.branchToast() != null ? ArcQuestCenterToastManager.branchToast().questId : null;
    }
}