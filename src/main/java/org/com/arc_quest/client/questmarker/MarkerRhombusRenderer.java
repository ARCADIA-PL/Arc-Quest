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

        float phase1 = Math.max(0f, Math.min(1f, tier));        // 0.0 -> 1.0 (近 -> 中)
        float phase2 = Math.max(0f, Math.min(1f, tier - 1f));   // 1.0 -> 2.0 (中 -> 远)

        // 1. 尺寸：近距 10 -> 中距 5 -> 远距 3.5 (不再拉长，而是变成一个小巧的指示点)
        float outSize = lerp(lerp(10f, 5f, phase1), 3.5f, phase2);
        // 内部镂空：远距时压到 0，合拢成一个实心的小菱形
        float inSize  = lerp(lerp(7f,  3f, phase1), 0.0f, phase2);
        // 装甲撕裂：炸开后，远距重新合拢
        float gap = lerp(lerp(0f, 3.5f, phase1), 0.0f, phase2);

        // 【废弃巨丑拉伸】：保持 1.0，绝不扭曲你原本的形状比例
        float scaleX = 1.0f;
        float scaleY = 1.0f;

        // 【核心新增】：光照混合度。近/中距离=100%光照，远距平滑衰减到0(失去光照变成纯净色块)
        float lightBlend = 1.0f - phase2;

        float globalAlpha = lerp(lerp(1.0f, 0.8f, phase1), 0.75f, phase2);

        // 内部玻璃和灰框在中/远距离淡出
        float glassAlphaMod = Math.max(0f, 1.0f - phase1 * 1.5f);
        float rimAlphaMod   = Math.max(0f, 1.0f - phase1 * 2.0f);

        // 0. 边缘机能描边
        if (rimAlphaMod > 0.05f) {
            int rimA = (int)(baseAlpha * 0.35f * rimAlphaMod);
            int rimColor = (rimA << 24) | 0x888888;
            float rimOut = outSize + 1f;
            float rimIn = outSize;

            addSolid(builder, matrix,  0, -rimOut, gap, -gap, scaleX, scaleY, rimColor);
            addSolid(builder, matrix,  0, -rimIn,  gap, -gap, scaleX, scaleY, rimColor);
            addSolid(builder, matrix, rimIn,   0,  gap, -gap, scaleX, scaleY, rimColor);
            addSolid(builder, matrix, rimOut,  0,  gap, -gap, scaleX, scaleY, rimColor);
            addSolid(builder, matrix, rimOut,  0,  gap, gap, scaleX, scaleY, rimColor);
            addSolid(builder, matrix, rimIn,   0,  gap, gap, scaleX, scaleY, rimColor);
            addSolid(builder, matrix,  0,  rimIn,  gap, gap, scaleX, scaleY, rimColor);
            addSolid(builder, matrix,  0, rimOut,  gap, gap, scaleX, scaleY, rimColor);
            addSolid(builder, matrix,   0, rimOut, -gap, gap, scaleX, scaleY, rimColor);
            addSolid(builder, matrix,   0,  rimIn, -gap, gap, scaleX, scaleY, rimColor);
            addSolid(builder, matrix, -rimIn,   0, -gap, gap, scaleX, scaleY, rimColor);
            addSolid(builder, matrix, -rimOut,  0, -gap, gap, scaleX, scaleY, rimColor);
            addSolid(builder, matrix, -rimOut,  0, -gap, -gap, scaleX, scaleY, rimColor);
            addSolid(builder, matrix, -rimIn,   0, -gap, -gap, scaleX, scaleY, rimColor);
            addSolid(builder, matrix,   0, -rimIn, -gap, -gap, scaleX, scaleY, rimColor);
            addSolid(builder, matrix,   0,-rimOut, -gap, -gap, scaleX, scaleY, rimColor);
        }

        // 1. 全息玻璃内舱
        if (glassAlphaMod > 0.05f && inSize > 0) {
            addDyn(builder, matrix,  0, -inSize, 0, 0, scaleX, scaleY, lightX, lightY, accentColor, 0.2f * glassAlphaMod, lightBlend);
            addDyn(builder, matrix, -inSize,  0, 0, 0, scaleX, scaleY, lightX, lightY, accentColor, 0.2f * glassAlphaMod, lightBlend);
            addDyn(builder, matrix,  0,  inSize, 0, 0, scaleX, scaleY, lightX, lightY, accentColor, 0.2f * glassAlphaMod, lightBlend);
            addDyn(builder, matrix,  inSize,  0, 0, 0, scaleX, scaleY, lightX, lightY, accentColor, 0.2f * glassAlphaMod, lightBlend);
        }

        // 2. 外部主发光装甲带
        int glow = accentColor;
        addDyn(builder, matrix,  0, -outSize, gap, -gap, scaleX, scaleY, lightX, lightY, glow, globalAlpha, lightBlend);
        addDyn(builder, matrix,  0,  -inSize, gap, -gap, scaleX, scaleY, lightX, lightY, glow, globalAlpha, lightBlend);
        addDyn(builder, matrix,  inSize,   0, gap, -gap, scaleX, scaleY, lightX, lightY, glow, globalAlpha, lightBlend);
        addDyn(builder, matrix, outSize,   0, gap, -gap, scaleX, scaleY, lightX, lightY, glow, globalAlpha, lightBlend);

        addDyn(builder, matrix, outSize,  0, gap, gap, scaleX, scaleY, lightX, lightY, glow, globalAlpha, lightBlend);
        addDyn(builder, matrix,  inSize,  0, gap, gap, scaleX, scaleY, lightX, lightY, glow, globalAlpha, lightBlend);
        addDyn(builder, matrix,  0,  inSize, gap, gap, scaleX, scaleY, lightX, lightY, glow, globalAlpha, lightBlend);
        addDyn(builder, matrix,  0, outSize, gap, gap, scaleX, scaleY, lightX, lightY, glow, globalAlpha, lightBlend);

        addDyn(builder, matrix,   0, outSize, -gap, gap, scaleX, scaleY, lightX, lightY, glow, globalAlpha, lightBlend);
        addDyn(builder, matrix,   0,  inSize, -gap, gap, scaleX, scaleY, lightX, lightY, glow, globalAlpha, lightBlend);
        addDyn(builder, matrix, -inSize,   0, -gap, gap, scaleX, scaleY, lightX, lightY, glow, globalAlpha, lightBlend);
        addDyn(builder, matrix, -outSize,  0, -gap, gap, scaleX, scaleY, lightX, lightY, glow, globalAlpha, lightBlend);

        addDyn(builder, matrix, -outSize,   0, -gap, -gap, scaleX, scaleY, lightX, lightY, glow, globalAlpha, lightBlend);
        addDyn(builder, matrix, -inSize,    0, -gap, -gap, scaleX, scaleY, lightX, lightY, glow, globalAlpha, lightBlend);
        addDyn(builder, matrix,   0,  -inSize, -gap, -gap, scaleX, scaleY, lightX, lightY, glow, globalAlpha, lightBlend);
        addDyn(builder, matrix,   0, -outSize, -gap, -gap, scaleX, scaleY, lightX, lightY, glow, globalAlpha, lightBlend);

        // 3. 中央量子呼吸核心
        float coreSize = lerp(lerp(2.0f, 1.5f, phase1), 1.2f, phase2);
        float coreBaseA = lerp(lerp(1.0f, 0.7f, phase1), 0.9f, phase2);
        int coreA = (int) (baseAlpha * coreBaseA * (0.4f + 0.6f * breath));
        int coreColor = (coreA << 24) | 0xFFFFFF;

        // 远距离时核心稍微缩小，融入实心菱形中
        addSolid(builder, matrix,  0, -coreSize, 0, 0, scaleX, scaleY, coreColor);
        addSolid(builder, matrix, -coreSize,  0, 0, 0, scaleX, scaleY, coreColor);
        addSolid(builder, matrix,  0,  coreSize, 0, 0, scaleX, scaleY, coreColor);
        addSolid(builder, matrix,  coreSize,  0, 0, 0, scaleX, scaleY, coreColor);

        BufferUploader.drawWithShader(builder.end());
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // 新增传入 lightBlend
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

    // 利用 lightBlend 控制阴影和高光的衰减
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

        if (dot > 0.1f) {
            // 光照受 lightBlend 限制，远距离归 0
            float glow = ((dot - 0.1f) / 0.9f) * lightBlend;
            outR = (int)(r + (255 - r) * glow);
            outG = (int)(g + (255 - g) * glow);
            outB = (int)(b + (255 - b) * glow);
            outA = (int)Math.min(255, baseA + (255 - baseA) * (glow * 0.4f));
        } else if (dot < -0.1f) {
            // 阴影受 lightBlend 限制，远距离归 0
            float shadow = ((-dot - 0.1f) / 0.9f) * lightBlend;
            outA = (int)(baseA * (1.0f - shadow * 0.8f));
        }

        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }
}