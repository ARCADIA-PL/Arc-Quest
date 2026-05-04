package org.arcadia.arc_quest.client.hud.quest.arcmutil.toast;

import net.minecraft.client.Minecraft;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.mutil.core.ArcGuiTickContext;
import org.arcadia.arc_quest.mutil.overlay.ArcOverlayRoot;

public class ArcQuestCenterToastOverlayRoot extends ArcOverlayRoot {
    private static final int LEFT_BASE_X = 20;

    private final ArcQuestCenterToastElement phaseElement = new ArcQuestCenterToastElement();
    private final ArcQuestCenterToastElement branchElement = new ArcQuestCenterToastElement();
    private float currentPhaseY = -1f;
    private float currentBranchY = -1f;

    public ArcQuestCenterToastOverlayRoot(Minecraft minecraft) {
        super(minecraft, "arc_quest_center_toasts");
        addChild(phaseElement);
        addChild(branchElement);
    }

    @Override
    protected void tick(ArcGuiTickContext context, int refX, int refY) {
        int centerY = context.screenHeight() / 2;
        int gap = 8;
        boolean phaseActive = ArcQuestCenterToastManager.phaseToast() != null;
        boolean branchActive = ArcQuestCenterToastManager.branchToast() != null;
        float targetPhaseY = centerY - ArcQuestCenterToastElement.POPUP_H / 2f;
        float targetBranchY = centerY - ArcQuestCenterToastElement.POPUP_H / 2f;
        if (phaseActive && branchActive) {
            float totalH = ArcQuestCenterToastElement.POPUP_H * 2 + gap;
            targetPhaseY = centerY - totalH / 2f;
            targetBranchY = targetPhaseY + ArcQuestCenterToastElement.POPUP_H + gap;
        } else if (phaseActive) {
            targetBranchY = targetPhaseY;
        } else if (branchActive) {
            targetPhaseY = targetBranchY;
        }
        if (currentPhaseY < 0 || !phaseActive) currentPhaseY = targetPhaseY;
        if (currentBranchY < 0 || !branchActive) currentBranchY = targetBranchY;
        if (phaseActive || branchActive) {
            currentPhaseY = HudAnimUtil.lerp(currentPhaseY, targetPhaseY, 0.15f, context.deltaTime());
            currentBranchY = HudAnimUtil.lerp(currentBranchY, targetBranchY, 0.15f, context.deltaTime());
        }
        phaseElement.applyPhase(ArcQuestCenterToastManager.phaseToast(), LEFT_BASE_X, Math.round(currentPhaseY), false);
        branchElement.applyBranch(ArcQuestCenterToastManager.branchToast(), LEFT_BASE_X, Math.round(currentBranchY), false);
        setVisible(phaseActive || branchActive);
    }
}
