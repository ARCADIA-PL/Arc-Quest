package org.com.arc_quest.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 独立的阶段推进弹窗实体，掌管自身的生命周期与丝滑渲染。
 */
public class PhaseUpdateToast {
    private static final int POPUP_W = 220;
    private static final int POPUP_H = 36;

    private static final float PHASE_ENTER = 500f;
    private static final float PHASE_HOLD  = 2800f;
    private static final float PHASE_EXIT  = 400f;
    private static final float PHASE_FLY   = 10f;
    private static final float PHASE_DRIFT = 1f;

    private final String phaseName;
    private final int themeColor;
    private long startTime;
    private long lastRenderTime;

    public PhaseUpdateToast(String phaseName, int themeColor) {
        this.phaseName = phaseName;
        this.themeColor = themeColor;
        this.startTime = Util.getMillis();
        this.lastRenderTime = this.startTime;
    }

    public boolean render(GuiGraphics g, Font font, int baseX, int baseY, float parentAlpha, boolean isFrozen) {
        long now = Util.getMillis();
        long dt = now - lastRenderTime;
        this.lastRenderTime = now;

        if (isFrozen) {
            this.startTime += dt;
            return true;
        }

        long elapsed = now - startTime;
        float totalTime = PHASE_ENTER + PHASE_HOLD + PHASE_EXIT;

        if (elapsed >= totalTime) {
            return false; // 动画结束，要求管理器销毁自身
        }

        float alpha, textDriftX, revealProgress, wipeProgress = 0f, accentLineWidth;
        int activeTheme = themeColor & 0x00FFFFFF;
        float targetLineWidth = POPUP_W - 20f;

        if (elapsed < PHASE_ENTER) {
            float t = elapsed / PHASE_ENTER;
            float ease = QuestAnimUtil.easeOutQuintic(t);
            revealProgress   = ease;
            alpha            = ease * parentAlpha;
            textDriftX       = -(1f - ease) * PHASE_FLY;
            accentLineWidth  = ease * targetLineWidth;
        } else if (elapsed < PHASE_ENTER + PHASE_HOLD) {
            float t = (elapsed - PHASE_ENTER) / PHASE_HOLD;
            revealProgress   = 1f;
            alpha            = parentAlpha;
            textDriftX       = (float)Math.sin(t * Math.PI) * (PHASE_DRIFT * 0.5f);
            accentLineWidth  = targetLineWidth;
        } else {
            float t = (elapsed - PHASE_ENTER - PHASE_HOLD) / PHASE_EXIT;
            float ease = QuestAnimUtil.easeInQuartic(t);
            revealProgress   = 1f;
            wipeProgress     = ease;
            alpha            = (1f - (float)Math.pow(t, 6)) * parentAlpha;
            textDriftX       = ease * PHASE_FLY;
            accentLineWidth  = (1f - ease) * targetLineWidth;
        }

        if (alpha < 0.01f) return true;

        int scLeft  = baseX - 20;
        int scRight;
        if (elapsed < PHASE_ENTER) scRight = baseX + (int)(POPUP_W * revealProgress);
        else if (elapsed >= PHASE_ENTER + PHASE_HOLD) scRight = baseX + (int)(POPUP_W * (1f - wipeProgress));
        else scRight = baseX + POPUP_W + 20;

        g.enableScissor(scLeft, baseY - 10, scRight, baseY + POPUP_H + 20);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 使用共用渲染方法绘制磨砂玻璃面板
        int bgA = (int)(alpha * 0x88);
        int accentA = (int)(alpha * 255);
        QuestRenderUtil.drawGlassPanel(g, baseX, baseY, POPUP_W, POPUP_H,
                0x121212, bgA, themeColor & 0x00FFFFFF, accentA, 4);

        float textBaseX = baseX + 12 + textDriftX;
        float textBaseY = baseY + 6;

        if (accentA > 5) {
            String subtitle = Component.translatable("arc_quest.hud.new_phase").getString();
            String title = font.plainSubstrByWidth(phaseName, POPUP_W - 30);
            
            // 使用共用方法绘制双行文本
            int subColor = QuestAnimUtil.withAlpha(0xAAAAAA, accentA);
            int titleColor = QuestAnimUtil.withAlpha(0xFFFFFF, accentA);
            QuestRenderUtil.drawDualText(g, font, textBaseX, textBaseY,
                    subtitle, title, subColor, titleColor, 1.0f);

            // 绘制装饰线
            float lineWidth = targetLineWidth * (elapsed < PHASE_ENTER ? revealProgress : (elapsed >= PHASE_ENTER + PHASE_HOLD ? (1f - wipeProgress) : 1f));
            QuestRenderUtil.drawTextWithLine(g, font, textBaseX, textBaseY + 10,
                    "", QuestAnimUtil.withAlpha(activeTheme, accentA),
                    lineWidth, 12);
        }
        g.disableScissor();
        RenderSystem.disableBlend();
        return true; // 存活
    }
}