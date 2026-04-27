package org.com.arc_quest.client.questmarker;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

public final class MarkerRhombusRenderer {

    private MarkerRhombusRenderer() {}

    public static void draw(GuiGraphics gui, int accentColor, float breath, float lightX, float lightY, float tier) {
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

        float time = System.currentTimeMillis() / 1000.0f;

        // 由于调用端传入的 breath 是 Math.sin(...) 算出来的 0 到 1 之间的浮动系数
        // 映射为震幅用于替代旧逻辑的直接 sin 计算
        float breathAmp = 0.15f;
        float breathSine = breath * 2.0f - 1.0f;
        float breathScale = 1.0f + breathAmp * breathSine;

        // 生成虚拟旋转光照向量并混合环境偏转光
        float flowSpeed = 4.0f;
        float flowLx = (float)Math.cos(time * flowSpeed);
        float flowLy = (float)Math.sin(time * flowSpeed);

        float mixLx = lightX * 0.35f + flowLx * 0.65f;
        float mixLy = lightY * 0.35f + flowLy * 0.65f;

        // --- 几何与形变推导 ---
        float outSize = lerp(lerp(10f, 5f, phase1), 3.5f, phase2);
        float inSize  = lerp(lerp(7f,  3f, phase1), 0.0f, phase2);
        float gap = lerp(lerp(0f, 3.5f, phase1), 0.0f, phase2);

        // 全局乘上呼吸系数，让它明显涨缩
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
            addDyn(builder, matrix,  0, -inSize, 0, 0, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, 0.2f * glassAlphaMod, lightBlend);
            addDyn(builder, matrix, -inSize,  0, 0, 0, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, 0.2f * glassAlphaMod, lightBlend);
            addDyn(builder, matrix,  0,  inSize, 0, 0, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, 0.2f * glassAlphaMod, lightBlend);
            addDyn(builder, matrix,  inSize,  0, 0, 0, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, 0.2f * glassAlphaMod, lightBlend);
        }

        addDyn(builder, matrix,  0, -outSize, gap, -gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend);
        addDyn(builder, matrix,  0,  -inSize, gap, -gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend);
        addDyn(builder, matrix,  inSize,   0, gap, -gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend);
        addDyn(builder, matrix, outSize,   0, gap, -gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend);

        addDyn(builder, matrix, outSize,  0, gap, gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend);
        addDyn(builder, matrix,  inSize,  0, gap, gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend);
        addDyn(builder, matrix,  0,  inSize, gap, gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend);
        addDyn(builder, matrix,  0, outSize, gap, gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend);

        addDyn(builder, matrix,   0, outSize, -gap, gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend);
        addDyn(builder, matrix,   0,  inSize, -gap, gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend);
        addDyn(builder, matrix, -inSize,   0, -gap, gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend);
        addDyn(builder, matrix, -outSize,  0, -gap, gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend);

        addDyn(builder, matrix, -outSize,   0, -gap, -gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend);
        addDyn(builder, matrix, -inSize,    0, -gap, -gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend);
        addDyn(builder, matrix,   0,  -inSize, -gap, -gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend);
        addDyn(builder, matrix,   0, -outSize, -gap, -gap, finalScaleX, finalScaleY, mixLx, mixLy, accentColor, globalAlpha, lightBlend);

        float coreSize = lerp(lerp(2.0f, 1.5f, phase1), 1.2f, phase2);
        float coreBaseA = lerp(lerp(1.0f, 0.7f, phase1), 0.9f, phase2);

        // 直接复用 breath 实现中心的呼吸亮度明暗变化
        int coreA = (int) (baseAlpha * coreBaseA * (0.4f + 0.6f * breath));
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

    private static void addDyn(BufferBuilder b, Matrix4f m, float baseX, float baseY, float gapX, float gapY, float scaleX, float scaleY, float lx, float ly, int color, float alphaMod, float lightBlend) {
        float finalX = (baseX + gapX) * scaleX;
        float finalY = (baseY + gapY) * scaleY;
        int argb = calcColor(baseX, baseY, lx, ly, color, alphaMod, lightBlend);
        b.vertex(m, finalX, finalY, 0).color(argb).endVertex();
    }

    private static void addSolid(BufferBuilder b, Matrix4f m, float baseX, float baseY, float gapX, float gapY, float scaleX, float scaleY, int argb) {
        float finalX = (baseX + gapX) * scaleX;
        float finalY = (baseY + gapY) * scaleY;
        b.vertex(m, finalX, finalY, 0).color(argb).endVertex();
    }

    private static int calcColor(float vx, float vy, float lx, float ly, int baseColor, float alphaMul, float lightBlend) {
        int baseA = (int) (((baseColor >> 24) & 0xFF) * alphaMul);
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;

        float len = (float)Math.sqrt(vx*vx + vy*vy);
        float nx = len > 0 ? vx / len : 0;
        float ny = len > 0 ? vy / len : 0;

        float dot = nx * lx + ny * ly;
        int outR = r, outG = g, outB = b;
        int outA = baseA;

        // 面向高光光源时，产生光效流转提亮
        if (dot > 0.1f) {
            float glow = ((dot - 0.1f) / 0.9f) * lightBlend;
            outR = (int)(r + (255 - r) * glow);
            outG = (int)(g + (255 - g) * glow);
            outB = (int)(b + (255 - b) * glow);
            outA = (int)Math.min(255, baseA + (255 - baseA) * (glow * 0.4f));
        } else if (dot < -0.1f) {
            float shadow = ((-dot - 0.1f) / 0.9f) * lightBlend;
            outA = (int)(baseA * (1.0f - shadow * 0.8f));
        }

        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }

}