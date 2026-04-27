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

        float phase1 = clamp01(tier);
        float phase2 = clamp01(tier - 1f);

        float breathSpeed = 2.5f;
        float cycle = time * breathSpeed;
        float breathFactor = ((float) Math.sin(cycle) + 1.0f) * 0.5f;
        float breathAmp = 0.10f * activeProgress;
        float breathScale = 1.0f + breathAmp * breathFactor;

        // 匀速流转相位（核心）
        float flowSpeed = -4.8f;
        float flowPhase = time * flowSpeed;

        // 外部光向 + 内部流转光向混合
        float flowLx = (float) Math.cos(flowPhase);
        float flowLy = (float) Math.sin(flowPhase);

        float highlightMix = 0.22f + 0.78f * activeProgress;
        float mixLx = lightX * (1.0f - highlightMix) + flowLx * highlightMix;
        float mixLy = lightY * (1.0f - highlightMix) + flowLy * highlightMix;

        float mixLen = (float) Math.sqrt(mixLx * mixLx + mixLy * mixLy);
        if (mixLen > 1e-5f) {
            mixLx /= mixLen;
            mixLy /= mixLen;
        }

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
            int rimA = (int) (baseAlpha * 0.35f * rimAlphaMod);
            int rimColor = (rimA << 24) | 0x888888;
            float rimOut = outSize + 1f;
            float rimIn = outSize;

            addSolid(builder, matrix,  0, -rimOut,  gap, -gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix,  0, -rimIn,   gap, -gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix,  rimIn, 0,    gap, -gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix,  rimOut, 0,   gap, -gap, finalScaleX, finalScaleY, rimColor);

            addSolid(builder, matrix,  rimOut, 0,   gap,  gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix,  rimIn, 0,    gap,  gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix,  0, rimIn,    gap,  gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix,  0, rimOut,   gap,  gap, finalScaleX, finalScaleY, rimColor);

            addSolid(builder, matrix,  0, rimOut,  -gap,  gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix,  0, rimIn,   -gap,  gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix, -rimIn, 0,   -gap,  gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix, -rimOut, 0,  -gap,  gap, finalScaleX, finalScaleY, rimColor);

            addSolid(builder, matrix, -rimOut, 0,  -gap, -gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix, -rimIn, 0,   -gap, -gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix,  0, -rimIn,  -gap, -gap, finalScaleX, finalScaleY, rimColor);
            addSolid(builder, matrix,  0, -rimOut, -gap, -gap, finalScaleX, finalScaleY, rimColor);
        }

        if (glassAlphaMod > 0.05f && inSize > 0) {
            addDyn(builder, matrix,  0, -inSize, 0, 0, finalScaleX, finalScaleY, mixLx, mixLy, flowPhase, accentColor, 0.2f * glassAlphaMod, lightBlend, activeProgress);
            addDyn(builder, matrix, -inSize, 0,  0, 0, finalScaleX, finalScaleY, mixLx, mixLy, flowPhase, accentColor, 0.2f * glassAlphaMod, lightBlend, activeProgress);
            addDyn(builder, matrix,  0, inSize,  0, 0, finalScaleX, finalScaleY, mixLx, mixLy, flowPhase, accentColor, 0.2f * glassAlphaMod, lightBlend, activeProgress);
            addDyn(builder, matrix,  inSize, 0,  0, 0, finalScaleX, finalScaleY, mixLx, mixLy, flowPhase, accentColor, 0.2f * glassAlphaMod, lightBlend, activeProgress);
        }

        addDyn(builder, matrix,  0, -outSize,  gap, -gap, finalScaleX, finalScaleY, mixLx, mixLy, flowPhase, accentColor, globalAlpha, lightBlend, activeProgress);
        addDyn(builder, matrix,  0, -inSize,   gap, -gap, finalScaleX, finalScaleY, mixLx, mixLy, flowPhase, accentColor, globalAlpha, lightBlend, activeProgress);
        addDyn(builder, matrix,  inSize, 0,    gap, -gap, finalScaleX, finalScaleY, mixLx, mixLy, flowPhase, accentColor, globalAlpha, lightBlend, activeProgress);
        addDyn(builder, matrix,  outSize, 0,   gap, -gap, finalScaleX, finalScaleY, mixLx, mixLy, flowPhase, accentColor, globalAlpha, lightBlend, activeProgress);

        addDyn(builder, matrix,  outSize, 0,   gap,  gap, finalScaleX, finalScaleY, mixLx, mixLy, flowPhase, accentColor, globalAlpha, lightBlend, activeProgress);
        addDyn(builder, matrix,  inSize, 0,    gap,  gap, finalScaleX, finalScaleY, mixLx, mixLy, flowPhase, accentColor, globalAlpha, lightBlend, activeProgress);
        addDyn(builder, matrix,  0, inSize,    gap,  gap, finalScaleX, finalScaleY, mixLx, mixLy, flowPhase, accentColor, globalAlpha, lightBlend, activeProgress);
        addDyn(builder, matrix,  0, outSize,   gap,  gap, finalScaleX, finalScaleY, mixLx, mixLy, flowPhase, accentColor, globalAlpha, lightBlend, activeProgress);

        addDyn(builder, matrix,  0, outSize,  -gap,  gap, finalScaleX, finalScaleY, mixLx, mixLy, flowPhase, accentColor, globalAlpha, lightBlend, activeProgress);
        addDyn(builder, matrix,  0, inSize,   -gap,  gap, finalScaleX, finalScaleY, mixLx, mixLy, flowPhase, accentColor, globalAlpha, lightBlend, activeProgress);
        addDyn(builder, matrix, -inSize, 0,   -gap,  gap, finalScaleX, finalScaleY, mixLx, mixLy, flowPhase, accentColor, globalAlpha, lightBlend, activeProgress);
        addDyn(builder, matrix, -outSize, 0,  -gap,  gap, finalScaleX, finalScaleY, mixLx, mixLy, flowPhase, accentColor, globalAlpha, lightBlend, activeProgress);

        addDyn(builder, matrix, -outSize, 0,  -gap, -gap, finalScaleX, finalScaleY, mixLx, mixLy, flowPhase, accentColor, globalAlpha, lightBlend, activeProgress);
        addDyn(builder, matrix, -inSize, 0,   -gap, -gap, finalScaleX, finalScaleY, mixLx, mixLy, flowPhase, accentColor, globalAlpha, lightBlend, activeProgress);
        addDyn(builder, matrix,  0, -inSize,  -gap, -gap, finalScaleX, finalScaleY, mixLx, mixLy, flowPhase, accentColor, globalAlpha, lightBlend, activeProgress);
        addDyn(builder, matrix,  0, -outSize, -gap, -gap, finalScaleX, finalScaleY, mixLx, mixLy, flowPhase, accentColor, globalAlpha, lightBlend, activeProgress);

        float coreSize = lerp(lerp(2.0f, 1.5f, phase1), 1.2f, phase2);
        float coreBaseA = lerp(lerp(1.0f, 0.7f, phase1), 0.9f, phase2);

        float coreGlow = lerp(0.7f, 0.6f + 0.4f * breathFactor, activeProgress);
        int coreA = (int) (baseAlpha * coreBaseA * coreGlow);
        int coreColor = (coreA << 24) | 0xFFFFFF;

        addSolid(builder, matrix,  0, -coreSize, 0, 0, finalScaleX, finalScaleY, coreColor);
        addSolid(builder, matrix, -coreSize, 0,  0, 0, finalScaleX, finalScaleY, coreColor);
        addSolid(builder, matrix,  0, coreSize,  0, 0, finalScaleX, finalScaleY, coreColor);
        addSolid(builder, matrix,  coreSize, 0,  0, 0, finalScaleX, finalScaleY, coreColor);

        BufferUploader.drawWithShader(builder.end());
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static void addDyn(
            BufferBuilder b, Matrix4f m,
            float baseX, float baseY, float gapX, float gapY,
            float scaleX, float scaleY,
            float lx, float ly, float flowPhase,
            int color, float alphaMod, float lightBlend, float activeProgress
    ) {
        float finalX = (baseX + gapX) * scaleX;
        float finalY = (baseY + gapY) * scaleY;
        int argb = calcColor(baseX, baseY, lx, ly, flowPhase, color, alphaMod, lightBlend, activeProgress);
        b.vertex(m, finalX, finalY, 0).color(argb).endVertex();
    }

    private static void addSolid(BufferBuilder b, Matrix4f m, float baseX, float baseY, float gapX, float gapY, float scaleX, float scaleY, int argb) {
        float finalX = (baseX + gapX) * scaleX;
        float finalY = (baseY + gapY) * scaleY;
        b.vertex(m, finalX, finalY, 0).color(argb).endVertex();
    }

    private static int calcColor(
            float vx, float vy,
            float lx, float ly,
            float flowPhase,
            int baseColor,
            float alphaMul,
            float lightBlend,
            float activeProgress
    ) {
        int baseA = (int) (((baseColor >> 24) & 0xFF) * alphaMul);
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;

        float len = (float) Math.sqrt(vx * vx + vy * vy);
        float nx = len > 0 ? vx / len : 0f;
        float ny = len > 0 ? vy / len : 0f;

        // 传统法线光照（只保留中低频贡献）
        float dot = nx * lx + ny * ly;
        float halfLambert = dot * 0.5f + 0.5f;
        float diffuse = (float) Math.pow(clamp01(halfLambert), 1.6f);

        // 角度流转高光（核心：沿极角匀速，不会在角落停顿）
        float theta = (float) Math.atan2(vy, vx); // [-PI, PI]
        float wave = 0.5f + 0.5f * (float) Math.cos(theta - flowPhase); // 匀速绕行
        float flowGlow = (float) Math.pow(clamp01(wave), 3.0f);

        // 融合：active 越高，流转高光占比越大
        float flowWeight = lerp(0.45f, 0.78f, activeProgress);
        float diffuseWeight = 1.0f - flowWeight;

        float glow = diffuse * diffuseWeight + flowGlow * flowWeight;
        float glowGain = lerp(0.9f, 1.65f, activeProgress);
        float finalGlow = clamp01(glow * glowGain * lightBlend);

        int outR = (int) (r + (255 - r) * finalGlow);
        int outG = (int) (g + (255 - g) * finalGlow);
        int outB = (int) (b + (255 - b) * finalGlow);
        int outA = (int) Math.min(255, baseA + (255 - baseA) * (finalGlow * 0.36f));

        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }
}