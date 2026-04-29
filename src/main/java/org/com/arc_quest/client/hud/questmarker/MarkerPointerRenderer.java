// file_name: MarkerPointerRenderer.java
package org.com.arc_quest.client.hud.questmarker;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

public final class MarkerPointerRenderer {

    private MarkerPointerRenderer() {}

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

        // ================= 动画核心重构 (与菱形严格一致) =================
        float breathSpeed = 2.5f;
        float cycle = time * breathSpeed;
        float breathFactor = ((float) Math.sin(cycle) + 1.0f) * 0.5f;
        float breathAmp = 0.10f * activeProgress;
        float breathScale = 1.0f + breathAmp * breathFactor;

        float flowSpeed = -4.5f;
        float flowPhase = time * flowSpeed;
        float flowLx = (float)Math.cos(flowPhase);
        float flowLy = (float)Math.sin(flowPhase);

        float highlightMix = 0.2f + 0.8f * activeProgress;
        float mixLx = lightX * (1.0f - highlightMix) + flowLx * highlightMix;
        float mixLy = lightY * (1.0f - highlightMix) + flowLy * highlightMix;
        // ====================================================================

        float cTop = lerp(lerp(-12f, -8f, phase1), -5f, phase2) * breathScale;
        float cBot = lerp(lerp(-9f,  -6f, phase1), -3.5f, phase2) * breathScale;
        float cX   = lerp(lerp(8f,    4f, phase1),  2f, phase2) * breathScale;

        float wTop = cBot;
        float wBot = lerp(lerp(0f, -2f, phase1), -1f, phase2) * breathScale;
        float wOut = lerp(lerp(8f,  5f, phase1),  2.5f, phase2) * breathScale;
        float wIn  = lerp(lerp(6f,  3.5f, phase1), 1.5f, phase2) * breathScale;
        float wingGap = lerp(lerp(0f, 2.5f, phase1), 1.0f, phase2) * breathScale;

        float globalAlpha = lerp(lerp(1.0f, 0.6f, phase1), 0.25f, phase2);
        float coreAlphaMod = Math.max(0f, 1.0f - phase1 * 1.5f);

        addVertex(builder, matrix, -cX, cTop, calcColor(-8, -6, mixLx, mixLy, accentColor, globalAlpha, activeProgress));
        addVertex(builder, matrix, -cX, cBot, calcColor(-8, -3, mixLx, mixLy, accentColor, globalAlpha, activeProgress));
        addVertex(builder, matrix,  cX, cBot, calcColor( 8, -3, mixLx, mixLy, accentColor, globalAlpha, activeProgress));
        addVertex(builder, matrix,  cX, cTop, calcColor( 8, -6, mixLx, mixLy, accentColor, globalAlpha, activeProgress));

        addVertex(builder, matrix, -cX - wingGap,   wTop, calcColor(-8, -3, mixLx, mixLy, accentColor, globalAlpha, activeProgress));
        addVertex(builder, matrix, -wOut - wingGap, wBot, calcColor(-8,  6, mixLx, mixLy, accentColor, globalAlpha, activeProgress));
        addVertex(builder, matrix, -wIn - wingGap,  wBot, calcColor(-6,  6, mixLx, mixLy, accentColor, globalAlpha, activeProgress));
        addVertex(builder, matrix, -wIn - wingGap,  wTop, calcColor(-6, -3, mixLx, mixLy, accentColor, globalAlpha, activeProgress));

        addVertex(builder, matrix, wIn + wingGap,  wTop, calcColor(6, -3, mixLx, mixLy, accentColor, globalAlpha, activeProgress));
        addVertex(builder, matrix, wIn + wingGap,  wBot, calcColor(6,  6, mixLx, mixLy, accentColor, globalAlpha, activeProgress));
        addVertex(builder, matrix, wOut + wingGap, wBot, calcColor(8,  6, mixLx, mixLy, accentColor, globalAlpha, activeProgress));
        addVertex(builder, matrix, cX + wingGap,   wTop, calcColor(8, -3, mixLx, mixLy, accentColor, globalAlpha, activeProgress));

        if (coreAlphaMod > 0.05f) {
            float coreGlow = lerp(0.8f, 0.6f + 0.4f * breathFactor, activeProgress);
            int coreA = (int)(baseAlpha * 0.8f * coreGlow);
            int dotColor = (coreA << 24) | (accentColor & 0xFFFFFF);
            addVertex(builder, matrix, -2, -5, calcColor(-2, 1, mixLx, mixLy, dotColor, coreAlphaMod * globalAlpha, activeProgress));
            addVertex(builder, matrix, -2, -3, calcColor(-2, 3, mixLx, mixLy, dotColor, coreAlphaMod * globalAlpha, activeProgress));
            addVertex(builder, matrix,  2, -3, calcColor( 2, 3, mixLx, mixLy, dotColor, coreAlphaMod * globalAlpha, activeProgress));
            addVertex(builder, matrix,  2, -5, calcColor( 2, 1, mixLx, mixLy, dotColor, coreAlphaMod * globalAlpha, activeProgress));
        }

        BufferUploader.drawWithShader(builder.end());
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int calcColor(float nx, float ny, float lx, float ly, int baseColor, float alphaMul, float activeProgress) {
        int baseA = (int) (((baseColor >> 24) & 0xFF) * alphaMul);
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;

        float len = (float)Math.sqrt(nx*nx + ny*ny);
        float normX = len > 0 ? nx / len : 0;
        float normY = len > 0 ? ny / len : 0;

        float dot = normX * lx + normY * ly;

        // 【应用 Half-Lambert 光照模型】
        float halfLambert = dot * 0.5f + 0.5f;
        float glow = (float) Math.pow(halfLambert, 2.5f);

        float glowMultiplier = 0.4f + 1.6f * activeProgress;
        float finalGlow = Math.min(1.0f, glow * glowMultiplier);

        int outR = (int)(r + (255 - r) * finalGlow);
        int outG = (int)(g + (255 - g) * finalGlow);
        int outB = (int)(b + (255 - b) * finalGlow);
        int outA = (int)Math.min(255, baseA + (255 - baseA) * (finalGlow * 0.4f));

        if (dot < -0.2f) {
            float shadow = (-dot - 0.2f) / 0.8f;
            outA = (int)(baseA * (1.0f - shadow * 0.7f));
        }

        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }

    private static void addVertex(BufferBuilder b, Matrix4f m, float x, float y, int argb) {
        b.vertex(m, x, y, 0).color(argb).endVertex();
    }
}