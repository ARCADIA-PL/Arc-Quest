package org.com.arc_quest.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * 共享渲染工具集。
 */
public final class QuestRenderUtil {

    private QuestRenderUtil() {
    }

    public static void drawBatchRects(GuiGraphics g, int[] rects, int rectCount) {
        if (rectCount <= 0) return;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        Matrix4f pose = g.pose().last().pose();

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

            buffer.vertex(pose, x, y + h, 0).color(r, gr, b, a).endVertex();
            buffer.vertex(pose, x + w, y + h, 0).color(r, gr, b, a).endVertex();
            buffer.vertex(pose, x + w, y, 0).color(r, gr, b, a).endVertex();
            buffer.vertex(pose, x, y, 0).color(r, gr, b, a).endVertex();
        }

        tesselator.end();
    }

    public static void drawGlassPanel(GuiGraphics g, int x, int y, int w, int h,
                                      int bgColor, int bgAlpha,
                                      int accentColor, int accentAlpha, int accentWidth) {
        int[] rects = new int[10];
        rects[0] = x; rects[1] = y; rects[2] = w; rects[3] = h; rects[4] = (bgAlpha << 24) | (bgColor & 0x00FFFFFF);
        rects[5] = x; rects[6] = y; rects[7] = accentWidth; rects[8] = h; rects[9] = (accentAlpha << 24) | (accentColor & 0x00FFFFFF);
        drawBatchRects(g, rects, 2);
    }

    public static void drawToastPanel(GuiGraphics g, int x, int y, int w, int h,
                                      int bgColor, int bgAlpha,
                                      int accentColor, int accentAlpha, int accentWidth,
                                      int lineAlpha) {
        int rectCount = lineAlpha > 0 ? 3 : 2;
        int[] rects = new int[rectCount * 5];

        rects[0] = x; rects[1] = y; rects[2] = w; rects[3] = h; rects[4] = (bgAlpha << 24) | (bgColor & 0x00FFFFFF);
        rects[5] = x; rects[6] = y; rects[7] = accentWidth; rects[8] = h; rects[9] = (accentAlpha << 24) | (accentColor & 0x00FFFFFF);

        if (lineAlpha > 0) {
            rects[10] = x + accentWidth; rects[11] = y + h - 1; rects[12] = w - accentWidth; rects[13] = 1; rects[14] = (lineAlpha << 24) | (accentColor & 0x00FFFFFF);
        }

        drawBatchRects(g, rects, rectCount);
    }

    public static void drawDualText(GuiGraphics g, Font font, float x, float y,
                                    String subtitle, String title,
                                    int subtitleColor, int titleColor, float alpha) {
        int subA = (int) (((subtitleColor >> 24) & 0xFF) * alpha);
        int titleA = (int) (((titleColor >> 24) & 0xFF) * alpha);

        if (subA < 5 && titleA < 5) return;

        if (subA > 5) {
            g.pose().pushPose();
            g.pose().translate((int) x, (int) y, 0);
            g.pose().scale(0.7f, 0.7f, 1f);
            g.drawString(font, subtitle, 0, 0, QuestAnimUtil.withAlpha(subtitleColor & 0x00FFFFFF, subA), true);
            g.pose().popPose();
        }

        if (titleA > 5) {
            g.pose().pushPose();
            g.pose().translate((int) x, (int) (y + 10), 0);
            g.pose().scale(1.0f, 1.0f, 1f);
            g.drawString(font, title, 0, 0, QuestAnimUtil.withAlpha(titleColor & 0x00FFFFFF, titleA), true);
            g.pose().popPose();
        }
    }

    public static void drawTextWithLine(GuiGraphics g, Font font, float x, float y,
                                        String text, int color,
                                        float lineWidth, float lineYOffset) {
        int alpha = (color >> 24) & 0xFF;
        if (alpha < 5) return;

        g.pose().pushPose();
        g.pose().translate((int) x, (int) y, 0);
        g.pose().scale(1.0f, 1.0f, 1f);
        g.drawString(font, text, 0, 0, color, true);
        g.pose().popPose();

        if (lineWidth > 2) {
            int lineY = (int) (y + lineYOffset);
            int lineColor = QuestAnimUtil.withAlpha(color & 0x00FFFFFF, alpha);
            g.fill((int) x, lineY, (int) (x + lineWidth), lineY + 1, lineColor);

            if (lineWidth > 10) {
                g.fill((int) (x + lineWidth), lineY - 1,
                        (int) (x + lineWidth) + 3, lineY + 2, lineColor);
            }
        }
    }

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

    public static void enableBlendNoDepth() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
    }

    public static void disableBlendWithDepth() {
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}