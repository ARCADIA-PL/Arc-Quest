package org.arcadia.arc_quest.client.hud.questmarker;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * 现代平滑机能风视觉组件（Vector / Smooth 版）。
 * 彻底抛弃像素感，采用矩阵旋转与纯矢量三角形绘制，提供极致平滑的锐利 UI。
 */
public final class MarkerRenderUtil {

    private MarkerRenderUtil() {
    }

    /**
     * 平滑暗色机能菱形 (包含柔和的呼吸核心)
     */
    public static void drawSmoothRhombus(GuiGraphics gui, int accentColor, int darkEdge, int darkFill, float breath) {
        gui.pose().pushPose();
        // 将正方形旋转45度形成完美的平滑菱形
        gui.pose().mulPose(Axis.ZP.rotationDegrees(45f));

        // 1. 外部装甲边框 (深空灰)
        gui.fill(-6, -6, 6, 6, darkEdge);

        // 2. 主体内衬底板 (半透明黑灰)
        gui.fill(-5, -5, 5, 5, darkFill);

        // 3. 核心机能点缀
        int alpha = (accentColor >> 24) & 0xFF;
        int rgb = accentColor & 0x00FFFFFF;

        // --- 外层：柔和呼吸光晕 (消除突兀感) ---
        int haloAlpha = (int) (alpha * 0.3f * breath);
        gui.fill(-4, -4, 4, 4, (haloAlpha << 24) | rgb);

        // --- 内层：小巧的高亮核心 (旋转后呈现完美的锐利小菱形) ---
        int coreAlpha = (int) (alpha * (0.6f + 0.4f * breath));
        gui.fill(-2, -2, 2, 2, (coreAlpha << 24) | rgb);

        gui.pose().popPose();
    }

    /**
     * 极度锐利的矢量 V 型机能游标 (Chevron)
     */
    public static void drawVectorPointer(GuiGraphics gui, int accentColor, int darkEdge, int darkFill) {
        Matrix4f mat = gui.pose().last().pose();

        // 开启抗锯齿混合模式
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        // 1. 外部装甲翼轮廓 (深空灰)
        // 参数：尖端Y，翼宽，翼尖Y，内凹底X，内凹底Y
        buildChevron(buffer, mat, 0, -10, 7, 7, 0, 2, darkEdge);

        // 2. 内部机翼填充 (半透黑灰)
        buildChevron(buffer, mat, 0, -8, 5, 5, 0, 1, darkFill);

        // 3. 核心机能亮色点缀 (极小、锋利的彩色指示尖端)
        buildChevron(buffer, mat, 0, -8, 3, -1, 0, -3, accentColor);

        tesselator.end();
        RenderSystem.disableBlend();
    }

    /**
     * 辅助方法：构建平滑的 V 型机翼多边形
     */
    private static void buildChevron(BufferBuilder buffer, Matrix4f mat, float tipX, float tipY, float wingX, float wingY, float innerX, float innerY, int color) {
        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8 & 255) / 255.0F;
        float b = (color & 255) / 255.0F;

        // 左半翼
        buffer.vertex(mat, tipX, tipY, 0).color(r, g, b, a).endVertex();
        buffer.vertex(mat, -wingX, wingY, 0).color(r, g, b, a).endVertex();
        buffer.vertex(mat, innerX, innerY, 0).color(r, g, b, a).endVertex();

        // 右半翼
        buffer.vertex(mat, tipX, tipY, 0).color(r, g, b, a).endVertex();
        buffer.vertex(mat, innerX, innerY, 0).color(r, g, b, a).endVertex();
        buffer.vertex(mat, wingX, wingY, 0).color(r, g, b, a).endVertex();
    }
}