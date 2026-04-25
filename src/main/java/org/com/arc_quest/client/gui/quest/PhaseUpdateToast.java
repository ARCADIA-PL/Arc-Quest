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
 * 视觉效果全面升级为【机能风并行模块插槽】样式。
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

        int bgA = (int) (alpha * 0x66);
        int accentA = (int) (alpha * 255);

        // ==========================================
        // Toast 机能风背景重构
        // ==========================================
        // 底板
        g.fill(baseX, baseY, baseX + POPUP_W, baseY + POPUP_H, HudAnimUtil.withAlpha(0x151515, bgA));
        // 上下发光细线
        g.fill(baseX, baseY, baseX + POPUP_W, baseY + 1, HudAnimUtil.withAlpha(0xFFFFFF, (int)(0x22 * alpha)));
        g.fill(baseX, baseY + POPUP_H - 1, baseX + POPUP_W, baseY + POPUP_H, HudAnimUtil.withAlpha(0xFFFFFF, (int)(0x22 * alpha)));

        // 侧边指示光刃
        HudRenderUtil.drawCyberneticEdge(g, baseX, baseY, POPUP_H, activeTheme, accentA);

        // 右侧装饰性机能纹理阵列
        int decorX = baseX + POPUP_W - 14;
        g.fill(decorX, baseY + 6, decorX + 4, baseY + 10, HudAnimUtil.withAlpha(activeTheme, (int)(0xAA * alpha)));
        g.fill(decorX, baseY + 12, decorX + 4, baseY + 20, HudAnimUtil.withAlpha(0xFFFFFF, (int)(0x33 * alpha)));
        g.fill(decorX, baseY + 22, decorX + 4, baseY + 30, HudAnimUtil.withAlpha(0xFFFFFF, (int)(0x1A * alpha)));
        // ==========================================

        float textBaseX = baseX + 16 + textDriftX; // 稍微往右移一点避开光刃
        float textBaseY = baseY + 6;

        if (accentA > 5) {
            String subtitle = subtitleByKind();
            String title = font.plainSubstrByWidth(phaseName, POPUP_W - 40);

            int subColor = HudAnimUtil.withAlpha(0x888888, accentA);
            int titleColor = HudAnimUtil.withAlpha(0xFFFFFF, accentA);

            // 绘制文字
            HudRenderUtil.drawDualText(g, font, textBaseX, textBaseY, subtitle, title, subColor, titleColor, 1.0f);

            // 绘制下方的细线框体分隔
            float targetLineWidth = POPUP_W - 40f;
            float lineWidth = targetLineWidth * (elapsed < PHASE_ENTER
                    ? revealProgress
                    : (elapsed >= PHASE_ENTER + PHASE_HOLD ? (1f - wipeProgress) : 1f));

            g.fill((int)textBaseX, (int)textBaseY + 20, (int)(textBaseX + lineWidth), (int)textBaseY + 21, HudAnimUtil.withAlpha(activeTheme, accentA));
            g.fill((int)textBaseX, (int)textBaseY + 20, (int)textBaseX + 1, (int)textBaseY + 24, HudAnimUtil.withAlpha(activeTheme, accentA));
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
        // 更具系统沉浸感的终端指令式副标题
        return switch (kind) {
            case ADDED -> "[// PARALLEL LANE INITIATED ]";
            case SWITCHED -> "[// FOCUS SHIFTED ]";
            case COMPLETED -> "[// LANE SECURED ]";
        };
    }
}