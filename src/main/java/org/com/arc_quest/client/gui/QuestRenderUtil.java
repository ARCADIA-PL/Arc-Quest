package org.com.arc_quest.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;

/**
 * 共享渲染工具集，提取自各个HUD组件的通用绘制逻辑。
 */
public final class QuestRenderUtil {

    private QuestRenderUtil() {
    }

    // ═══════════════════════════════════════════════════════
    //  批量矩形绘制（顶点缓冲优化）
    // ═══════════════════════════════════════════════════════

    /**
     * 使用顶点缓冲批量绘制多个矩形（比GuiGraphics.fill更高效）。
     *
     * @param g        图形上下文
     * @param rects    矩形数组 [x, y, w, h, color] 每组5个int
     * @param rectCount 矩形数量
     */
    public static void drawBatchRects(GuiGraphics g, int[] rects, int rectCount) {
        if (rectCount <= 0) return;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < rectCount; i++) {
            int idx = i * 5;
            int x = rects[idx];
            int y = rects[idx + 1];
            int w = rects[idx + 2];
            int h = rects[idx + 3];
            int color = rects[idx + 4];

            float a = (color >> 24 & 255) / 255.0F;
            float r = (color >> 16 & 255) / 255.0F;
            float gr = (color >> 8 & 255) / 255.0F;
            float b = (color & 255) / 255.0F;

            buffer.vertex(x, y + h, 0).color(r, gr, b, a).endVertex();
            buffer.vertex(x + w, y + h, 0).color(r, gr, b, a).endVertex();
            buffer.vertex(x + w, y, 0).color(r, gr, b, a).endVertex();
            buffer.vertex(x, y, 0).color(r, gr, b, a).endVertex();
        }

        tesselator.end();
    }

    // ═══════════════════════════════════════════════════════
    //  面板绘制
    // ═══════════════════════════════════════════════════════

    /**
     * 绘制带左侧主题色条的面板（磨砂玻璃风格）- 优化版。
     *
     * @param g            图形上下文
     * @param x            X坐标
     * @param y            Y坐标
     * @param w            宽度
     * @param h            高度
     * @param bgColor      背景颜色（RGB）
     * @param bgAlpha      背景透明度（0-255）
     * @param accentColor  强调色（RGB）
     * @param accentAlpha  强调色透明度（0-255）
     * @param accentWidth  强调条宽度
     */
    public static void drawGlassPanel(GuiGraphics g, int x, int y, int w, int h,
                                      int bgColor, int bgAlpha,
                                      int accentColor, int accentAlpha, int accentWidth) {
        // 使用批量绘制（2个矩形：背景+强调条）
        int[] rects = new int[10]; // 2个矩形 * 5个参数
        
        // 背景矩形
        rects[0] = x;
        rects[1] = y;
        rects[2] = w;
        rects[3] = h;
        rects[4] = (bgAlpha << 24) | (bgColor & 0x00FFFFFF);
        
        // 强调条矩形
        rects[5] = x;
        rects[6] = y;
        rects[7] = accentWidth;
        rects[8] = h;
        rects[9] = (accentAlpha << 24) | (accentColor & 0x00FFFFFF);
        
        drawBatchRects(g, rects, 2);
    }

    /**
     * 绘制带装饰线的Toast面板 - 优化版。
     *
     * @param g              图形上下文
     * @param x              X坐标
     * @param y              Y坐标
     * @param w              宽度
     * @param h              高度
     * @param bgColor        背景颜色（RGB）
     * @param bgAlpha        背景透明度
     * @param accentColor    强调色（RGB）
     * @param accentAlpha    强调色透明度
     * @param accentWidth    强调条宽度
     * @param lineAlpha      底部装饰线透明度
     */
    public static void drawToastPanel(GuiGraphics g, int x, int y, int w, int h,
                                      int bgColor, int bgAlpha,
                                      int accentColor, int accentAlpha, int accentWidth,
                                      int lineAlpha) {
        // 使用批量绘制（3个矩形：背景+强调条+装饰线）
        int rectCount = lineAlpha > 0 ? 3 : 2;
        int[] rects = new int[rectCount * 5];
        
        // 背景矩形
        rects[0] = x;
        rects[1] = y;
        rects[2] = w;
        rects[3] = h;
        rects[4] = (bgAlpha << 24) | (bgColor & 0x00FFFFFF);
        
        // 强调条矩形
        rects[5] = x;
        rects[6] = y;
        rects[7] = accentWidth;
        rects[8] = h;
        rects[9] = (accentAlpha << 24) | (accentColor & 0x00FFFFFF);
        
        // 底部装饰线矩形
        if (lineAlpha > 0) {
            rects[10] = x + accentWidth;
            rects[11] = y + h - 1;
            rects[12] = w - accentWidth;
            rects[13] = 1;
            rects[14] = (lineAlpha << 24) | (accentColor & 0x00FFFFFF);
        }
        
        drawBatchRects(g, rects, rectCount);
    }

    // ═══════════════════════════════════════════════════════
    //  文本渲染
    // ═══════════════════════════════════════════════════════

    /**
     * 绘制带缩放的双行文本（标题+副标题）。
     *
     * @param g             图形上下文
     * @param font          字体对象
     * @param x             基础X坐标
     * @param y             基础Y坐标
     * @param subtitle      副标题文本
     * @param title         主标题文本
     * @param subtitleColor 副标题颜色（ARGB）
     * @param titleColor    主标题颜色（ARGB）
     * @param alpha         整体透明度系数
     */
    public static void drawDualText(GuiGraphics g, Font font, float x, float y,
                                    String subtitle, String title,
                                    int subtitleColor, int titleColor, float alpha) {
        int subA = (int) (((subtitleColor >> 24) & 0xFF) * alpha);
        int titleA = (int) (((titleColor >> 24) & 0xFF) * alpha);
        
        if (subA < 5 && titleA < 5) return;

        // 副标题（0.7x缩放）
        if (subA > 5) {
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(0.7f, 0.7f, 1f);
            g.drawString(font, subtitle, 0, 0, QuestAnimUtil.withAlpha(subtitleColor & 0x00FFFFFF, subA), true);
            g.pose().popPose();
        }

        // 主标题（1.0x缩放）
        if (titleA > 5) {
            g.pose().pushPose();
            g.pose().translate(x, y + 10, 0);
            g.pose().scale(1.0f, 1.0f, 1f);
            g.drawString(font, title, 0, 0, QuestAnimUtil.withAlpha(titleColor & 0x00FFFFFF, titleA), true);
            g.pose().popPose();
        }
    }

    /**
     * 绘制带装饰线的文本。
     *
     * @param g           图形上下文
     * @param font        字体对象
     * @param x           X坐标
     * @param y           Y坐标
     * @param text        文本内容
     * @param color       文本颜色（ARGB）
     * @param lineWidth   装饰线宽度
     * @param lineYOffset 装饰线Y偏移
     */
    public static void drawTextWithLine(GuiGraphics g, Font font, float x, float y,
                                        String text, int color,
                                        float lineWidth, float lineYOffset) {
        int alpha = (color >> 24) & 0xFF;
        if (alpha < 5) return;

        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(1.0f, 1.0f, 1f);
        g.drawString(font, text, 0, 0, color, true);
        g.pose().popPose();

        // 装饰线
        if (lineWidth > 2) {
            float lineY = y + lineYOffset;
            int lineColor = QuestAnimUtil.withAlpha(color & 0x00FFFFFF, alpha);
            g.fill((int) x, (int) lineY, (int) (x + lineWidth), (int) lineY + 1, lineColor);
            
            // 末端高亮
            if (lineWidth > 10) {
                g.fill((int) (x + lineWidth), (int) lineY - 1, 
                       (int) (x + lineWidth) + 3, (int) lineY + 2, lineColor);
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Scissor 辅助
    // ═══════════════════════════════════════════════════════

    /**
     * 计算并应用渐入/渐出的Scissor裁剪区域。
     *
     * @param g              图形上下文
     * @param baseX          基础X坐标
     * @param baseY          基础Y坐标
     * @param width          总宽度
     * @param height         总高度
     * @param revealProgress 渐入进度（0-1）
     * @param wipeProgress   渐出进度（0-1）
     * @param isEntering     是否处于渐入阶段
     * @param isExiting      是否处于渐出阶段
     */
    public static void applyDynamicScissor(GuiGraphics g, int baseX, int baseY,
                                           int width, int height,
                                           float revealProgress, float wipeProgress,
                                           boolean isEntering, boolean isExiting) {
        int scLeft = baseX - 20;
        int scRight;
        
        if (isEntering) {
            scRight = baseX + (int) (width * revealProgress);
        } else if (isExiting) {
            scRight = baseX + (int) (width * (1f - wipeProgress));
        } else {
            scRight = baseX + width + 20;
        }
        
        g.enableScissor(scLeft, baseY - 10, scRight, baseY + height + 20);
    }

    // ═══════════════════════════════════════════════════════
    //  RenderSystem 封装
    // ═══════════════════════════════════════════════════════

    /**
     * 启用标准混合模式并禁用深度测试。
     */
    public static void enableBlendNoDepth() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
    }

    /**
     * 禁用混合并启用深度测试。
     */
    public static void disableBlendWithDepth() {
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
