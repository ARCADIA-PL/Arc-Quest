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

public final class MarkerRhombusRenderer {

    private MarkerRhombusRenderer() {}

    public static void draw(GuiGraphics gui, int accentColor, float breath, float lightX, float lightY) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        Matrix4f matrix = gui.pose().last().pose();

        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        int baseAlpha = (accentColor >> 24) & 0xFF;

        // 0. 边缘机能描边 (1px 灰色高透框，包裹在主装甲带距离10~11的位置)
        int rimA = (int)(baseAlpha * 0.35f); // 35% 透明度
        int rimColor = (rimA << 24) | 0x888888; // 灰色 #888888

        // 右上外框
        addVertex(builder, matrix,  0, -11, rimColor);
        addVertex(builder, matrix,  0, -10, rimColor);
        addVertex(builder, matrix, 10,   0, rimColor);
        addVertex(builder, matrix, 11,   0, rimColor);
        // 右下外框
        addVertex(builder, matrix, 11,   0, rimColor);
        addVertex(builder, matrix, 10,   0, rimColor);
        addVertex(builder, matrix,  0,  10, rimColor);
        addVertex(builder, matrix,  0,  11, rimColor);
        // 左下外框
        addVertex(builder, matrix,   0,  11, rimColor);
        addVertex(builder, matrix,   0,  10, rimColor);
        addVertex(builder, matrix, -10,   0, rimColor);
        addVertex(builder, matrix, -11,   0, rimColor);
        // 左上外框
        addVertex(builder, matrix, -11,   0, rimColor);
        addVertex(builder, matrix, -10,   0, rimColor);
        addVertex(builder, matrix,   0, -10, rimColor);
        addVertex(builder, matrix,   0, -11, rimColor);

        // 1. 全息玻璃内舱
        addVertex(builder, matrix,  0, -7, calcColor( 0, -7, lightX, lightY, accentColor, 0.2f));
        addVertex(builder, matrix, -7,  0, calcColor(-7,  0, lightX, lightY, accentColor, 0.2f));
        addVertex(builder, matrix,  0,  7, calcColor( 0,  7, lightX, lightY, accentColor, 0.2f));
        addVertex(builder, matrix,  7,  0, calcColor( 7,  0, lightX, lightY, accentColor, 0.2f));

        // 2. 外部主发光装甲带
        int glow = accentColor;
        addVertex(builder, matrix,  0, -10, calcColor( 0, -10, lightX, lightY, glow, 1.0f));
        addVertex(builder, matrix,  0,  -7, calcColor( 0,  -7, lightX, lightY, glow, 1.0f));
        addVertex(builder, matrix,  7,   0, calcColor( 7,   0, lightX, lightY, glow, 1.0f));
        addVertex(builder, matrix, 10,   0, calcColor(10,   0, lightX, lightY, glow, 1.0f));

        addVertex(builder, matrix, 10,  0, calcColor(10,  0, lightX, lightY, glow, 1.0f));
        addVertex(builder, matrix,  7,  0, calcColor( 7,  0, lightX, lightY, glow, 1.0f));
        addVertex(builder, matrix,  0,  7, calcColor( 0,  7, lightX, lightY, glow, 1.0f));
        addVertex(builder, matrix,  0, 10, calcColor( 0, 10, lightX, lightY, glow, 1.0f));

        addVertex(builder, matrix,   0, 10, calcColor( 0, 10, lightX, lightY, glow, 1.0f));
        addVertex(builder, matrix,   0,  7, calcColor( 0,  7, lightX, lightY, glow, 1.0f));
        addVertex(builder, matrix,  -7,  0, calcColor(-7,  0, lightX, lightY, glow, 1.0f));
        addVertex(builder, matrix, -10,  0, calcColor(-10, 0, lightX, lightY, glow, 1.0f));

        addVertex(builder, matrix, -10,   0, calcColor(-10,   0, lightX, lightY, glow, 1.0f));
        addVertex(builder, matrix,  -7,   0, calcColor( -7,   0, lightX, lightY, glow, 1.0f));
        addVertex(builder, matrix,   0,  -7, calcColor(  0,  -7, lightX, lightY, glow, 1.0f));
        addVertex(builder, matrix,   0, -10, calcColor(  0, -10, lightX, lightY, glow, 1.0f));

        // 3. 中央量子呼吸核心
        int coreA = (int) (baseAlpha * (0.4f + 0.6f * breath));
        int coreColor = (coreA << 24) | 0xFFFFFF;
        addVertex(builder, matrix,  0, -2, coreColor);
        addVertex(builder, matrix, -2,  0, coreColor);
        addVertex(builder, matrix,  0,  2, coreColor);
        addVertex(builder, matrix,  2,  0, coreColor);

        BufferUploader.drawWithShader(builder.end());
    }

    private static int calcColor(float vx, float vy, float lx, float ly, int baseColor, float alphaMul) {
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

        if (dot > 0.1f) {
            float glow = (dot - 0.1f) / 0.9f;
            outR = (int)(r + (255 - r) * glow);
            outG = (int)(g + (255 - g) * glow);
            outB = (int)(b + (255 - b) * glow);
            outA = (int)Math.min(255, baseA + (255 - baseA) * (glow * 0.4f));
        } else if (dot < -0.1f) {
            float shadow = (-dot - 0.1f) / 0.9f;
            outA = (int)(baseA * (1.0f - shadow * 0.8f));
        }

        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }

    private static void addVertex(BufferBuilder b, Matrix4f m, float x, float y, int argb) {
        b.vertex(m, x, y, 0).color(argb).endVertex();
    }
}