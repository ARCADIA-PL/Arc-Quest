package org.com.arc_quest.client.gui.quest;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.client.gui.HudRenderUtil;

/**
 * 阶段推进弹窗（支持并行语义类型）。
 */
public class PhaseUpdateToast {

    public enum Kind {
        ADDED,
        SWITCHED,
        COMPLETED
    }

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

    /**
     * 兼容旧调用：默认 ADDED
     */
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

    public Kind getKind() {
        return kind;
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
        if (elapsed >= totalTime) return false;

        float alpha, textDriftX, revealProgress, wipeProgress = 0f;
        float targetLineWidth = POPUP_W - 20f;

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

        int activeTheme = resolveThemeByKind();
        int scLeft = baseX - 20;
        int scRight;
        if (elapsed < PHASE_ENTER) {
            scRight = baseX + (int) (POPUP_W * revealProgress);
        } else if (elapsed >= PHASE_ENTER + PHASE_HOLD) {
            scRight = baseX + (int) (POPUP_W * (1f - wipeProgress));
        } else {
            scRight = baseX + POPUP_W + 20;
        }

        g.enableScissor(scLeft, baseY - 10, scRight, baseY + POPUP_H + 20);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int bgA = (int) (alpha * 0x88);
        int accentA = (int) (alpha * 255);

        HudRenderUtil.drawGlassPanel(
                g, baseX, baseY, POPUP_W, POPUP_H,
                0x121212, bgA, activeTheme, accentA, 4
        );

        float textBaseX = baseX + 12 + textDriftX;
        float textBaseY = baseY + 6;

        if (accentA > 5) {
            String subtitle = subtitleByKind();
            String title = font.plainSubstrByWidth(phaseName, POPUP_W - 30);

            int subColor = HudAnimUtil.withAlpha(0xAAAAAA, accentA);
            int titleColor = HudAnimUtil.withAlpha(0xFFFFFF, accentA);

            HudRenderUtil.drawDualText(g, font, textBaseX, textBaseY, subtitle, title, subColor, titleColor, 1.0f);

            float lineWidth = targetLineWidth * (elapsed < PHASE_ENTER
                    ? revealProgress
                    : (elapsed >= PHASE_ENTER + PHASE_HOLD ? (1f - wipeProgress) : 1f));

            HudRenderUtil.drawTextWithLine(
                    g, font, textBaseX, textBaseY + 10,
                    "", HudAnimUtil.withAlpha(activeTheme, accentA), lineWidth, 12
            );
        }

        g.disableScissor();
        RenderSystem.disableBlend();
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
            case ADDED -> Component.translatable("arc_quest.hud.new_phase").getString();
            case SWITCHED -> Component.translatable("arc_quest.hud.phase_switched").getString();
            case COMPLETED -> Component.translatable("arc_quest.hud.phase_completed").getString();
        };
    }
}