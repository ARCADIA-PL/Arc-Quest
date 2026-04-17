package org.com.arc_quest.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 共享动画工具集，复用 GenesisSkinScreen / SkinSplashRenderer 的动画范式。
 *
 * <p>所有方法均为纯函数 / 静态工具，无状态。
 */
public final class QuestAnimUtil {

    private QuestAnimUtil() {
    }

    // ═══════════════════════════════════════════════════════
    //  插值
    // ═══════════════════════════════════════════════════════

    /**
     * 帧率无关的指数平滑 lerp。
     *
     * @param current      当前值
     * @param target       目标值
     * @param speedAt60Fps 在 60fps 时每帧的追赶比率 (0~1)
     * @param dt           帧间隔（秒）
     */
    public static float lerp(float current, float target, float speedAt60Fps, float dt) {
        float factor = 1.0f - (float) Math.pow(1.0 - speedAt60Fps, dt * 60.0f);
        return current + (target - current) * factor;
    }

    /**
     * 线性步进（固定速度）。
     */
    public static float step(float current, float target, float speedPerSecond, float dt) {
        float amount = speedPerSecond * dt;
        if (current < target) return Math.min(current + amount, target);
        if (current > target) return Math.max(current - amount, target);
        return current;
    }

    // ═══════════════════════════════════════════════════════
    //  缓动函数
    // ═══════════════════════════════════════════════════════

    /**
     * Ease-out cubic: 快→慢
     */
    public static float easeOutCubic(float t) {
        return 1.0f - (float) Math.pow(1.0 - t, 3);
    }

    /**
     * Ease-in cubic: 慢→快
     */
    public static float easeInCubic(float t) {
        return t * t * t;
    }

    /**
     * Ease-in quartic
     */
    public static float easeInQuartic(float t) {
        return t * t * t * t;
    }

    /**
     * Ease-out quintic
     */
    public static float easeOutQuintic(float t) {
        return 1.0f - (float) Math.pow(1.0 - t, 5);
    }

    /**
     * Ease-in sextic（用于退场alpha）
     */
    public static float easeInSextic(float t) {
        return (float) Math.pow(t, 6);
    }

    /**
     * Smooth-step (Hermite): S 形曲线
     */
    public static float smoothStep(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    /**
     * Ease-out-back: 带轻微回弹
     */
    public static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return 1.0f + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    // ═══════════════════════════════════════════════════════
    //  颜色工具
    // ═══════════════════════════════════════════════════════

    /**
     * 将 RGB 整数与 alpha (0~255) 合并为 ARGB 整数。
     */
    public static int withAlpha(int rgb, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    /**
     * 在两个颜色之间插值。
     */
    public static int lerpColor(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ═══════════════════════════════════════════════════════
    //  绘制工具
    // ═══════════════════════════════════════════════════════

    /**
     * 绘制带边框的矩形框（仿 GenesisSkinScreen 风格）。
     */
    public static void drawFrame(GuiGraphics g, int x, int y, int w, int h,
                                 int bgColor, int borderColor) {
        int r = x + w, b = y + h;
        g.fill(x, y, r, b, bgColor);
        g.fill(x - 1, y - 1, r + 1, y, borderColor);     // top
        g.fill(x - 1, b, r + 1, b + 1, borderColor);     // bottom
        g.fill(x - 1, y, x, b, borderColor);              // left
        g.fill(r, y, r + 1, b, borderColor);               // right
    }

    /**
     * 绘制带左侧主题色条的面板（仿 SkinSplashRenderer 风格）。
     */
    public static void drawAccentPanel(GuiGraphics g, int x, int y, int w, int h,
                                       int bgColor, int accentColor, int accentWidth) {
        int r = x + w, bottom = y + h;
        g.fill(x, y, r, bottom, bgColor);
        g.fill(x, y, x + accentWidth, bottom, accentColor);
    }

    /**
     * 绘制进度条。
     */
    public static void drawProgressBar(GuiGraphics g, int x, int y, int w, int h,
                                       float progress, int bgColor, int fillColor) {
        g.fill(x, y, x + w, y + h, bgColor);
        int fillW = (int) (w * Math.max(0f, Math.min(1f, progress)));
        if (fillW > 0) {
            g.fill(x, y, x + fillW, y + h, fillColor);
        }
    }

    /**
     * 绘制进度条（带发光边缘）。
     */
    public static void drawProgressBarGlow(GuiGraphics g, int x, int y, int w, int h,
                                           float progress, int bgColor, int fillColor, int glowColor) {
        drawProgressBar(g, x, y, w, h, progress, bgColor, fillColor);
        int fillW = (int) (w * Math.max(0f, Math.min(1f, progress)));
        if (fillW > 2) {
            // 末端发光像素
            g.fill(x + fillW - 2, y, x + fillW, y + h, glowColor);
        }
    }
}