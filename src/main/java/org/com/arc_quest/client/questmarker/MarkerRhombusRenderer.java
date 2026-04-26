package org.com.arc_quest.client.questmarker;

import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 视野内：对称、通透、极简的全息菱形。
 * 抛弃了不对称的高光(白帽子)，回归纯粹的几何美学与极佳的透明度。
 */
public final class MarkerRhombusRenderer {

    private MarkerRhombusRenderer() {}

    public static void draw(GuiGraphics gui, int accentColor, float breath) {
        int baseAlpha = (accentColor >> 24) & 0xFF;
        int rgb = accentColor & 0xFFFFFF;

        gui.pose().pushPose();
        // 旋转45度，后续的矩形绘制将自动变为完美的菱形
        gui.pose().mulPose(Axis.ZP.rotationDegrees(45f));

        // 1. 极细的外层半透明全息边框 (均匀对称，没有突兀的高光)
        int borderAlpha = (int) (baseAlpha * 0.5f);
        int borderColor = rgb | (borderAlpha << 24);
        gui.fill(-5, -5,  5, -4, borderColor); // 上
        gui.fill(-5,  4,  5,  5, borderColor); // 下
        gui.fill(-5, -4, -4,  4, borderColor); // 左
        gui.fill( 4, -4,  5,  4, borderColor); // 右

        // 2. 内部的通透玻璃质感 (极低的透明度，保证不遮挡游戏画面)
        int glassAlpha = (int) (baseAlpha * 0.15f);
        int glassColor = rgb | (glassAlpha << 24);
        gui.fill(-4, -4, 4, 4, glassColor);

        // 3. 悬浮的机能核心 (随时间平滑呼吸)
        // 核心底座 (主题色)
        int coreBaseAlpha = (int) (baseAlpha * (0.4f + 0.3f * breath));
        gui.fill(-2, -2, 2, 2, rgb | (coreBaseAlpha << 24));

        // 核心点亮 (最中心的 2x2 纯白极点，克制且高级)
        int coreWhiteAlpha = (int) (255 * (0.3f + 0.5f * breath));
        gui.fill(-1, -1, 1, 1, 0xFFFFFF | (coreWhiteAlpha << 24));

        gui.pose().popPose();
    }
}