package org.arcadia.arc_quest.client.hud.questmarker;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

public final class MarkerPointerRenderer {

    private MarkerPointerRenderer() {
    }

    public static void draw(GuiGraphics gui, int accentColor, float time, float activeProgress, float lightX, float lightY, float tier) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        Matrix4f matrix = gui.pose().last().pose();

        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        int baseAlpha = (accentColor >> 24) & 0xFF;

        // ================= 【优化核心】限制边缘指示器的过度退化 =================
        // 指向屏幕外的箭头不应该缩小成看不见的点，必须保持作为“导航标识”的绝对可见性！
        float phase1 = Math.max(0f, Math.min(1f, tier));
        float phase2 = Math.max(0f, Math.min(1f, tier - 1f));

        float breathSpeed = 2.5f;
        float cycle = time * breathSpeed;
        float breathFactor = ((float) Math.sin(cycle) + 1.0f) * 0.5f;
        float breathAmp = 0.10f * activeProgress;
        float breathScale = 1.0f + breathAmp * breathFactor;

        float flowSpeed = -4.5f;
        float flowPhase = time * flowSpeed;
        float flowLx = (float) Math.cos(flowPhase);
        float flowLy = (float) Math.sin(flowPhase);

        float highlightMix = 0.2f + 0.8f * activeProgress;
        float mixLx = lightX * (1.0f - highlightMix) + flowLx * highlightMix;
        float mixLy = lightY * (1.0f - highlightMix) + flowLy * highlightMix;

        // 尺寸修改：FAR(phase2) 时箭头依然保持足够的宽度和高度，绝不聚拢成点
        float baseOutY = lerp(lerp(5.5f, 4.8f, phase1), 4.8f, phase2) * breathScale;
        float baseInY = lerp(lerp(3.8f, 3.2f, phase1), 3.2f, phase2) * breathScale;
        float baseXOut = lerp(lerp(7.2f, 6.2f, phase1), 6.2f, phase2) * breathScale;
        float baseXIn = lerp(lerp(4.8f, 4.0f, phase1), 4.0f, phase2) * breathScale;

        float tipOutY = lerp(lerp(-7.2f, -6.2f, phase1), -6.2f, phase2) * breathScale;
        float tipInY = lerp(lerp(-3.8f, -3.2f, phase1), -3.2f, phase2) * breathScale;
        float tipX = 0f;

        // 透明度修改：保底全局Alpha极高（0.75），发光核心永不熄灭（保底0.6）！
        float globalAlpha = lerp(lerp(1.0f, 0.9f, phase1), 0.9f, phase2);
        float coreAlphaMod = lerp(lerp(1.0f, 0.85f, phase1), 0.8f, phase2);

        float aa = 0.9f * breathScale;
        float tipOuterY = tipOutY - aa * 1.5f;
        float blOutX = -baseXOut - aa;
        float blOutY = baseOutY + aa;
        float brOutX = baseXOut + aa;
        float brOutY = baseOutY + aa;

        // ================= 绘制实体框架 =================
        addVertex(builder, matrix, -baseXIn, baseInY, calcColor(-baseXIn, baseInY, mixLx, mixLy, accentColor, globalAlpha, activeProgress, 1.0f));
        addVertex(builder, matrix, -baseXOut, baseOutY, calcColor(-baseXOut, baseOutY, mixLx, mixLy, accentColor, globalAlpha, activeProgress, 1.0f));
        addVertex(builder, matrix, baseXOut, baseOutY, calcColor(baseXOut, baseOutY, mixLx, mixLy, accentColor, globalAlpha, activeProgress, 1.0f));
        addVertex(builder, matrix, baseXIn, baseInY, calcColor(baseXIn, baseInY, mixLx, mixLy, accentColor, globalAlpha, activeProgress, 1.0f));

        addVertex(builder, matrix, tipX, tipOutY, calcColor(tipX, tipOutY, mixLx, mixLy, accentColor, globalAlpha, activeProgress, 1.0f));
        addVertex(builder, matrix, -baseXOut, baseOutY, calcColor(-baseXOut, baseOutY, mixLx, mixLy, accentColor, globalAlpha, activeProgress, 1.0f));
        addVertex(builder, matrix, -baseXIn, baseInY, calcColor(-baseXIn, baseInY, mixLx, mixLy, accentColor, globalAlpha, activeProgress, 1.0f));
        addVertex(builder, matrix, tipX, tipInY, calcColor(tipX, tipInY, mixLx, mixLy, accentColor, globalAlpha, activeProgress, 1.0f));

        addVertex(builder, matrix, tipX, tipInY, calcColor(tipX, tipInY, mixLx, mixLy, accentColor, globalAlpha, activeProgress, 1.0f));
        addVertex(builder, matrix, baseXIn, baseInY, calcColor(baseXIn, baseInY, mixLx, mixLy, accentColor, globalAlpha, activeProgress, 1.0f));
        addVertex(builder, matrix, baseXOut, baseOutY, calcColor(baseXOut, baseOutY, mixLx, mixLy, accentColor, globalAlpha, activeProgress, 1.0f));
        addVertex(builder, matrix, tipX, tipOutY, calcColor(tipX, tipOutY, mixLx, mixLy, accentColor, globalAlpha, activeProgress, 1.0f));

        // ================= 绘制外部抗锯齿羽化层 =================
        addVertex(builder, matrix, -baseXOut, baseOutY, calcColor(-baseXOut, baseOutY, mixLx, mixLy, accentColor, globalAlpha, activeProgress, 1.0f));
        addVertex(builder, matrix, blOutX, blOutY, calcColor(blOutX, blOutY, mixLx, mixLy, accentColor, globalAlpha, activeProgress, 0.0f));
        addVertex(builder, matrix, brOutX, brOutY, calcColor(brOutX, brOutY, mixLx, mixLy, accentColor, globalAlpha, activeProgress, 0.0f));
        addVertex(builder, matrix, baseXOut, baseOutY, calcColor(baseXOut, baseOutY, mixLx, mixLy, accentColor, globalAlpha, activeProgress, 1.0f));

        addVertex(builder, matrix, tipX, tipOuterY, calcColor(tipX, tipOuterY, mixLx, mixLy, accentColor, globalAlpha, activeProgress, 0.0f));
        addVertex(builder, matrix, blOutX, blOutY, calcColor(blOutX, blOutY, mixLx, mixLy, accentColor, globalAlpha, activeProgress, 0.0f));
        addVertex(builder, matrix, -baseXOut, baseOutY, calcColor(-baseXOut, baseOutY, mixLx, mixLy, accentColor, globalAlpha, activeProgress, 1.0f));
        addVertex(builder, matrix, tipX, tipOutY, calcColor(tipX, tipOutY, mixLx, mixLy, accentColor, globalAlpha, activeProgress, 1.0f));

        addVertex(builder, matrix, tipX, tipOutY, calcColor(tipX, tipOutY, mixLx, mixLy, accentColor, globalAlpha, activeProgress, 1.0f));
        addVertex(builder, matrix, baseXOut, baseOutY, calcColor(baseXOut, baseOutY, mixLx, mixLy, accentColor, globalAlpha, activeProgress, 1.0f));
        addVertex(builder, matrix, brOutX, brOutY, calcColor(brOutX, brOutY, mixLx, mixLy, accentColor, globalAlpha, activeProgress, 0.0f));
        addVertex(builder, matrix, tipX, tipOuterY, calcColor(tipX, tipOuterY, mixLx, mixLy, accentColor, globalAlpha, activeProgress, 0.0f));

        // ================= 内部发光核心 =================
        if (coreAlphaMod > 0.05f) {
            float coreGlow = lerp(0.8f, 0.6f + 0.4f * breathFactor, activeProgress);
            int coreA = (int) (baseAlpha * 0.8f * coreGlow);
            int dotColor = (coreA << 24) | (accentColor & 0xFFFFFF);

            // 核心同样保持极高的视觉下限，绝不缩成没有存在感的微尘
            float coreSize = lerp(lerp(1.8f, 1.4f, phase1), 1.0f, phase2) * breathScale;
            float coreTipY = lerp(lerp(-1.2f, -0.8f, phase1), -0.5f, phase2) * breathScale;
            float coreBotY = lerp(lerp(1.8f, 1.4f, phase1), 1.0f, phase2) * breathScale;

            float coreAlphaFinal = coreAlphaMod * globalAlpha;

            addVertex(builder, matrix, 0, coreTipY, calcColor(0, coreTipY, mixLx, mixLy, dotColor, coreAlphaFinal, activeProgress, 1.0f));
            addVertex(builder, matrix, -coreSize, coreBotY, calcColor(-coreSize, coreBotY, mixLx, mixLy, dotColor, coreAlphaFinal, activeProgress, 1.0f));
            addVertex(builder, matrix, coreSize, coreBotY, calcColor(coreSize, coreBotY, mixLx, mixLy, dotColor, coreAlphaFinal, activeProgress, 1.0f));
            addVertex(builder, matrix, 0, coreTipY, calcColor(0, coreTipY, mixLx, mixLy, dotColor, coreAlphaFinal, activeProgress, 1.0f));

            float coreAa = 0.8f * breathScale;
            float tipAaY = coreTipY - coreAa * 1.5f;
            float blAaX = -coreSize - coreAa;
            float blAaY = coreBotY + coreAa;
            float brAaX = coreSize + coreAa;
            float brAaY = coreBotY + coreAa;

            addVertex(builder, matrix, 0, coreTipY, calcColor(0, coreTipY, mixLx, mixLy, dotColor, coreAlphaFinal, activeProgress, 1.0f));
            addVertex(builder, matrix, 0, tipAaY, calcColor(0, tipAaY, mixLx, mixLy, dotColor, coreAlphaFinal, activeProgress, 0.0f));
            addVertex(builder, matrix, blAaX, blAaY, calcColor(blAaX, blAaY, mixLx, mixLy, dotColor, coreAlphaFinal, activeProgress, 0.0f));
            addVertex(builder, matrix, -coreSize, coreBotY, calcColor(-coreSize, coreBotY, mixLx, mixLy, dotColor, coreAlphaFinal, activeProgress, 1.0f));

            addVertex(builder, matrix, -coreSize, coreBotY, calcColor(-coreSize, coreBotY, mixLx, mixLy, dotColor, coreAlphaFinal, activeProgress, 1.0f));
            addVertex(builder, matrix, blAaX, blAaY, calcColor(blAaX, blAaY, mixLx, mixLy, dotColor, coreAlphaFinal, activeProgress, 0.0f));
            addVertex(builder, matrix, brAaX, brAaY, calcColor(brAaX, brAaY, mixLx, mixLy, dotColor, coreAlphaFinal, activeProgress, 0.0f));
            addVertex(builder, matrix, coreSize, coreBotY, calcColor(coreSize, coreBotY, mixLx, mixLy, dotColor, coreAlphaFinal, activeProgress, 1.0f));

            addVertex(builder, matrix, coreSize, coreBotY, calcColor(coreSize, coreBotY, mixLx, mixLy, dotColor, coreAlphaFinal, activeProgress, 1.0f));
            addVertex(builder, matrix, brAaX, brAaY, calcColor(brAaX, brAaY, mixLx, mixLy, dotColor, coreAlphaFinal, activeProgress, 0.0f));
            addVertex(builder, matrix, 0, tipAaY, calcColor(0, tipAaY, mixLx, mixLy, dotColor, coreAlphaFinal, activeProgress, 0.0f));
            addVertex(builder, matrix, 0, coreTipY, calcColor(0, coreTipY, mixLx, mixLy, dotColor, coreAlphaFinal, activeProgress, 1.0f));
        }

        BufferUploader.drawWithShader(builder.end());
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int calcColor(float nx, float ny, float lx, float ly, int baseColor, float alphaMul, float activeProgress, float edgeFade) {
        int baseA = (int) (((baseColor >> 24) & 0xFF) * alphaMul);
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;

        float len = (float) Math.sqrt(nx * nx + ny * ny);
        float normX = len > 0 ? nx / len : 0;
        float normY = len > 0 ? ny / len : 0;

        float dot = normX * lx + normY * ly;

        float halfLambert = dot * 0.5f + 0.5f;
        float glow = (float) Math.pow(halfLambert, 2.5f);

        float glowMultiplier = 0.4f + 1.6f * activeProgress;
        float finalGlow = Math.min(1.0f, glow * glowMultiplier);

        int outR = (int) (r + (255 - r) * finalGlow);
        int outG = (int) (g + (255 - g) * finalGlow);
        int outB = (int) (b + (255 - b) * finalGlow);
        int outA = (int) Math.min(255, baseA + (255 - baseA) * (finalGlow * 0.4f));

        if (dot < -0.2f) {
            float shadow = (-dot - 0.2f) / 0.8f;
            outA = (int) (baseA * (1.0f - shadow * 0.7f));
        }

        outA = (int) (outA * edgeFade);

        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }

    private static void addVertex(BufferBuilder b, Matrix4f m, float x, float y, int argb) {
        b.vertex(m, x, y, 0).color(argb).endVertex();
    }
}
