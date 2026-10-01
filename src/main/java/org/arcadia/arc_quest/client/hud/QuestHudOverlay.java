package org.arcadia.arc_quest.client.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.arcadia.arc_quest.client.hud.quest.toast.PhaseUpdateToast;
import org.arcadia.arc_quest.client.hud.quest.toast.QuestToastManager;
import org.arcadia.arc_quest.client.hud.quest.tracker.QuestTrackerPanel;
import org.arcadia.arc_quest.client.quest.tracking.ClientQuestTrackingController;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

/**
 * Compatibility facade for tracking and old notification callers.
 * The registered quest_hud ID stays available, but every notification is rendered
 * exactly once by quest_toasts, at the original left-centre position.
 */
public final class QuestHudOverlay implements IGuiOverlay {
    public static final QuestHudOverlay INSTANCE = new QuestHudOverlay();

    private QuestHudOverlay() {}

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

    public void clearClientSession() {
        QuestTrackerPanel.INSTANCE.clearClientSession();
        QuestToastManager.clear();
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        // Deliberately empty. Keeping the old ID does not create a second notification pass.
    }

    public void showBranchChoiceToast(String questId) {
        showBranchChoiceToast(questId, null);
    }

    public void showBranchChoiceToast(String questId, String phaseId) {
        if (questId == null || questId.isBlank()) return;
        boolean hasPhase = phaseId != null && !phaseId.isBlank();
        Component quest = ClientQuestCache.INSTANCE.getQuestDisplayComponent(questId);
        Component subject = hasPhase
                ? ClientQuestCache.INSTANCE.getPhaseDisplayComponent(questId, phaseId) : quest;
        QuestToastManager.show(QuestToastManager.ToastType.BRANCH_CHOICE, questId,
                hasPhase ? phaseId : "legacy", subject, hasPhase ? quest : Component.empty());
    }

    public void clearBranchChoiceToast() {
        String questId = getBranchChoiceQuestId();
        if (questId != null) clearBranchChoiceToast(questId, null);
    }

    public void clearBranchChoiceToast(String questId, String phaseId) {
        if (questId != null) {
            QuestToastManager.dismissPending(questId, phaseId, QuestToastManager.ToastType.BRANCH_CHOICE);
        }
    }

    public String getBranchChoiceQuestId() {
        return QuestToastManager.firstPendingQuestId(QuestToastManager.ToastType.BRANCH_CHOICE);
    }

    public void showPhaseUpdateToast(String phaseName, int themeColor, PhaseUpdateToast.Kind kind) {
        if (phaseName != null && !phaseName.isBlank()) {
            showPhaseUpdateToast(Component.literal(phaseName), themeColor, kind);
        }
    }

    public void showPhaseUpdateToast(Component phaseName, int themeColor, PhaseUpdateToast.Kind kind) {
        if (phaseName == null || phaseName.getString().isBlank()) return;
        QuestToastManager.ToastType type = switch (kind == null ? PhaseUpdateToast.Kind.ADDED : kind) {
            case ADDED -> QuestToastManager.ToastType.PHASE_ADDED;
            case SWITCHED -> QuestToastManager.ToastType.PHASE_SWITCHED;
            case COMPLETED -> QuestToastManager.ToastType.PHASE_COMPLETED;
            case PENDING_CONFIRM -> QuestToastManager.ToastType.PHASE_PENDING_CONFIRM;
        };
        String questId = getTrackedQuestId();
        String phaseId = getTrackedPhaseId();
        QuestToastManager.show(type, questId == null ? "arc_quest:legacy" : questId,
                phaseId == null ? "legacy:" + phaseName.getString() : phaseId,
                phaseName, questId == null ? Component.empty()
                        : ClientQuestCache.INSTANCE.getQuestDisplayComponent(questId));
    }

    public void refreshToastConfiguration() {
        QuestToastManager.refreshConfiguration();
    }
}
