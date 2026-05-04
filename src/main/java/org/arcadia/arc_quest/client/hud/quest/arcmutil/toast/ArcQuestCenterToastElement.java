package org.arcadia.arc_quest.client.hud.quest.arcmutil.toast;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.mutil.animation.ArcAnimClock;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;
import org.arcadia.arc_quest.mutil.screen.ArcScaleResolver;
import org.arcadia.arc_quest.mutil.theme.ArcPanelChrome;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

public class ArcQuestCenterToastElement extends ArcGuiElement {
    public static final int POPUP_W = 220;
    public static final int POPUP_H = 36;

    private ArcQuestPhaseToastViewModel phaseToast;
    private ArcQuestBranchToastViewModel branchToast;
    private int baseX;
    private int baseY;
    private boolean frozen;

    public ArcQuestCenterToastElement() {
        super(0, 0, POPUP_W, POPUP_H);
        setVisible(false);
    }

    public void applyPhase(ArcQuestPhaseToastViewModel toast, int baseX, int baseY, boolean frozen) {
        this.phaseToast = toast;
        this.branchToast = null;
        this.baseX = baseX;
        this.baseY = baseY;
        this.frozen = frozen;
        setVisible(toast != null);
    }

    public void applyBranch(ArcQuestBranchToastViewModel toast, int baseX, int baseY, boolean frozen) {
        this.phaseToast = null;
        this.branchToast = toast;
        this.baseX = baseX;
        this.baseY = baseY;
        this.frozen = frozen;
        setVisible(toast != null);
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!visible) return;
        if (phaseToast != null) drawPhase(graphics, inheritedOpacity);
        else if (branchToast != null) drawBranch(graphics, inheritedOpacity);
    }

    private void drawPhase(GuiGraphics g, float inheritedOpacity) {
        long elapsed = phaseToast == null ? 0 : Util.getMillis() - phaseToast.startTime;
        float totalTime = ArcQuestPhaseToastViewModel.PHASE_ENTER + ArcQuestPhaseToastViewModel.PHASE_HOLD + ArcQuestPhaseToastViewModel.PHASE_EXIT;
        if (elapsed >= totalTime) return;

        float alpha;
        float textDriftX;
        float revealProgress;
        float wipeProgress = 0f;

        if (elapsed < ArcQuestPhaseToastViewModel.PHASE_ENTER) {
            float t = elapsed / ArcQuestPhaseToastViewModel.PHASE_ENTER;
            float ease = ArcAnimClock.easeOutQuintic(t);
            revealProgress = ease;
            alpha = ease * inheritedOpacity;
            textDriftX = -(1f - ease) * ArcQuestPhaseToastViewModel.PHASE_FLY;
        } else if (elapsed < ArcQuestPhaseToastViewModel.PHASE_ENTER + ArcQuestPhaseToastViewModel.PHASE_HOLD) {
            float t = (elapsed - ArcQuestPhaseToastViewModel.PHASE_ENTER) / ArcQuestPhaseToastViewModel.PHASE_HOLD;
            revealProgress = 1f;
            alpha = inheritedOpacity;
            textDriftX = (float) Math.sin(t * Math.PI) * (ArcQuestPhaseToastViewModel.PHASE_DRIFT * 0.5f);
        } else {
            float t = (elapsed - ArcQuestPhaseToastViewModel.PHASE_ENTER - ArcQuestPhaseToastViewModel.PHASE_HOLD) / ArcQuestPhaseToastViewModel.PHASE_EXIT;
            float ease = ArcAnimClock.easeInQuartic(t);
            revealProgress = 1f;
            wipeProgress = ease;
            alpha = (1f - (float) Math.pow(t, 6)) * inheritedOpacity;
            textDriftX = ease * ArcQuestPhaseToastViewModel.PHASE_FLY;
        }
        if (alpha < 0.01f) return;

        int activeTheme = resolvePhaseTheme();
        int scLeft = baseX - 20;
        int scRight;
        if (elapsed < ArcQuestPhaseToastViewModel.PHASE_ENTER) scRight = baseX + (int) (POPUP_W * revealProgress);
        else if (elapsed >= ArcQuestPhaseToastViewModel.PHASE_ENTER + ArcQuestPhaseToastViewModel.PHASE_HOLD) scRight = baseX + (int) (POPUP_W * (1f - wipeProgress));
        else scRight = baseX + POPUP_W + 20;

        Minecraft mc = Minecraft.getInstance();
        float uiScale = ArcScaleResolver.resolveUniversalUiScale(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        g.enableScissor((int) (scLeft * uiScale), (int) ((baseY - 10) * uiScale), (int) (scRight * uiScale), (int) ((baseY + POPUP_H + 20) * uiScale));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int bgA = (int) (alpha * 0x66);
        int accentA = (int) (alpha * 255);
        g.fill(baseX, baseY, baseX + POPUP_W, baseY + POPUP_H, ArcDrawUtil.withAlpha(0x151515, bgA));
        g.fill(baseX, baseY, baseX + POPUP_W, baseY + 1, ArcDrawUtil.withAlpha(0xFFFFFF, (int) (0x22 * alpha)));
        g.fill(baseX, baseY + POPUP_H - 1, baseX + POPUP_W, baseY + POPUP_H, ArcDrawUtil.withAlpha(0xFFFFFF, (int) (0x22 * alpha)));
        ArcDrawUtil.drawCyberneticEdge(g, baseX, baseY, POPUP_H, activeTheme, accentA, 3);

        int decorX = baseX + POPUP_W - 14;
        g.fill(decorX, baseY + 6, decorX + 4, baseY + 10, ArcDrawUtil.withAlpha(activeTheme, (int) (0xAA * alpha)));
        g.fill(decorX, baseY + 12, decorX + 4, baseY + 20, ArcDrawUtil.withAlpha(0xFFFFFF, (int) (0x33 * alpha)));
        g.fill(decorX, baseY + 22, decorX + 4, baseY + 30, ArcDrawUtil.withAlpha(0xFFFFFF, (int) (0x1A * alpha)));

        float textBaseX = baseX + 16 + textDriftX;
        float textBaseY = baseY + 6;
        if (accentA > 5) {
            Font font = Minecraft.getInstance().font;
            String subtitle = phaseSubtitle();
            String title = font.plainSubstrByWidth(phaseToast.phaseName, POPUP_W - 40);
            ArcDrawUtil.drawDualText(g, font, textBaseX, textBaseY, subtitle, title,
                    ArcDrawUtil.withAlpha(0x888888, accentA), ArcDrawUtil.withAlpha(0xFFFFFF, accentA), 1.0f);
            float targetLineWidth = POPUP_W - 40f;
            float lineWidth = targetLineWidth * (elapsed < ArcQuestPhaseToastViewModel.PHASE_ENTER ? revealProgress : (elapsed >= ArcQuestPhaseToastViewModel.PHASE_ENTER + ArcQuestPhaseToastViewModel.PHASE_HOLD ? (1f - wipeProgress) : 1f));
            g.fill((int) textBaseX, (int) textBaseY + 20, (int) (textBaseX + lineWidth), (int) textBaseY + 21, ArcDrawUtil.withAlpha(activeTheme, accentA));
            g.fill((int) textBaseX, (int) textBaseY + 20, (int) textBaseX + 1, (int) textBaseY + 24, ArcDrawUtil.withAlpha(activeTheme, accentA));
        }

        g.disableScissor();
        RenderSystem.disableBlend();
    }

    private void drawBranch(GuiGraphics g, float inheritedOpacity) {
        long now = Util.getMillis();
        long elapsedEnter = now - branchToast.startTime;
        float alpha = 1f;
        float textDriftX = 0f;
        float revealProgress = 1f;
        float wipeProgress = 0f;
        float lineWidth = POPUP_W - 20f;

        if (!branchToast.dismissing && elapsedEnter < ArcQuestBranchToastViewModel.TIME_ENTER) {
            float t = elapsedEnter / ArcQuestBranchToastViewModel.TIME_ENTER;
            float ease = ArcAnimClock.easeOutQuintic(t);
            alpha = ease;
            revealProgress = ease;
            textDriftX = -(1f - ease) * ArcQuestBranchToastViewModel.FLY_DIST;
            lineWidth *= ease;
        } else if (!branchToast.dismissing) {
            textDriftX = (float) Math.sin(elapsedEnter / 400.0) * 0.5f;
        } else {
            long elapsedExit = now - branchToast.dismissStartTime;
            if (elapsedExit >= ArcQuestBranchToastViewModel.TIME_EXIT) return;
            float t = elapsedExit / ArcQuestBranchToastViewModel.TIME_EXIT;
            float ease = ArcAnimClock.easeInQuartic(t);
            wipeProgress = ease;
            alpha = 1f - (float) Math.pow(t, 6);
            textDriftX = ease * ArcQuestBranchToastViewModel.FLY_DIST;
            lineWidth *= (1f - ease);
        }

        float finalAlpha = alpha * inheritedOpacity;
        if (finalAlpha < 0.01f) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int scLeft = baseX - 20;
        int scRight = baseX + POPUP_W + 20;
        if (!branchToast.dismissing && elapsedEnter < ArcQuestBranchToastViewModel.TIME_ENTER) scRight = baseX + (int) (POPUP_W * revealProgress);
        else if (branchToast.dismissing) scRight = baseX + (int) (POPUP_W * (1f - wipeProgress));

        Minecraft mc = Minecraft.getInstance();
        float uiScale = ArcScaleResolver.resolveUniversalUiScale(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        g.enableScissor((int) (scLeft * uiScale), (int) ((baseY - 10) * uiScale), (int) (scRight * uiScale), (int) ((baseY + POPUP_H + 20) * uiScale));

        int bgA = (int) (finalAlpha * 0x88);
        int accentA = (int) (finalAlpha * 255);
        int themeColor = ArcQuestBranchToastViewModel.COLOR_ACCENT & 0xFFFFFF;
        ArcPanelChrome.drawGlassPanel(g, baseX, baseY, POPUP_W, POPUP_H, 0x121212, bgA, themeColor, accentA, 4);

        String questName = ClientQuestCache.INSTANCE.getQuestDisplayName(branchToast.questId);
        String phaseName = branchToast.phaseId == null || branchToast.phaseId.isEmpty() ? null : ClientQuestCache.INSTANCE.getPhaseDisplayName(branchToast.questId, branchToast.phaseId);
        Font font = Minecraft.getInstance().font;
        float contentX = baseX + 12 + textDriftX;
        float contentY = baseY + 6;

        if (accentA > 5) {
            String subtitle = Component.translatable("arc_quest.toast.branch.subtitle").getString();
            String prefix = Component.translatable("arc_quest.toast.branch.prefix").getString();
            String line1 = prefix + font.plainSubstrByWidth(questName, POPUP_W - 30 - font.width(prefix));
            String line2 = null;
            if (phaseName != null && !phaseName.isEmpty()) {
                String phasePrefix = "• ";
                line2 = phasePrefix + font.plainSubstrByWidth(phaseName, POPUP_W - 30 - font.width(phasePrefix));
            }
            ArcDrawUtil.drawDualText(g, font, contentX, contentY, subtitle, line1,
                    ArcDrawUtil.withAlpha(0xAAAAAA, accentA), ArcDrawUtil.withAlpha(0xFFFFFF, accentA), 1.0f);
            if (line2 != null) {
                g.pose().pushPose();
                g.pose().translate(contentX, contentY + 19, 0);
                g.pose().scale(0.80f, 0.80f, 1f);
                g.drawString(font, line2, 0, 0, ArcDrawUtil.withAlpha(0xE8DFAF, accentA), false);
                g.pose().popPose();
            }
            ArcDrawUtil.drawTextWithLine(g, font, contentX, contentY + 10, "", ArcDrawUtil.withAlpha(themeColor, accentA), lineWidth, 12);
        }

        g.disableScissor();
        RenderSystem.disableBlend();
    }

    private int resolvePhaseTheme() {
        return switch (phaseToast.kind) {
            case COMPLETED -> 0x66FF66;
            case SWITCHED -> 0x8CD8FF;
            case ADDED -> phaseToast.themeColor & 0x00FFFFFF;
        };
    }

    private String phaseSubtitle() {
        return switch (phaseToast.kind) {
            case ADDED -> "[// PARALLEL LANE INITIATED ]";
            case SWITCHED -> "[// FOCUS SHIFTED ]";
            case COMPLETED -> "[// LANE SECURED ]";
        };
    }
}
