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

/**
 * 视野内：机能高光全息菱形 - 顶点着色版
 * 彻底抛弃贴片拼凑，采用原生 GPU 顶点着色。
 * 光效直接在厚实的边框内部流淌：从顶端的白热化过曝，渐变过渡至主题色，再向下消散！
 */
public final class MarkerRhombusRenderer {

    private MarkerRhombusRenderer() {}

    public static void draw(GuiGraphics gui, int accentColor, float breath) {
        int baseAlpha = (accentColor >> 24) & 0xFF;
        int r = (accentColor >> 16) & 0xFF;
        int g = (accentColor >> 8) & 0xFF;
        int b = accentColor & 0xFF;

        // 提取渐变节点 Alpha
        int glowA = 255;                           // 顶部纯白高光
        int fadeA = (int) (baseAlpha * 0.3f);      // 底部消散透明度
        int glassA = (int) (baseAlpha * 0.2f);     // 玻璃舱体上半部
        int glassFadeA = (int) (baseAlpha * 0.05f);// 玻璃舱体下半部

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        Matrix4f matrix = gui.pose().last().pose();

        // 开启四边形渲染
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // ==========================================================
        // 1. 全息玻璃内舱 (单体渐变菱形)
        // 同样具备光照方向感：上亮下暗
        addVertex(builder, matrix,  0, -7, r, g, b, glassA);     // 顶部
        addVertex(builder, matrix, -7,  0, r, g, b, glassA);     // 左部
        addVertex(builder, matrix,  0,  7, r, g, b, glassFadeA); // 底部
        addVertex(builder, matrix,  7,  0, r, g, b, glassA);     // 右部

        // ==========================================================
        // 2. 外部主发光装甲带 (由 4 个梯形构建而成的空心菱形带)
        // 顶点颜色将自动在这些几何体内形成极致平滑的自发光渐变！

        // 边 1：右上段 (从纯白 -> 主题色)
        addVertex(builder, matrix,  0, -10, 255, 255, 255, glowA);     // 外顶端 (白热)
        addVertex(builder, matrix,  0,  -7, 255, 255, 255, glowA);     // 内顶端 (白热)
        addVertex(builder, matrix,  7,   0,   r,   g,   b, baseAlpha); // 内右端 (主题色)
        addVertex(builder, matrix, 10,   0,   r,   g,   b, baseAlpha); // 外右端 (主题色)

        // 边 2：右下段 (从主题色 -> 半透消散)
        addVertex(builder, matrix, 10,  0, r, g, b, baseAlpha);
        addVertex(builder, matrix,  7,  0, r, g, b, baseAlpha);
        addVertex(builder, matrix,  0,  7, r, g, b, fadeA);
        addVertex(builder, matrix,  0, 10, r, g, b, fadeA);

        // 边 3：左下段 (从半透消散 -> 主题色)
        addVertex(builder, matrix,   0, 10, r, g, b, fadeA);
        addVertex(builder, matrix,   0,  7, r, g, b, fadeA);
        addVertex(builder, matrix,  -7,  0, r, g, b, baseAlpha);
        addVertex(builder, matrix, -10,  0, r, g, b, baseAlpha);

        // 边 4：左上段 (从主题色 -> 纯白)
        addVertex(builder, matrix, -10,   0,   r,   g,   b, baseAlpha);
        addVertex(builder, matrix,  -7,   0,   r,   g,   b, baseAlpha);
        addVertex(builder, matrix,   0,  -7, 255, 255, 255, glowA);
        addVertex(builder, matrix,   0, -10, 255, 255, 255, glowA);

        // ==========================================================
        // 3. 中央量子呼吸核心 (微型纯白菱形)
        int coreA = (int) (255 * (0.4f + 0.6f * breath));
        addVertex(builder, matrix,  0, -2, 255, 255, 255, coreA);
        addVertex(builder, matrix, -2,  0, 255, 255, 255, coreA);
        addVertex(builder, matrix,  0,  2, 255, 255, 255, coreA);
        addVertex(builder, matrix,  2,  0, 255, 255, 255, coreA);

        // 提交绘制
        BufferUploader.drawWithShader(builder.end());
    }

    private static void addVertex(BufferBuilder b, Matrix4f m, float x, float y, int red, int green, int blue, int alpha) {
        b.vertex(m, x, y, 0).color(red, green, blue, alpha).endVertex();
    }
}