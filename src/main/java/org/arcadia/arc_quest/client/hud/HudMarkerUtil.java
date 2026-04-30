package org.arcadia.arc_quest.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

public class HudMarkerUtil {

    /**
     * 纯代码绘制：次世代机能风任务标点（类似鸣潮/明日方舟）
     *
     * @param g        GuiGraphics实例
     * @param x        屏幕2D坐标 X
     * @param y        屏幕2D坐标 Y
     * @param color    主题色 (如鸣潮主线是 0xFFFFCC00 金黄色，支线是 0xFF00E5FF 青蓝色)
     * @param distance 距离玩家的距离
     * @param label    可选的文本标签（如 "主线" 或 NPC名字），传 null 则不显示
     */
    public static void renderCyberQuestMarker(GuiGraphics g, float x, float y, int color, float distance, String label) {
        long time = Util.getMillis();

        // 动画变量生成
        // 1. 悬浮动画：Sine曲线上下浮动
        float floatY = (float) Math.sin(time / 300.0) * 4.0f;
        // 2. 呼吸脉冲：0.5 到 1.0 的平滑过渡
        float pulse = (float) Math.sin(time / 200.0) * 0.25f + 0.75f;
        // 3. 旋转角度：外圈的缓慢旋转
        float rotateAngle = (time % 4000) / 4000.0f * 360f;

        PoseStack pose = g.pose();
        pose.pushPose();
        // 将原点移动到目标坐标，并加上悬浮Y轴偏移
        pose.translate(x, y + floatY, 0);

        // 提取颜色通道
        int r = (color >> 16) & 0xFF;
        int gCol = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        // ==========================================
        // 元素1：指向地面的下引线 (Pointer Line)
        // ==========================================
        // 模拟鸣潮标点下方那条垂直插向目标的光线
        fillFloat(pose, -0.5f, 15f, 0.5f, 35f, withAlpha(color, 180));
        // 引线底部的光点（锚定点）
        drawRhombus(pose, 0, 37f, 4f, 4f, withAlpha(color, 255));

        // ==========================================
        // 元素2：外环机能框 (Cyber Brackets)
        // ==========================================
        float bracketSpread = 16f;
        // 左机翼
        fillFloat(pose, -bracketSpread - 6, -1, -bracketSpread, 1, withAlpha(color, 150));
        fillFloat(pose, -bracketSpread - 6, -4, -bracketSpread - 4, -1, withAlpha(color, 150));
        // 右机翼
        fillFloat(pose, bracketSpread, -1, bracketSpread + 6, 1, withAlpha(color, 150));
        fillFloat(pose, bracketSpread + 4, -4, bracketSpread + 6, -1, withAlpha(color, 150));

        // ==========================================
        // 元素3：悬浮菱形核心 (Floating Core)
        // ==========================================
        pose.pushPose();
        // 逆时针呼吸旋转
        pose.mulPose(Axis.ZP.rotationDegrees(-rotateAngle));
        // 核心实心菱形，伴随脉冲呼吸缩放
        float coreSize = 10f * pulse;
        drawRhombus(pose, 0, 0, coreSize, coreSize, withAlpha(color, 255));

        // 外部透明描边菱形（比核心大一点）
        drawRhombus(pose, 0, 0, 18f, 18f, withAlpha(color, 80));
        pose.popPose();

        // ==========================================
        // 元素4：文本信息渲染 (距离 & 标签)
        // ==========================================
        pose.pushPose();
        pose.translate(0, -20, 0); // 在菱形上方显示
        pose.scale(0.75f, 0.75f, 1f); // 缩小字体，增加精致感

        Font font = Minecraft.getInstance().font;
        String distStr = String.format("%.1fm", distance);

        // 绘制距离
        g.drawCenteredString(font, distStr, 0, 0, 0xFFFFFF);

        // 可选：绘制主线/支线标签
        if (label != null) {
            g.drawCenteredString(font, label, 0, -10, color);
        }
        pose.popPose();

        pose.popPose();
    }

    // ==========================================================
    // 底层纯代码几何绘制支持 (基于 BufferBuilder 的直接内存操作)
    // ==========================================================

    private static void drawRhombus(PoseStack poseStack, float x, float y, float width, float height, int color) {
        Matrix4f matrix = poseStack.last().pose();
        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8 & 255) / 255.0F;
        float b = (color & 255) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // 顶点顺序：上 -> 右 -> 下 -> 左 (菱形)
        buffer.vertex(matrix, x, y - height / 2f, 0.0F).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x + width / 2f, y, 0.0F).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x, y + height / 2f, 0.0F).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x - width / 2f, y, 0.0F).color(r, g, b, a).endVertex();

        tesselator.end();
        RenderSystem.disableBlend();
    }

    private static void fillFloat(PoseStack poseStack, float minX, float minY, float maxX, float maxY, int color) {
        Matrix4f matrix = poseStack.last().pose();
        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8 & 255) / 255.0F;
        float b = (color & 255) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        buffer.vertex(matrix, minX, maxY, 0.0F).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, maxY, 0.0F).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, minY, 0.0F).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, minY, 0.0F).color(r, g, b, a).endVertex();

        tesselator.end();
        RenderSystem.disableBlend();
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}