package org.com.arc_quest.client.questmarker;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 视野外：HUD 全息括号 (Tech Bracket)。
 * 彻底抛弃尖锐的三角形，采用机能风的【矩形扫描框边缘】设计，
 * 配合向后消散的渐变拖尾，指示方向的同时保持极高的 UI 逼格。
 */
public final class MarkerPointerRenderer {

    private MarkerPointerRenderer() {}

    public static void draw(GuiGraphics gui, int accentColor) {
        int baseAlpha = (accentColor >> 24) & 0xFF;
        int rgb = accentColor & 0xFFFFFF;

        // 纯色前端
        int solidColor = rgb | (baseAlpha << 24);
        // 消散尾迹 (完全透明)
        int fadeColor = rgb | 0;

        // 此时的 PoseStack 已经被父类旋转过了，负 Y 轴 (-Y) 就是目标所在的方向。

        // 1. 前导主控横线 (宽而扁，像雷达扫描的切面)
        gui.fill(-6, -9, 6, -7, solidColor);

        // 在横线的正中心加一个纯白色的机能识别点 (极小，提供视觉焦点)
        gui.fill(-1, -9, 1, -7, 0xFFFFFF | ((int)(baseAlpha * 0.9f) << 24));

        // 2. 左侧翼轨道 (从前导线向下延伸，并向完全透明丝滑渐变)
        gui.fillGradient(-6, -7, -4, -1, solidColor, fadeColor);

        // 3. 右侧翼轨道 (对称渐变)
        gui.fillGradient(4, -7, 6, -1, solidColor, fadeColor);

        // 4. 后方跟随的微型数据游标 (一个独立悬浮的小方块)
        int dotAlpha = (int) (baseAlpha * 0.6f);
        gui.fill(-1, -3, 1, -1, rgb | (dotAlpha << 24));
    }
}