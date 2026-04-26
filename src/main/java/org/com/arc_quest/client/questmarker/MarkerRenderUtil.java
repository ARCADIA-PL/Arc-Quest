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
 * 纯代码绘制机能风视觉组件（极简修饰版）。
 */
public final class MarkerRenderUtil {

    private MarkerRenderUtil() {}

    private static float a(int c) { return ((c >> 24) & 0xFF) / 255.0f; }
    private static float r(int c) { return ((c >> 16) & 0xFF) / 255.0f; }
    private static float g(int c) { return ((c >>  8) & 0xFF) / 255.0f; }
    private static float b(int c) { return ( c        & 0xFF) / 255.0f; }

    public static void drawRhombus(GuiGraphics gui, int coreColor, int brightColor, int bgColor) {
        // 暗色底衬
        gui.fill(-6, -6, 6, 6, bgColor);
        // 四条锐利细线边框
        gui.fill(-6, -6, 6, -5, brightColor);
        gui.fill(-6, 5, 6, 6, brightColor);
        gui.fill(-6, -5, -5, 5, brightColor);
        gui.fill(5, -5, 6, 5, brightColor);
        // 核心跳动点
        gui.fill(-3, -3, 3, 3, coreColor);
    }

    public static void drawMinimalPointer(GuiGraphics gui, int color, int brightColor, int bgColor) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        Matrix4f pose = gui.pose().last().pose();

        float ba = a(bgColor);
        float ca = a(color), cr = r(color), cg = g(color), cb = b(color);
        float hA = a(brightColor), hR = r(brightColor), hG = g(brightColor), hB = b(brightColor);

        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(pose, 0, -7.5f, 0).color(0f, 0f, 0f, ba).endVertex(); // 顶尖
        buf.vertex(pose, -4.5f, 5, 0).color(0f, 0f, 0f, ba).endVertex(); // 左下角
        buf.vertex(pose, 0, 1.5f, 0).color(0f, 0f, 0f, ba).endVertex();  // 尾部内凹

        buf.vertex(pose, 0, -7.5f, 0).color(0f, 0f, 0f, ba).endVertex(); // 顶尖
        buf.vertex(pose, 0, 1.5f, 0).color(0f, 0f, 0f, ba).endVertex();  // 尾部内凹
        buf.vertex(pose, 4.5f, 5, 0).color(0f, 0f, 0f, ba).endVertex();  // 右下角
        BufferUploader.drawWithShader(buf.end());

        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(pose, 0, -6, 0).color(hR, hG, hB, hA).endVertex();  // 尖端使用亮白色
        buf.vertex(pose, -3.5f, 4, 0).color(cr, cg, cb, ca).endVertex(); // 左翼
        buf.vertex(pose, 0, 1, 0).color(cr, cg, cb, ca).endVertex();     // 尾部内凹

        buf.vertex(pose, 0, -6, 0).color(hR, hG, hB, hA).endVertex();  // 尖端使用亮白色
        buf.vertex(pose, 0, 1, 0).color(cr, cg, cb, ca).endVertex();     // 尾部内凹
        buf.vertex(pose, 3.5f, 4, 0).color(cr, cg, cb, ca).endVertex();  // 右翼
        BufferUploader.drawWithShader(buf.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}