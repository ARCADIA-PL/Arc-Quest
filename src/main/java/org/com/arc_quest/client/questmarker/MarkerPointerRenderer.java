package org.com.arc_quest.client.questmarker;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 视野外：HUD 全息引导括号 - 材质自发光版
 * 抛弃了额外的白色修饰线。
 * 直接将 3px 宽的引导带本身作为发光体，顶端白热化，向后流动消散！
 */
public final class MarkerPointerRenderer {

    private MarkerPointerRenderer() {}

    public static void draw(GuiGraphics gui, int accentColor) {
        int baseAlpha = (accentColor >> 24) & 0xFF;
        int rgb = accentColor & 0xFFFFFF;

        // ==========================================================
        // 色彩预设：
        // solidColor: 主题色实心
        // fadeColor:  主题色完全透明 (用于尾部消散)
        // glowColor:  纯白满高光 (用于前置撞风面过曝)
        // ==========================================================
        int solidColor = rgb | (baseAlpha << 24);
        int fadeColor  = rgb | 0;
        int glowColor  = 0xFFFFFF | (255 << 24); // 极度锐利的纯白高温

        // 此时的 PoseStack 已被旋转，负 Y 轴 (-Y) 即为目标所在方向。

        // 1. 前置雷达撞风盖板 (横向带)
        // 效果：在这个 3px 的带子内部，从顶部的纯白直接平滑渐变到下方的主题色
        gui.fillGradient(-8, -12, 8, -9, glowColor, solidColor);

        // 2. 侧翼消散流线 (纵向带)
        // 效果：无缝衔接上面盖板底部的主题色，并向后方平滑消散为完全透明
        gui.fillGradient(-8, -9, -6, 0, solidColor, fadeColor); // 左侧流线 (2px宽)
        gui.fillGradient(6, -9, 8, 0, solidColor, fadeColor);   // 右侧流线 (2px宽)

        // 3. 中央微型机能锁扣
        // 提供视觉重心的锚点，颜色为偏暗的主题色
        int dotAlpha = (int) (baseAlpha * 0.8f);
        gui.fill(-2, -5, 2, -3, rgb | (dotAlpha << 24));
    }
}