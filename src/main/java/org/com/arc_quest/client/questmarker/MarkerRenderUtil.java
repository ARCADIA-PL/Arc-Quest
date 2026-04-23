package org.com.arc_quest.client.questmarker;

import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * 纯代码绘制：菱形、圆形、三角箭头、发光环、线条
 * 全部不依赖纹理
 */
public final class MarkerRenderUtil {

    private MarkerRenderUtil() {}

    /* ────────── 颜色解包 ────────── */
    private static float a(int c) { return ((c >> 24) & 0xFF) / 255.0f; }
    private static float r(int c) { return ((c >> 16) & 0xFF) / 255.0f; }
    private static float g(int c) { return ((c >>  8) & 0xFF) / 255.0f; }
    private static float b(int c) { return ( c        & 0xFF) / 255.0f; }

    /* ────────── 菱形标记（任务点） ────────── */
    public static void drawDiamond(GuiGraphics gui, float cx, float cy, float size, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f mat = gui.pose().last().pose();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);

        float fa = a(color), fr = r(color), fg = g(color), fb = b(color);

        buf.vertex(mat, cx,        cy - size, 0).color(fr, fg, fb, fa).endVertex(); // 上
        buf.vertex(mat, cx + size, cy,        0).color(fr, fg, fb, fa).endVertex(); // 右
        buf.vertex(mat, cx,        cy + size, 0).color(fr, fg, fb, fa).endVertex(); // 下
        buf.vertex(mat, cx - size, cy,        0).color(fr, fg, fb, fa).endVertex(); // 左

        BufferUploader.drawWithShader(buf.end());
        RenderSystem.disableBlend();
    }

    /* ────────── 菱形轮廓（空心） ────────── */
    public static void drawDiamondOutline(GuiGraphics gui, float cx, float cy,
                                          float size, float lineWidth, int color) {
        float inner = size - lineWidth;
        drawDiamond(gui, cx, cy, size, color);
        // 用背景色覆盖内部 → 形成轮廓效果
        drawDiamond(gui, cx, cy, inner, 0xCC000000);
    }

    /* ────────── 圆形（NPC/地点标记） ────────── */
    public static void drawCircle(GuiGraphics gui, float cx, float cy,
                                  float radius, int segments, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f mat = gui.pose().last().pose();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);

        float fa = a(color), fr = r(color), fg = g(color), fb = b(color);

        // 中心点
        buf.vertex(mat, cx, cy, 0).color(fr, fg, fb, fa).endVertex();

        for (int i = 0; i <= segments; i++) {
            float angle = (float) (2.0 * Math.PI * i / segments);
            float px = cx + (float) Math.cos(angle) * radius;
            float py = cy + (float) Math.sin(angle) * radius;
            buf.vertex(mat, px, py, 0).color(fr, fg, fb, fa).endVertex();
        }

        BufferUploader.drawWithShader(buf.end());
        RenderSystem.disableBlend();
    }

    /* ────────── 圆环（发光环效果） ────────── */
    public static void drawRing(GuiGraphics gui, float cx, float cy,
                                float innerRadius, float outerRadius,
                                int segments, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f mat = gui.pose().last().pose();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        float fa = a(color), fr = r(color), fg = g(color), fb = b(color);

        for (int i = 0; i <= segments; i++) {
            float angle = (float) (2.0 * Math.PI * i / segments);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            buf.vertex(mat, cx + cos * outerRadius, cy + sin * outerRadius, 0)
                    .color(fr, fg, fb, fa * 0.3f).endVertex();
            buf.vertex(mat, cx + cos * innerRadius, cy + sin * innerRadius, 0)
                    .color(fr, fg, fb, fa).endVertex();
        }

        BufferUploader.drawWithShader(buf.end());
        RenderSystem.disableBlend();
    }

    /* ────────── 三角箭头（边缘指示） ────────── */
    /**
     * @param cx,cy  箭头尖端位置
     * @param angle  朝向角度（弧度，0=右，PI/2=下）
     * @param size   箭头大小
     */
    public static void drawArrow(GuiGraphics gui, float cx, float cy,
                                 float angle, float size, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f mat = gui.pose().last().pose();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        float fa = a(color), fr = r(color), fg = g(color), fb = b(color);

        // 箭头三个顶点
        // 尖端
        float tipX = cx + (float) Math.cos(angle) * size;
        float tipY = cy + (float) Math.sin(angle) * size;

        // 两个翼
        float wingAngle1 = angle + (float) Math.toRadians(150);
        float wingAngle2 = angle - (float) Math.toRadians(150);
        float wingLen = size * 0.7f;

        float w1x = cx + (float) Math.cos(wingAngle1) * wingLen;
        float w1y = cy + (float) Math.sin(wingAngle1) * wingLen;
        float w2x = cx + (float) Math.cos(wingAngle2) * wingLen;
        float w2y = cy + (float) Math.sin(wingAngle2) * wingLen;

        buf.vertex(mat, tipX, tipY, 0).color(fr, fg, fb, fa).endVertex();
        buf.vertex(mat, w1x,  w1y,  0).color(fr, fg, fb, fa * 0.6f).endVertex();
        buf.vertex(mat, w2x,  w2y,  0).color(fr, fg, fb, fa * 0.6f).endVertex();

        BufferUploader.drawWithShader(buf.end());
        RenderSystem.disableBlend();
    }

    /* ────────── 脉冲动画环 ────────── */
    public static void drawPulseRing(GuiGraphics gui, float cx, float cy,
                                     float baseRadius, float time,
                                     float speed, int color) {
        // 0~1循环
        float phase = (time * speed) % 1.0f;
        float radius = baseRadius + phase * baseRadius * 0.8f;
        float alpha = (1.0f - phase) * 0.6f;

        int pulseColor = (((int)(alpha * 255)) << 24)
                | (color & 0x00FFFFFF);

        drawRing(gui, cx, cy, radius - 1.0f, radius + 1.0f, 32, pulseColor);
    }

    /* ────────── 赛博风线条边框 ────────── */
    public static void drawCyberneticEdge(GuiGraphics gui, float cx, float cy,
                                          float width, float height,
                                          float cornerCut, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f mat = gui.pose().last().pose();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        float fa = a(color), fr = r(color), fg = g(color), fb = b(color);
        float hw = width / 2, hh = height / 2;
        float cc = cornerCut;

        // 八边形路径（左上开始顺时针）
        buf.vertex(mat, cx - hw + cc, cy - hh,      0).color(fr, fg, fb, fa).endVertex();
        buf.vertex(mat, cx + hw - cc, cy - hh,      0).color(fr, fg, fb, fa).endVertex();
        buf.vertex(mat, cx + hw,      cy - hh + cc, 0).color(fr, fg, fb, fa).endVertex();
        buf.vertex(mat, cx + hw,      cy + hh - cc, 0).color(fr, fg, fb, fa).endVertex();
        buf.vertex(mat, cx + hw - cc, cy + hh,      0).color(fr, fg, fb, fa).endVertex();
        buf.vertex(mat, cx - hw + cc, cy + hh,      0).color(fr, fg, fb, fa).endVertex();
        buf.vertex(mat, cx - hw,      cy + hh - cc, 0).color(fr, fg, fb, fa).endVertex();
        buf.vertex(mat, cx - hw,      cy - hh + cc, 0).color(fr, fg, fb, fa).endVertex();
        buf.vertex(mat, cx - hw + cc, cy - hh,      0).color(fr, fg, fb, fa).endVertex(); // 闭合

        BufferUploader.drawWithShader(buf.end());
        RenderSystem.disableBlend();
    }
}