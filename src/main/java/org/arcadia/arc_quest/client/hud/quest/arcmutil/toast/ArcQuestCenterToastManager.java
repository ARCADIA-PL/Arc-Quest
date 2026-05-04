package org.arcadia.arc_quest.client.hud.quest.arcmutil.toast;

import net.minecraft.Util;

public final class ArcQuestCenterToastManager {
    private static ArcQuestPhaseToastViewModel phaseToast;
    private static ArcQuestBranchToastViewModel branchToast;

    private ArcQuestCenterToastManager() {
    }

    public static void showPhase(String phaseName, int themeColor, ArcQuestPhaseToastViewModel.Kind kind) {
        long now = Util.getMillis();
        phaseToast = new ArcQuestPhaseToastViewModel(phaseName, themeColor, kind, now);
    }

    public static void showBranchChoice(String questId, String phaseId) {
        if (branchToast != null && branchToast.sameTarget(questId, phaseId)) return;
        branchToast = new ArcQuestBranchToastViewModel(questId, phaseId, Util.getMillis());
    }

    public static void clearBranchChoice() {
        if (branchToast != null) branchToast.dismiss(Util.getMillis());
    }

    public static void clearBranchChoice(String questId, String phaseId) {
        if (branchToast != null && branchToast.sameTarget(questId, phaseId)) branchToast.dismiss(Util.getMillis());
    }

    public static void tick(boolean frozen) {
        long now = Util.getMillis();
        if (phaseToast != null) {
            phaseToast.tick(now, frozen);
            if (phaseToast.isExpired(now)) phaseToast = null;
        }
        if (branchToast != null) {
            branchToast.tick(now, frozen);
            if (branchToast.isExpired(now)) branchToast = null;
        }
    }

    public static ArcQuestPhaseToastViewModel phaseToast() {
        return phaseToast;
    }

    public static ArcQuestBranchToastViewModel branchToast() {
        return branchToast;
    }

    public static int overlayPressure() {
        int gap = 8;
        return (phaseToast != null ? ArcQuestCenterToastElement.POPUP_H + gap : 0)
                + (branchToast != null ? ArcQuestCenterToastElement.POPUP_H + gap : 0);
    }
}
