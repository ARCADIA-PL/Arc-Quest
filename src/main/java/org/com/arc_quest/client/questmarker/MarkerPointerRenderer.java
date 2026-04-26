package org.com.arc_quest.client.questmarker;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

public final class MarkerPointerRenderer {

    private MarkerPointerRenderer() {}

    public static void draw(GuiGraphics gui, int accentColor, float lightX, float lightY) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        Matrix4f matrix = gui.pose().last().pose();

        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // 1. 前置雷达撞风盖板
        addVertex(builder, matrix, -8, -12, calcColor(-8, -6, lightX, lightY, accentColor));
        addVertex(builder, matrix, -8,  -9, calcColor(-8, -3, lightX, lightY, accentColor));
        addVertex(builder, matrix,  8,  -9, calcColor( 8, -3, lightX, lightY, accentColor));
        addVertex(builder, matrix,  8, -12, calcColor( 8, -6, lightX, lightY, accentColor));

        // 2. 左翼消散流线
        addVertex(builder, matrix, -8, -9, calcColor(-8, -3, lightX, lightY, accentColor));
        addVertex(builder, matrix, -8,  0, calcColor(-8,  6, lightX, lightY, accentColor));
        addVertex(builder, matrix, -6,  0, calcColor(-6,  6, lightX, lightY, accentColor));
        addVertex(builder, matrix, -6, -9, calcColor(-6, -3, lightX, lightY, accentColor));

        // 3. 右翼消散流线
        addVertex(builder, matrix, 6, -9, calcColor(6, -3, lightX, lightY, accentColor));
        addVertex(builder, matrix, 6,  0, calcColor(6,  6, lightX, lightY, accentColor));
        addVertex(builder, matrix, 8,  0, calcColor(8,  6, lightX, lightY, accentColor));
        addVertex(builder, matrix, 8, -9, calcColor(8, -3, lightX, lightY, accentColor));

        // 4. 中央微型机能锁扣
        int dotColor = (int)(((accentColor >> 24) & 0xFF) * 0.8f) << 24 | (accentColor & 0xFFFFFF);
        addVertex(builder, matrix, -2, -5, calcColor(-2, 1, lightX, lightY, dotColor));
        addVertex(builder, matrix, -2, -3, calcColor(-2, 3, lightX, lightY, dotColor));
        addVertex(builder, matrix,  2, -3, calcColor( 2, 3, lightX, lightY, dotColor));
        addVertex(builder, matrix,  2, -5, calcColor( 2, 1, lightX, lightY, dotColor));

        BufferUploader.drawWithShader(builder.end());
    }

    private static int calcColor(float nx, float ny, float lx, float ly, int baseColor) {
        int baseA = (baseColor >> 24) & 0xFF;
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