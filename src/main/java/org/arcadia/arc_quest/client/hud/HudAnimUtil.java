package org.arcadia.arc_quest.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 共享动画工具集 (极致性能优化版)
 */
public final class HudAnimUtil {

    private HudAnimUtil() {
    }

    public static float lerp(float current, float target, float speedAt60Fps, float dt) {
        // 使用单精度浮点运算，避免底层 double 类型转换。
        // Math.exp 逼近方式在部分 JVM 上比 Math.pow 更快
        float factor = 1.0f - (float) Math.exp(-speedAt60Fps * dt * 60.0f);
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

    // 【优化】全部将昂贵的 Math.pow() 替换为极速的多项式乘法
    public static float easeOutCubic(float t) {
        float inv = 1.0f - t;
        return 1.0f - (inv * inv * inv);
    }

    public static float easeInCubic(float t) {
        return t * t * t;
    }

    public static float easeInQuartic(float t) {
        return t * t * t * t;
    }

    public static float easeOutQuintic(float t) {
        float inv = 1.0f - t;
        float inv2 = inv * inv;
        return 1.0f - (inv2 * inv2 * inv);
    }

    public static float easeInSextic(float t) {
        float t3 = t * t * t;
        return t3 * t3; // t^6
    }

    public static float smoothStep(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    public static float easeOutBack(float t) {
        // 【优化】合并常量，提取公因式，完全消灭 pow
        float f = t - 1.0f;
        return 1.0f + f * f * (2.70158f * f + 1.70158f);
    }

    public static int withAlpha(int rgb, int alpha) {
        // 位运算代替 Math.min/max (若确保非负可以直接取小)
        alpha = alpha < 0 ? 0 : (alpha > 255 ? 255 : alpha);
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    public static float easeOutElastic(float t) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        float c4 = (2f * 3.14159265f) / 3f;
        // 这里保留了复杂的数学函数，因为它的非线性特性难以用低阶多项式逼近
        return (float) Math.pow(2, -10 * t) * (float) Math.sin((t * 10f - 0.75f) * c4) + 1f;
    }

    public static int lerpColor(int c1, int c2, float t) {
        t = t < 0f ? 0f : (t > 1f ? 1f : t);

        // 【优化】避免创建大量临时变量，使用纯整数位移计算，极大减少算术开销
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;

        int a = a1 + (int) ((a2 - a1) * t);
        int r = r1 + (int) ((r2 - r1) * t);
        int g = g1 + (int) ((g2 - g1) * t);
        int b = b1 + (int) ((b2 - b1) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int blend(int c1, int c2, float ratio) {
        ratio = ratio < 0f ? 0f : (ratio > 1f ? 1f : ratio);
        int a = (c1 >> 24) & 0xFF;

        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;

        int r = r1 + (int) ((r2 - r1) * ratio);
        int g = g1 + (int) ((g2 - g1) * ratio);
        int b = b1 + (int) ((b2 - b1) * ratio);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void drawFrame(GuiGraphics g, int x, int y, int w, int h, int bgColor, int borderColor) {
        int r = x + w, b = y + h;

        int[] rects = new int[25];
        rects[0] = x; rects[1] = y; rects[2] = w; rects[3] = h; rects[4] = bgColor;
        rects[5] = x - 1; rects[6] = y - 1; rects[7] = w + 2; rects[8] = 1; rects[9] = borderColor;
        rects[10] = x - 1; rects[11] = b; rects[12] = w + 2; rects[13] = 1; rects[14] = borderColor;
        rects[15] = x - 1; rects[16] = y; rects[17] = 1; rects[18] = h; rects[19] = borderColor;
        rects[20] = r; rects[21] = y; rects[22] = 1; rects[23] = h; rects[24] = borderColor;

        HudRenderUtil.drawBatchRects(g, rects, 5);
    }

    public static void drawAccentPanel(GuiGraphics g, int x, int y, int w, int h, int bgColor, int accentColor, int accentWidth) {
        g.fill(x, y, x + w, y + h, bgColor);
        HudRenderUtil.drawCyberneticEdge(g, x, y, h, accentColor, 255);
    }

    public static void drawProgressBar(GuiGraphics g, int x, int y, int w, int h, float progress, int bgColor, int fillColor) {
        int fillW = (int) (w * (progress < 0 ? 0 : (progress > 1 ? 1 : progress)));
        if (fillW > 0) {
            int[] rects = new int[10];
            rects[0] = x; rects[1] = y; rects[2] = w; rects[3] = h; rects[4] = bgColor;
            rects[5] = x; rects[6] = y; rects[7] = fillW; rects[8] = h; rects[9] = fillColor;
            HudRenderUtil.drawBatchRects(g, rects, 2);
        } else {
            g.fill(x, y, x + w, y + h, bgColor);
        }
    }

    public static void drawProgressBarGlow(GuiGraphics g, int x, int y, int w, int h, float progress, int bgColor, int fillColor, int glowColor) {
        int fillW = (int) (w * (progress < 0 ? 0 : (progress > 1 ? 1 : progress)));
        int rectCount = 1 + (fillW > 0 ? 2 : 0);
        int[] rects = new int[rectCount * 5];

        int idx = 0;
        rects[idx++] = x; rects[idx++] = y; rects[idx++] = w; rects[idx++] = h; rects[idx++] = bgColor;

        if (fillW > 0) {
            rects[idx++] = x; rects[idx++] = y; rects[idx++] = fillW; rects[idx++] = h; rects[idx++] = fillColor;
            rects[idx++] = x + fillW - 2; rects[idx++] = y - 1; rects[idx++] = 2; rects[idx++] = h + 2; rects[idx++] = glowColor;
        }

        RenderSystem.enableBlend();
        HudRenderUtil.drawBatchRects(g, rects, rectCount);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}