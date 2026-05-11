package org.arcadia.arc_quest.client.hud.quest.toast;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

public class BranchChoiceToast {

    public static final int POPUP_W = 220;
    public static final int POPUP_H = 36;
    private static final int COLOR_ACCENT = 0xFFFFCC44;

    private static final float TIME_ENTER = 600f;
    private static final float TIME_EXIT = 400f;
    private static final float FLY_DIST = 10f;

    private final String questId;
    private final String phaseId;

    private long startTime;
    private boolean isDismissing = false;
    private long dismissStartTime = 0;
    private long lastRenderTime = 0;

    public BranchChoiceToast(String questId) {
        this(questId, null);
    }

    public BranchChoiceToast(String questId, String phaseId) {
        this.questId = questId;
        this.phaseId = phaseId;
        startTime = Util.getMillis();
        lastRenderTime = startTime;
    }

    public void dismiss() {
        if (!isDismissing) {
            isDismissing = true;
            dismissStartTime = Util.getMillis();
        }
    }

    public boolean isExpired() {
        if (!isDismissing) return false;
        return Util.getMillis() - dismissStartTime >= TIME_EXIT;
    }

    public String getQuestId() {
        return questId;
    }

    public String getPhaseId() {
        return phaseId;
    }

    public boolean sameTarget(String otherQuestId, String otherPhaseId) {
        if (otherQuestId == null) return false;
        if (!otherQuestId.equals(questId)) return false;
        if (phaseId == null) return otherPhaseId == null || otherPhaseId.isEmpty();
        return phaseId.equals(otherPhaseId);
    }

    public boolean render(GuiGraphics g, int baseX, int baseY, float parentAlpha, float partialTick, boolean isFrozen) {
        long now = Util.getMillis();

        if (isFrozen) {
            long dt = now - lastRenderTime;
            startTime += dt;
            if (isDismissing) dismissStartTime += dt;
            lastRenderTime = now;
            return true;
        }
        lastRenderTime = now;

        long elapsedEnter = now - startTime;
        float alpha = 1f, textDriftX = 0f, revealProgress = 1f, wipeProgress = 0f;
        float lineWidth = POPUP_W - 20f;

        if (!isDismissing && elapsedEnter < TIME_ENTER) {
            float t = elapsedEnter / TIME_ENTER;
            float ease = HudAnimUtil.easeOutQuintic(t);
            alpha = ease;
            revealProgress = ease;
            textDriftX = -(1f - ease) * FLY_DIST;
            lineWidth *= ease;
        } else if (!isDismissing) {
            textDriftX = (float) Math.sin(elapsedEnter / 400.0) * 0.5f;
        } else {
            long elapsedExit = now - dismissStartTime;
            if (elapsedExit >= TIME_EXIT) return false;
            float t = elapsedExit / TIME_EXIT;
            float ease = HudAnimUtil.easeInQuartic(t);
            wipeProgress = ease;
            alpha = 1f - (float) Math.pow(t, 6);
            textDriftX = ease * FLY_DIST;
            lineWidth *= (1f - ease);
        }

        float finalAlpha = alpha * parentAlpha;
        if (finalAlpha < 0.01f) return true;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 直接使用父组件传来的 baseX 和 baseY！不要再自己瞎算了！
        int scLeft = baseX - 20;
        int scRight = baseX + POPUP_W + 20;
        if (!isDismissing && elapsedEnter < TIME_ENTER) {
            scRight = baseX + (int) (POPUP_W * revealProgress);
        } else if (isDismissing) {
            scRight = baseX + (int) (POPUP_W * (1f - wipeProgress));
        }

        // 仅在裁剪器（Scissor）计算时获取全局 uiScale（因为 Scissor 必须用物理像素/绝对 gui 缩放）
        Minecraft mc = Minecraft.getInstance();
        float uiScale = HudRenderUtil.getUniversalUiScale(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        g.enableScissor((int) (scLeft * uiScale), (int) ((baseY - 10) * uiScale), (int) (scRight * uiScale), (int) ((baseY + POPUP_H + 20) * uiScale));

        int bgA = (int) (finalAlpha * 0x88);
        int accentA = (int) (finalAlpha * 255);
        int themeColor = COLOR_ACCENT & 0xFFFFFF;

        // 直接用 baseX, baseY 渲染
        HudRenderUtil.drawGlassPanel(g, baseX, baseY, POPUP_W, POPUP_H, 0x121212, bgA, themeColor, accentA, 4);

        String questName = ClientQuestCache.INSTANCE.getQuestDisplayName(questId);
        String phaseName = resolvePhaseDisplayName();

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

            int subColor = HudAnimUtil.withAlpha(0xAAAAAA, accentA);
            int titleColor = HudAnimUtil.withAlpha(0xFFFFFF, accentA);

            HudRenderUtil.drawDualText(g, font, contentX, contentY, subtitle, line1, subColor, titleColor, 1.0f);

            if (line2 != null) {
                // 这个 push/pop 仅用于缩放副标题，是合法的局部变换
                g.pose().pushPose();
                g.pose().translate(contentX, contentY + 19, 0);
                g.pose().scale(0.80f, 0.80f, 1f);
                g.drawString(font, line2, 0, 0, HudAnimUtil.withAlpha(0xE8DFAF, accentA), false);
                g.pose().popPose();
            }

            HudRenderUtil.drawTextWithLine(
                    g, font, contentX, contentY + 10,
                    "",
                    HudAnimUtil.withAlpha(themeColor, accentA),
                    lineWidth, 12
            );
        }

        g.disableScissor();
        RenderSystem.disableBlend();
        return true;
    }

    private String resolvePhaseDisplayName() {
        if (phaseId == null || phaseId.isEmpty()) return null;
        return ClientQuestCache.INSTANCE.getPhaseDisplayName(questId, phaseId);
    }
}