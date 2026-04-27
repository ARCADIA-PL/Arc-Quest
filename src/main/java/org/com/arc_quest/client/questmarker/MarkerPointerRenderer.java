package org.com.arc_quest.client.questmarker;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

public final class MarkerPointerRenderer {

    private MarkerPointerRenderer() {}

    public static void draw(GuiGraphics gui, int accentColor, float lightX, float lightY, float tier) {
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

        // 由于不再外部传入 time，采用系统时间保证光效继续流转
        float time = System.currentTimeMillis() / 1000.0f;

        float breathSpeed = 6.0f;
        float breathAmp = 0.15f;
        float breathScale = 1.0f + breathAmp * (float)Math.sin(time * breathSpeed);

        float flowSpeed = 4.0f;
        float flowLx = (float)Math.cos(time * flowSpeed);
        float flowLy = (float)Math.sin(time * flowSpeed);

        float mixLx = lightX * 0.35f + flowLx * 0.65f;
        float mixLy = lightY * 0.35f + flowLy * 0.65f;

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

        addVertex(builder, matrix, -cX, cTop, calcColor(-8, -6, mixLx, mixLy, accentColor, globalAlpha));
        addVertex(builder, matrix, -cX, cBot, calcColor(-8, -3, mixLx, mixLy, accentColor, globalAlpha));
        addVertex(builder, matrix,  cX, cBot, calcColor( 8, -3, mixLx, mixLy, accentColor, globalAlpha));
        addVertex(builder, matrix,  cX, cTop, calcColor( 8, -6, mixLx, mixLy, accentColor, globalAlpha));

        addVertex(builder, matrix, -cX - wingGap,   wTop, calcColor(-8, -3, mixLx, mixLy, accentColor, globalAlpha));
        addVertex(builder, matrix, -wOut - wingGap, wBot, calcColor(-8,  6, mixLx, mixLy, accentColor, globalAlpha));
        addVertex(builder, matrix, -wIn - wingGap,  wBot, calcColor(-6,  6, mixLx, mixLy, accentColor, globalAlpha));
        addVertex(builder, matrix, -wIn - wingGap,  wTop, calcColor(-6, -3, mixLx, mixLy, accentColor, globalAlpha));

        addVertex(builder, matrix, wIn + wingGap,  wTop, calcColor(6, -3, mixLx, mixLy, accentColor, globalAlpha));
        addVertex(builder, matrix, wIn + wingGap,  wBot, calcColor(6,  6, mixLx, mixLy, accentColor, globalAlpha));
        addVertex(builder, matrix, wOut + wingGap, wBot, calcColor(8,  6, mixLx, mixLy, accentColor, globalAlpha));
        addVertex(builder, matrix, cX + wingGap,   wTop, calcColor(8, -3, mixLx, mixLy, accentColor, globalAlpha));

        if (coreAlphaMod > 0.05f) {
            int dotColor = (int)(baseAlpha * 0.8f) << 24 | (accentColor & 0xFFFFFF);
            addVertex(builder, matrix, -2, -5, calcColor(-2, 1, mixLx, mixLy, dotColor, coreAlphaMod * globalAlpha));
            addVertex(builder, matrix, -2, -3, calcColor(-2, 3, mixLx, mixLy, dotColor, coreAlphaMod * globalAlpha));
            addVertex(builder, matrix,  2, -3, calcColor( 2, 3, mixLx, mixLy, dotColor, coreAlphaMod * globalAlpha));
            addVertex(builder, matrix,  2, -5, calcColor( 2, 1, mixLx, mixLy, dotColor, coreAlphaMod * globalAlpha));
        }

        BufferUploader.drawWithShader(builder.end());
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int calcColor(float nx, float ny, float lx, float ly, int baseColor, float alphaMul) {
        int baseA = (int) (((baseColor >> 24) & 0xFF) * alphaMul);
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;

        float len = (float)Math.sqrt(nx*nx + ny*ny);
        float normX = len > 0 ? nx / len : 0;
        float normY = len > 0 ? ny / len : 0;

        float dot = normX * lx + normY * ly;
        int outR = r, outG = g, outB = b;
        int outA = baseA;

        if (dot > 0.1f) {
            float glow = (dot - 0.1f) / 0.9f;
            outR = (int)(r + (255 - r) * glow);
            outG = (int)(g + (255 - g) * glow);
            outB = (int)(b + (255 - b) * glow);
            outA = (int)Math.min(255, baseA + (255 - baseA) * (glow * 0.4f));
        } else if (dot < -0.1f) {
            float shadow = (-dot - 0.1f) / 0.9f;
            outA = (int)(baseA * (1.0f - shadow * 0.95f));
        }

        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }

    private static void addVertex(BufferBuilder b, Matrix4f m, float x, float y, int argb) {
        b.vertex(m, x, y, 0).color(argb).endVertex();
    }
}