package org.com.arc_quest.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 共享渲染工具集。
 */
public final class HudRenderUtil {

    private HudRenderUtil() {
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
        // 【统一】背景
        g.fill(x, y, x + w, y + h, (bgAlpha << 24) | (bgColor & 0x00FFFFFF));
        
        // 【统一】使用机能风高级晶体侧边栏
        drawCyberneticEdge(g, x, y, h, accentColor, accentAlpha);
    }

    public static void drawToastPanel(GuiGraphics g, int x, int y, int w, int h,
                                      int bgColor, int bgAlpha,
                                      int accentColor, int accentAlpha, int accentWidth,
                                      int lineAlpha) {
        // 【统一】背景
        g.fill(x, y, x + w, y + h, (bgAlpha << 24) | (bgColor & 0x00FFFFFF));
        
        // 【统一】使用机能风高级晶体侧边栏
        drawCyberneticEdge(g, x, y, h, accentColor, accentAlpha);
        
        // 底部线条（保留原有逻辑）
        if (lineAlpha > 0) {
            g.fill(x + accentWidth, y + h - 1, x + w, y + h, 
                   (lineAlpha << 24) | (accentColor & 0x00FFFFFF));
        }
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
            g.drawString(font, subtitle, 0, 0, HudAnimUtil.withAlpha(subtitleColor & 0x00FFFFFF, subA), true);
            g.pose().popPose();
        }

        if (titleA > 5) {
            g.pose().pushPose();
            g.pose().translate((int) x, (int) (y + 10), 0);
            g.pose().scale(1.0f, 1.0f, 1f);
            g.drawString(font, title, 0, 0, HudAnimUtil.withAlpha(titleColor & 0x00FFFFFF, titleA), true);
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
            int lineColor = HudAnimUtil.withAlpha(color & 0x00FFFFFF, alpha);
            g.fill((int) x, lineY, (int) (x + lineWidth), lineY + 1, lineColor);

            if (lineWidth > 10) {
                g.fill((int) (x + lineWidth), lineY - 1,
                        (int) (x + lineWidth) + 3, lineY + 2, lineColor);
            }
        }
    }

    /**
     * 渲染机能风高级晶体侧边栏 (Cybernetic Edge)
     * 拥有顶部极高亮度的白光耀斑和柔和向下的消散渐变。
     *
     * @param g          GuiGraphics
     * @param x          起始 X
     * @param y          起始 Y
     * @param height     总高度
     * @param themeColor 主题色
     * @param alpha      整体透明度 (0-255)
     */
    public static void drawCyberneticEdge(GuiGraphics g, int x, int y, int height, int themeColor, int alpha) {
        if (alpha < 5) return;

        int coreColor = themeColor & 0xFFFFFF;
        int topAlpha = alpha;
        int botAlpha = (int) (alpha * 0.15f);

        int colorTop = coreColor | (topAlpha << 24);
        int colorBot = coreColor | (botAlpha << 24);

        // 主体 3px 宽的深层消散渐变
        g.fillGradient(x, y, x + 3, y + height, colorTop, colorBot);

        // 核心 1px 宽的极度高亮白色耀斑
        int glowAlpha = (int) (topAlpha * 0.8f);
        int colorGlow = 0xFFFFFF | (glowAlpha << 24);
        g.fillGradient(x, y, x + 1, y + (height / 2), colorGlow, colorTop);
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

    /**
     * 文本换行工具方法。
     * <p>
     * 将长文本按指定宽度分割为多行，支持段落分隔（\n）。
     *
     * @param text     原始文本
     * @param maxWidth 最大宽度（像素）
     * @param font     字体对象
     * @return 换行后的文本列表
     */
    public static List<String> wrapText(String text, int maxWidth, Font font) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;

        String[] paragraphs = text.split("\\n");
        for (String paragraph : paragraphs) {
            String[] words = paragraph.split(" ");
            StringBuilder current = new StringBuilder();
            for (String word : words) {
                String test = current.isEmpty() ? word : current + " " + word;
                if (font.width(test) > maxWidth && !current.isEmpty()) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    if (!current.isEmpty()) current.append(" ");
                    current.append(word);
                }
            }
            if (!current.isEmpty()) lines.add(current.toString());
        }
        return lines;
    }

    public static Component resolveTradeFailMessage(@Nullable String errorKey, @Nullable String rawReason) {
        String normalized = normalizeFailureKey(errorKey, rawReason);

        if (containsAny(normalized, "cannot_afford", "insufficient", "shortfall", "not_enough")) {
            return Component.translatable("arc_quest.gui.gacha.btn.insufficient_funds");
        }
        if (containsAny(normalized, "cooldown", "on_cooldown")) {
            return Component.translatable("arc_quest.gui.gacha.btn.cooldown");
        }
        if (containsAny(normalized, "limit", "max_purchase", "max_purchases", "maxed")) {
            return Component.translatable("arc_quest.gui.trade.status.maxed");
        }
        if (containsAny(normalized, "condition", "locked", "requirement", "blocked")) {
            return Component.translatable("arc_quest.gui.trade.status.locked");
        }

        return Component.translatable("arc_quest.gui.trade.error.shop_closed");
    }

    public static Component resolveGachaFailButtonText(@Nullable String failReason, boolean onCooldown, boolean maxed,
                                                       boolean locked, boolean insufficientFunds, @Nullable String cooldownText) {
        if (onCooldown) {
            return cooldownText == null || cooldownText.isEmpty()
                    ? Component.translatable("arc_quest.gui.gacha.btn.cooldown")
                    : Component.translatable("arc_quest.gui.trade.tooltip.cooldown", cooldownText);
        }
        if (maxed) {
            return Component.translatable("arc_quest.gui.trade.btn.empty");
        }
        if (insufficientFunds || containsAny(normalizeFailureKey(failReason, null), "cannot_afford", "insufficient", "shortfall")) {
            return Component.translatable("arc_quest.gui.gacha.btn.insufficient_funds");
        }
        if (locked) {
            return Component.translatable("arc_quest.gui.trade.btn.locked");
        }
        return Component.translatable("arc_quest.gui.gacha.btn.unlock_receptacle");
    }

    private static String normalizeFailureKey(@Nullable String primary, @Nullable String fallback) {
        String key = primary;
        if (key == null || key.isEmpty()) key = fallback;
        if (key == null) return "";
        return key.toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String source, String... needles) {
        for (String needle : needles) {
            if (source.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}