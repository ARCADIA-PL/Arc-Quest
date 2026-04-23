package org.com.arc_quest.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 共享动画工具集。
 */
public final class HudAnimUtil {

    private HudAnimUtil() {
    }

    public static float lerp(float current, float target, float speedAt60Fps, float dt) {
        float factor = 1.0f - (float) Math.pow(1.0 - speedAt60Fps, dt * 60.0f);
        return current + (target - current) * factor;
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float step(float current, float target, float speedPerSecond, float dt) {
        float amount = speedPerSecond * dt;
        if (current < target) return Math.min(current + amount, target);
        if (current > target) return Math.max(current - amount, target);
        return current;
    }

    public static float easeOutCubic(float t) {
        return 1.0f - (float) Math.pow(1.0 - t, 3);
    }


    public static float easeInCubic(float t) {
        return t * t * t;
    }


    public static float easeInQuartic(float t) {
        return t * t * t * t;
    }


    public static float easeOutQuintic(float t) {
        return 1.0f - (float) Math.pow(1.0 - t, 5);
    }


    public static float easeInSextic(float t) {
        return (float) Math.pow(t, 6);
    }


    public static float smoothStep(float t) {
        return t * t * (3.0f - 2.0f * t);
    }


    public static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return 1.0f + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    public static int withAlpha(int rgb, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    public static float easeOutElastic(float t) {
        float c4 = (2f * (float)Math.PI) / 3f;
        return t == 0 ? 0 : t == 1 ? 1 : (float)Math.pow(2, -10 * t) * (float)Math.sin((t * 10f - 0.75f) * c4) + 1f;
    }

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

    public static int blend(int c1, int c2, float ratio) {
        ratio = Math.max(0f, Math.min(1f, ratio));
        int a = (c1 >> 24) & 0xFF; // 锁定基础透明度

        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;

        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void drawFrame(GuiGraphics g, int x, int y, int w, int h,
                                 int bgColor, int borderColor) {
        int r = x + w, b = y + h;
        g.fill(x, y, r, b, bgColor);
        g.fill(x - 1, y - 1, r + 1, y, borderColor);     // top
        g.fill(x - 1, b, r + 1, b + 1, borderColor);     // bottom
        g.fill(x - 1, y, x, b, borderColor);              // left
        g.fill(r, y, r + 1, b, borderColor);               // right
    }


    public static void drawAccentPanel(GuiGraphics g, int x, int y, int w, int h,
                                       int bgColor, int accentColor, int accentWidth) {
        int r = x + w, bottom = y + h;
        g.fill(x, y, r, bottom, bgColor);
        
        // 【统一】使用机能风高级晶体侧边栏
        HudRenderUtil.drawCyberneticEdge(g, x, y, h, accentColor, 255);
    }


    public static void drawProgressBar(GuiGraphics g, int x, int y, int w, int h,
                                       float progress, int bgColor, int fillColor) {
        g.fill(x, y, x + w, y + h, bgColor);
        int fillW = (int) (w * Math.max(0f, Math.min(1f, progress)));
        if (fillW > 0) {
            g.fill(x, y, x + fillW, y + h, fillColor);
        }
    }


    public static void drawProgressBarGlow(GuiGraphics g, int x, int y, int w, int h,
                                           float progress, int bgColor, int fillColor, int glowColor) {
        // 使用批量绘制（最多3个矩形：背景+填充+发光）
        int fillW = (int) (w * Math.max(0f, Math.min(1f, progress)));
        int rectCount = 1 + (fillW > 0 ? 1 : 0) + (fillW > 2 ? 1 : 0);
        int[] rects = new int[rectCount * 5];

        int idx = 0;
        // 背景
        rects[idx++] = x;
        rects[idx++] = y;
        rects[idx++] = w;
        rects[idx++] = h;
        rects[idx++] = bgColor;

        // 填充
        if (fillW > 0) {
            rects[idx++] = x;
            rects[idx++] = y;
            rects[idx++] = fillW;
            rects[idx++] = h;
            rects[idx++] = fillColor;
        }

        // 发光边缘
        if (fillW > 2) {
            rects[idx++] = x + fillW - 2;
            rects[idx++] = y;
            rects[idx++] = 2;
            rects[idx++] = h;
            rects[idx++] = glowColor;
        }

        HudRenderUtil.drawBatchRects(g, rects, rectCount);
    }
}