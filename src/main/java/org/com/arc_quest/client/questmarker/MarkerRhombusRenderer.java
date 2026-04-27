// file_name: MarkerRhombusRenderer.java
package org.com.arc_quest.client.questmarker;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

public final class MarkerRhombusRenderer {

    private MarkerRhombusRenderer() {}

    public static void draw(GuiGraphics gui, int accentColor, float time, float activeProgress, float lightX, float lightY, float tier) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        Matrix4f matrix = gui.pose().last().pose();

        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        int baseAlpha = (accentColor >> 24) & 0xFF;

        float phase1 = Math.max(0f, Math.min(1f, tier));
        float phase2 = Math.max(0f, Math.min(1f, tier - 1f));

        // ================= 动画核心重构 =================
        // 1. 呼吸动画：保持优雅缓慢
        float breathSpeed = 2.5f;
        float cycle = time * breathSpeed;
        float breathFactor = ((float) Math.sin(cycle) + 1.0f) * 0.5f;
        float breathAmp = 0.10f * activeProgress;
        float breathScale = 1.0f + breathAmp * breathFactor;

        // 2. 流转动画：加快速度，使其像流水一样连续扫过，不与呼吸死绑
        // 使用负数让光芒顺时针流转，视觉上更自然
        float flowSpeed = -4.5f;
        float flowPhase = time * flowSpeed;
        float flowLx = (float)Math.cos(flowPhase);
        float flowLy = (float)Math.sin(flowPhase);

        float highlightMix = 0.2f + 0.8f * activeProgress;
        float mixLx = lightX * (1.0f - highlightMix) + flowLx * highlightMix;
        float mixLy = lightY * (1.0f - highlightMix) + flowLy * highlightMix;
        // =================================================

        float outSize = lerp(lerp(10f, 5f, phase1), 3.5f, phase2);
        float inSize  = lerp(lerp(7f,  3f, phase1), 0.0f, phase2);
        float gap = lerp(lerp(0f, 3.5f, phase1), 0.0f, phase2);

        float finalScaleX = breathScale;
        float finalScaleY = breathScale;

        float lightBlend = 1.0f - phase2;
        float globalAlpha = lerp(lerp(1.0f, 0.8f, phase1), 0.75f, phase2);

        float glassAlphaMod = Math.max(0f, 1.0f - phase1 * 1.5f);
        float rimAlphaMod   = Math.max(0f, 1.0f - phase1 * 2.0f);

        if (rimAlphaMod > 0.05f) {
            int rimA = (int)(baseAlpha * 0.35f * rimAlphaMod);
            int rimColor = (rimA << 24) | 0x888888;
            float rimOut = outSize + 1f;
            float rimIn = outSize;

            addSolid(builder, matrix,  0, -rimOut, gap, -gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix,  0, -rimIn,  gap, -gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix, rimIn,   0,  gap, -gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix, rimOut,  0,  gap, -gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix, rimOut,  0,  gap, gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix, rimIn,   0,  gap, gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix,  0,  rimIn,  gap, gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix,  0, rimOut,  gap, gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix,   0, rimOut, -gap, gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix,   0,  rimIn, -gap, gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix, -rimIn,   0, -gap, gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix, -rimOut,  0, -gap, gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix, -rimOut,  0, -gap, -gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix, -rimIn,   0, -gap, -gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix,   0, -rimIn, -gap, -gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix,   0,-rimOut, -gap, -gap, finalScaleX, finalScaleY, rimColor);
        }

        if (glassAlphaMod > 0.05f && inSize > 0) {
            addDyn(builder, matrix,  0, -inSize, 0, 0, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, 0.2f * glassAlphaMod, lightBlend, activeProgress);
            addDyn(builder, matrix, -inSize,  0, 0, 0, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, 0.2f * glassAlphaMod, lightBlend, activeProgress);
            addDyn(builder, matrix,  0,  inSize, 0, 0, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, 0.2f * glassAlphaMod, lightBlend, activeProgress);
            addDyn(builder, matrix,  inSize,  0, 0, 0, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, 0.2f * glassAlphaMod, lightBlend, activeProgress);
        }

        addDyn(builder, matrix,  0, -outSize, gap, -gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend, activeProgress);
        addDyn(builder, matrix,  0,  -inSize, gap, -gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend, activeProgress);
        addDyn(builder, matrix,  inSize,   0, gap, -gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend, activeProgress);
        addDyn(builder, matrix, outSize,   0, gap, -gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend, activeProgress);

        addDyn(builder, matrix, outSize,  0, gap, gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend, activeProgress);
        addDyn(builder, matrix,  inSize,  0, gap, gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend, activeProgress);
        addDyn(builder, matrix,  0,  inSize, gap, gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend, activeProgress);
        addDyn(builder, matrix,  0, outSize, gap, gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend, activeProgress);

        addDyn(builder, matrix,   0, outSize, -gap, gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend, activeProgress);
        addDyn(builder, matrix,   0,  inSize, -gap, gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend, activeProgress);
        addDyn(builder, matrix, -inSize,   0, -gap, gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend, activeProgress);
        addDyn(builder, matrix, -outSize,  0, -gap, gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend, activeProgress);

        addDyn(builder, matrix, -outSize,   0, -gap, -gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend, activeProgress);
        addDyn(builder, matrix, -inSize,    0, -gap, -gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend, activeProgress);
        addDyn(builder, matrix,   0,  -inSize, -gap, -gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend, activeProgress);
        addDyn(builder, matrix,   0, -outSize, -gap, -gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend, activeProgress);

        float coreSize = lerp(lerp(2.0f, 1.5f, phase1), 1.2f, phase2);
        float coreBaseA = lerp(lerp(1.0f, 0.7f, phase1), 0.9f, phase2);

        float coreGlow = lerp(0.7f, 0.6f + 0.4f * breathFactor, activeProgress);
        int coreA = (int) (baseAlpha * coreBaseA * coreGlow);
        int coreColor = (coreA << 24) | 0xFFFFFF;

        addSolid(builder, matrix,  0, -coreSize, 0, 0, finalScaleX, finalScaleY, coreColor);
        addSolid(builder, matrix, -coreSize,  0, 0, 0, finalScaleX, finalScaleY, coreColor);
        addSolid(builder, matrix,  0,  coreSize, 0, 0, finalScaleX, finalScaleY, coreColor);
        addSolid(builder, matrix,  coreSize,  0, 0, 0, finalScaleX, finalScaleY, coreColor);

        BufferUploader.drawWithShader(builder.end());
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static void addDyn(BufferBuilder b, Matrix4f m, float baseX, float baseY, float gapX, float gapY, float scaleX, float scaleY, float lx, float ly, int color, float alphaMod, float lightBlend, float activeProgress) {
        float finalX = (baseX + gapX) * scaleX;
        float finalY = (baseY + gapY) * scaleY;
        int argb = calcColor(baseX, baseY, lx, ly, color, alphaMod, lightBlend, activeProgress);
        b.vertex(m, finalX, finalY, 0).color(argb).endVertex();
    }

    private static void addSolid(BufferBuilder b, Matrix4f m, float baseX, float baseY, float gapX, float gapY, float scaleX, float scaleY, int argb) {
        float finalX = (baseX + gapX) * scaleX;
        float finalY = (baseY + gapY) * scaleY;
        b.vertex(m, finalX, finalY, 0).color(argb).endVertex();
    }

    private static int calcColor(float vx, float vy, float lx, float ly, int baseColor, float alphaMul, float lightBlend, float activeProgress) {
        int baseA = (int) (((baseColor >> 24) & 0xFF) * alphaMul);
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;

        float len = (float)Math.sqrt(vx*vx + vy*vy);
        float nx = len > 0 ? vx / len : 0;
        float ny = len > 0 ? vy / len : 0;

        float dot = nx * lx + ny * ly; // -1.0 到 1.0

        // 【核心魔法：Half-Lambert 光照模型】
        // 将 -1 到 1 的点积平滑映射到 0 到 1。这样光效会覆盖大半个图形，没有生硬的分界线
        float halfLambert = dot * 0.5f + 0.5f;

        // 使用指数函数收紧高光中心，而不是使用平顶的 Smoothstep，彻底消灭停顿感！
        float glow = (float) Math.pow(halfLambert, 2.5f);

        // Active 时大幅提亮高光
        float glowMultiplier = 0.4f + 1.6f * activeProgress;
        float finalGlow = Math.min(1.0f, glow * glowMultiplier * lightBlend);

        int outR = (int)(r + (255 - r) * finalGlow);
        int outG = (int)(g + (255 - g) * finalGlow);
        int outB = (int)(b + (255 - b) * finalGlow);
        int outA = (int)Math.min(255, baseA + (255 - baseA) * (finalGlow * 0.4f));

        // 生成边缘暗部阴影，增强立体感
        if (dot < -0.2f) {
            float shadow = (-dot - 0.2f) / 0.8f;
            outA = (int)(baseA * (1.0f - shadow * 0.6f * lightBlend));
        }

        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }
}