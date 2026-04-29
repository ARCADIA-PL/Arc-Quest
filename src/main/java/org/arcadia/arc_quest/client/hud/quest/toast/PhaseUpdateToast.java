package org.arcadia.arc_quest.client.hud.quest.toast;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;

public class PhaseUpdateToast {

    public enum Kind { ADDED, SWITCHED, COMPLETED }

    private static final int POPUP_W = 220;
    private static final int POPUP_H = 36;

    private static final float PHASE_ENTER = 500f;
    private static final float PHASE_HOLD = 2600f;
    private static final float PHASE_EXIT = 400f;
    private static final float PHASE_FLY = 10f;
    private static final float PHASE_DRIFT = 1f;

    private final String phaseName;
    private final int themeColor;
    private final Kind kind;

    private long startTime;
    private long lastRenderTime;

    public PhaseUpdateToast(String phaseName, int themeColor) {
        this(phaseName, themeColor, Kind.ADDED);
    }

    public PhaseUpdateToast(String phaseName, int themeColor, Kind kind) {
        this.phaseName = phaseName;
        this.themeColor = themeColor;
        this.kind = kind == null ? Kind.ADDED : kind;
        this.startTime = Util.getMillis();
        this.lastRenderTime = this.startTime;
    }

    public Kind getKind() { return kind; }

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
        if (elapsed >= totalTime) return false;

        float alpha, textDriftX, revealProgress, wipeProgress = 0f;

        if (elapsed < PHASE_ENTER) {
            float t = elapsed / PHASE_ENTER;
            float ease = HudAnimUtil.easeOutQuintic(t);
            revealProgress = ease;
            alpha = ease * parentAlpha;
            textDriftX = -(1f - ease) * PHASE_FLY;
        } else if (elapsed < PHASE_ENTER + PHASE_HOLD) {
            float t = (elapsed - PHASE_ENTER) / PHASE_HOLD;
            revealProgress = 1f;
            alpha = parentAlpha;
            textDriftX = (float) Math.sin(t * Math.PI) * (PHASE_DRIFT * 0.5f);
        } else {
            float t = (elapsed - PHASE_ENTER - PHASE_HOLD) / PHASE_EXIT;
            float ease = HudAnimUtil.easeInQuartic(t);
            revealProgress = 1f;
            wipeProgress = ease;
            alpha = (1f - (float) Math.pow(t, 6)) * parentAlpha;
            textDriftX = ease * PHASE_FLY;
        }

        if (alpha < 0.01f) return true;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        float uiScale = HudRenderUtil.getUniversalUiScale(screenWidth, screenHeight);
        float sw = screenWidth / uiScale;

        int virtualMarginRight = 16;
        int vBaseX = (int) sw - POPUP_W - virtualMarginRight;
        int vBaseY = (int) (baseY / uiScale);

        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1f);

        int activeTheme = resolveThemeByKind();
        int scLeft = vBaseX - 20;
        int scRight;
        if (elapsed < PHASE_ENTER) {
            scRight = vBaseX + (int) (POPUP_W * revealProgress);
        } else if (elapsed >= PHASE_ENTER + PHASE_HOLD) {
            scRight = vBaseX + (int) (POPUP_W * (1f - wipeProgress));
        } else {
            scRight = vBaseX + POPUP_W + 20;
        }

        g.enableScissor((int)(scLeft * uiScale), (int)((vBaseY - 10) * uiScale), (int)(scRight * uiScale), (int)((vBaseY + POPUP_H + 20) * uiScale));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int bgA = (int) (alpha * 0x66);
        int accentA = (int) (alpha * 255);

        g.fill(vBaseX, vBaseY, vBaseX + POPUP_W, vBaseY + POPUP_H, HudAnimUtil.withAlpha(0x151515, bgA));
        g.fill(vBaseX, vBaseY, vBaseX + POPUP_W, vBaseY + 1, HudAnimUtil.withAlpha(0xFFFFFF, (int)(0x22 * alpha)));
        g.fill(vBaseX, vBaseY + POPUP_H - 1, vBaseX + POPUP_W, vBaseY + POPUP_H, HudAnimUtil.withAlpha(0xFFFFFF, (int)(0x22 * alpha)));

        HudRenderUtil.drawCyberneticEdge(g, vBaseX, vBaseY, POPUP_H, activeTheme, accentA);

        int decorX = vBaseX + POPUP_W - 14;
        g.fill(decorX, vBaseY + 6, decorX + 4, vBaseY + 10, HudAnimUtil.withAlpha(activeTheme, (int)(0xAA * alpha)));
        g.fill(decorX, vBaseY + 12, decorX + 4, vBaseY + 20, HudAnimUtil.withAlpha(0xFFFFFF, (int)(0x33 * alpha)));
        g.fill(decorX, vBaseY + 22, decorX + 4, vBaseY + 30, HudAnimUtil.withAlpha(0xFFFFFF, (int)(0x1A * alpha)));

        float textBaseX = vBaseX + 16 + textDriftX;
        float textBaseY = vBaseY + 6;

        if (accentA > 5) {
            String subtitle = subtitleByKind();
            String title = font.plainSubstrByWidth(phaseName, POPUP_W - 40);
            int subColor = HudAnimUtil.withAlpha(0x888888, accentA);
            int titleColor = HudAnimUtil.withAlpha(0xFFFFFF, accentA);

            HudRenderUtil.drawDualText(g, font, textBaseX, textBaseY, subtitle, title, subColor, titleColor, 1.0f);

            float targetLineWidth = POPUP_W - 40f;
            float lineWidth = targetLineWidth * (elapsed < PHASE_ENTER ? revealProgress : (elapsed >= PHASE_ENTER + PHASE_HOLD ? (1f - wipeProgress) : 1f));

            g.fill((int)textBaseX, (int)textBaseY + 20, (int)(textBaseX + lineWidth), (int)textBaseY + 21, HudAnimUtil.withAlpha(activeTheme, accentA));
            g.fill((int)textBaseX, (int)textBaseY + 20, (int)textBaseX + 1, (int)textBaseY + 24, HudAnimUtil.withAlpha(activeTheme, accentA));
        }

        g.disableScissor();
        RenderSystem.disableBlend();
        g.pose().popPose();
        return true;
    }

    private int resolveThemeByKind() {
        return switch (kind) {
            case COMPLETED -> 0x66FF66;
            case SWITCHED -> 0x8CD8FF;
            case ADDED -> (themeColor & 0x00FFFFFF);
        };
    }

    private String subtitleByKind() {
        return switch (kind) {
            case ADDED -> "[// PARALLEL LANE INITIATED ]";
            case SWITCHED -> "[// FOCUS SHIFTED ]";
            case COMPLETED -> "[// LANE SECURED ]";
        };
    }
}