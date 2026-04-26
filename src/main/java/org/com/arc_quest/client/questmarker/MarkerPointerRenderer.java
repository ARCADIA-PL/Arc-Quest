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

    public static void draw(GuiGraphics gui, int accentColor, float lightX, float lightY, float tier) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        Matrix4f matrix = gui.pose().last().pose();

        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // --- UX/UI 多阶段形变映射计算 ---
        float phase1 = Math.max(0f, Math.min(1f, tier));        // 0.0 -> 1.0 (近距离变中距离)
        float phase2 = Math.max(0f, Math.min(1f, tier - 1f));   // 1.0 -> 2.0 (中距离变远距离)

        // 头部整流罩几何收缩
        float cTop = lerp(lerp(-12f, -8f, phase1), -5f, phase2);
        float cBot = lerp(lerp(-9f,  -6f, phase1), -3.5f, phase2);
        float cX   = lerp(lerp(8f,    4f, phase1),  2f, phase2);

        // 两侧尾翼几何收缩
        float wTop = cBot;
        float wBot = lerp(lerp(0f, -2f, phase1), -1f, phase2);
        float wOut = lerp(lerp(8f,  5f, phase1),  2.5f, phase2);
        float wIn  = lerp(lerp(6f,  3.5f, phase1), 1.5f, phase2);

        // 两翼脱离度(Gap): 中距离时护翼向两侧扯开，远距离变为极小缝隙
        float wingGap = lerp(lerp(0f, 2.5f, phase1), 1.0f, phase2);

        // 距离衰减透明度控制 (代入到了 calcColor 的最后一位参数)
        float globalAlpha = lerp(lerp(1.0f, 0.6f, phase1), 0.25f, phase2);
        float coreAlphaMod = Math.max(0f, 1.0f - phase1 * 1.5f);

        // 注: 这里为了完全保留你满意的打光效果，calcColor 参数里传过去的 (-8, -6), (8, -3) 这些是原有的假法线参数，不随着变形发生改变！

        // 1. 前置雷达撞风盖板 (保留在中央，不加 Gap)
        addVertex(builder, matrix, -cX, cTop, calcColor(-8, -6, lightX, lightY, accentColor, globalAlpha));
        addVertex(builder, matrix, -cX, cBot, calcColor(-8, -3, lightX, lightY, accentColor, globalAlpha));
        addVertex(builder, matrix,  cX, cBot, calcColor( 8, -3, lightX, lightY, accentColor, globalAlpha));
        addVertex(builder, matrix,  cX, cTop, calcColor( 8, -6, lightX, lightY, accentColor, globalAlpha));

        // 2. 左翼消散流线 (X轴偏移量叠加 -wingGap，向左扯开)
        addVertex(builder, matrix, -cX - wingGap,   wTop, calcColor(-8, -3, lightX, lightY, accentColor, globalAlpha));
        addVertex(builder, matrix, -wOut - wingGap, wBot, calcColor(-8,  6, lightX, lightY, accentColor, globalAlpha));
        addVertex(builder, matrix, -wIn - wingGap,  wBot, calcColor(-6,  6, lightX, lightY, accentColor, globalAlpha));
        addVertex(builder, matrix, -wIn - wingGap,  wTop, calcColor(-6, -3, lightX, lightY, accentColor, globalAlpha));

        // 3. 右翼消散流线 (X轴偏移量叠加 +wingGap，向右扯开)
        addVertex(builder, matrix, wIn + wingGap,  wTop, calcColor(6, -3, lightX, lightY, accentColor, globalAlpha));
        addVertex(builder, matrix, wIn + wingGap,  wBot, calcColor(6,  6, lightX, lightY, accentColor, globalAlpha));
        addVertex(builder, matrix, wOut + wingGap, wBot, calcColor(8,  6, lightX, lightY, accentColor, globalAlpha));
        addVertex(builder, matrix, cX + wingGap,   wTop, calcColor(8, -3, lightX, lightY, accentColor, globalAlpha));

        // 4. 中央微型机能锁扣 (中距离时完全消失，远距离不显示)
        if (coreAlphaMod > 0.05f) {
            int dotColor = (int)(((accentColor >> 24) & 0xFF) * 0.8f) << 24 | (accentColor & 0xFFFFFF);
            addVertex(builder, matrix, -2, -5, calcColor(-2, 1, lightX, lightY, dotColor, coreAlphaMod * globalAlpha));
            addVertex(builder, matrix, -2, -3, calcColor(-2, 3, lightX, lightY, dotColor, coreAlphaMod * globalAlpha));
            addVertex(builder, matrix,  2, -3, calcColor( 2, 3, lightX, lightY, dotColor, coreAlphaMod * globalAlpha));
            addVertex(builder, matrix,  2, -5, calcColor( 2, 1, lightX, lightY, dotColor, coreAlphaMod * globalAlpha));
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