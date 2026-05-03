// file_name: HudRenderUtil.java
package org.arcadia.arc_quest.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 共享渲染工具集 (极致性能优化版)
 */
public final class HudRenderUtil {

    private static final Map<String, Component> TRANSLATION_CACHE = new HashMap<>();

    private HudRenderUtil() {
    }

    public static float getUniversalUiScale(int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return 1.0f;
        float guiScale = (float) mc.getWindow().getGuiScale();
        if (guiScale == 0) guiScale = 1.0f;

        float scale = 3.0f / guiScale;
        float sw = screenWidth / scale;
        float sh = screenHeight / scale;

        if (sw < 480f) {
            scale = screenWidth / 480f;
            sh = screenHeight / scale;
        }
        if (sh < 260f) {
            scale = screenHeight / 260f;
        }
        return scale;
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
            float x = rects[idx];
            float y = rects[idx + 1];
            float w = rects[idx + 2];
            float h = rects[idx + 3];
            int color = rects[idx + 4];

            // 预先解包，避免内联四次调用
            float a = ((color >> 24) & 0xFF) / 255.0F;
            float r = ((color >> 16) & 0xFF) / 255.0F;
            float gr = ((color >> 8) & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;

            buffer.vertex(pose, x, y + h, 0).color(r, gr, b, a).endVertex();
            buffer.vertex(pose, x + w, y + h, 0).color(r, gr, b, a).endVertex();
            buffer.vertex(pose, x + w, y, 0).color(r, gr, b, a).endVertex();
            buffer.vertex(pose, x, y, 0).color(r, gr, b, a).endVertex();
        }

        tesselator.end();
    }

    public static void drawGlassPanel(GuiGraphics g, int x, int y, int w, int h, int bgColor, int bgAlpha, int accentColor, int accentAlpha, int accentWidth) {
        g.fill(x, y, x + w, y + h, (bgAlpha << 24) | (bgColor & 0x00FFFFFF));
        drawCyberneticEdge(g, x, y, h, accentColor, accentAlpha);
    }

    public static void drawToastPanel(GuiGraphics g, int x, int y, int w, int h, int bgColor, int bgAlpha, int accentColor, int accentAlpha, int accentWidth, int lineAlpha) {
        g.fill(x, y, x + w, y + h, (bgAlpha << 24) | (bgColor & 0x00FFFFFF));
        drawCyberneticEdge(g, x, y, h, accentColor, accentAlpha);
        if (lineAlpha > 0) {
            g.fill(x + accentWidth, y + h - 1, x + w, y + h, (lineAlpha << 24) | (accentColor & 0x00FFFFFF));
        }
    }

    public static void drawDualText(GuiGraphics g, Font font, float x, float y, String subtitle, String title, int subtitleColor, int titleColor, float alpha) {
        int subA = (int) (((subtitleColor >> 24) & 0xFF) * alpha);
        int titleA = (int) (((titleColor >> 24) & 0xFF) * alpha);

        if (subA >= 5) {
            // 【优化】副标题缩放，保持矩阵变换
            g.pose().pushPose();
            g.pose().translate((int) x, (int) y, 0);
            g.pose().scale(0.7f, 0.7f, 1f);
            g.drawString(font, subtitle, 0, 0, HudAnimUtil.withAlpha(subtitleColor, subA), true);
            g.pose().popPose();
        }

        if (titleA >= 5) {
            // 【优化】由于 scale 为 1.0f，彻底剔除昂贵的 pushPose/popPose 压栈操作
            g.drawString(font, title, (int) x, (int) (y + 10), HudAnimUtil.withAlpha(titleColor, titleA), true);
        }
    }

    public static void drawTextWithLine(GuiGraphics g, Font font, float x, float y, String text, int color, float lineWidth, float lineYOffset) {
        int alpha = (color >> 24) & 0xFF;
        if (alpha < 5) return;

        // 【优化】剔除无意义的矩阵操作，直接通过坐标位移绘制
        g.drawString(font, text, (int) x, (int) y, color, true);

        if (lineWidth > 2) {
            int lineY = (int) (y + lineYOffset);
            int lineColor = HudAnimUtil.withAlpha(color, alpha);

            // 使用 drawBatchRects 合并绘制
            int[] rects = lineWidth > 10 ? new int[10] : new int[5];
            rects[0] = (int) x; rects[1] = lineY; rects[2] = (int) lineWidth; rects[3] = 1; rects[4] = lineColor;
            if (lineWidth > 10) {
                rects[5] = (int) (x + lineWidth); rects[6] = lineY - 1; rects[7] = 3; rects[8] = 3; rects[9] = lineColor;
                drawBatchRects(g, rects, 2);
            } else {
                drawBatchRects(g, rects, 1);
            }
        }
    }

    public static void drawCyberneticEdge(GuiGraphics g, int x, int y, int height, int themeColor, int alpha) {
        if (alpha < 5) return;
        int coreColor = themeColor & 0xFFFFFF;
        int colorTop = coreColor | (alpha << 24);
        int colorBot = coreColor | ((int) (alpha * 0.15f) << 24);
        g.fillGradient(x, y, x + 3, y + height, colorTop, colorBot);

        int colorGlow = 0xFFFFFF | ((int) (alpha * 0.8f) << 24);
        g.fillGradient(x, y, x + 1, y + (height / 2), colorGlow, colorTop);
    }

    public static void applyDynamicScissor(GuiGraphics g, int baseX, int baseY, int width, int height, float revealProgress, float wipeProgress, boolean isEntering, boolean isExiting) {
        int scLeft = baseX - 20;
        int scRight = isEntering ? baseX + (int) (width * revealProgress) : (isExiting ? baseX + (int) (width * (1f - wipeProgress)) : baseX + width + 20);
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

    public static List<String> wrapText(String text, int maxWidth, Font font) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;

        int len = text.length();
        int lastLineStart = 0;
        int lastSpace = -1;

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);

            if (c == '\n') {
                lines.add(text.substring(lastLineStart, i).trim());
                lastLineStart = i + 1;
                lastSpace = -1;
                continue;
            }

            if (c == ' ') lastSpace = i;

            if (font.width(text.substring(lastLineStart, i + 1)) > maxWidth) {
                if (lastSpace > lastLineStart) {
                    lines.add(text.substring(lastLineStart, lastSpace).trim());
                    lastLineStart = lastSpace + 1;
                } else {
                    lines.add(text.substring(lastLineStart, i));
                    lastLineStart = i;
                }
            }
        }

        if (lastLineStart < len) {
            lines.add(text.substring(lastLineStart, len).trim());
        }

        return lines;
    }

    public static Component resolveTradeFailMessage(@Nullable String errorKey, @Nullable String rawReason) {
        String cacheKey = "fail:" + (errorKey != null ? errorKey : "null") + "|" + (rawReason != null ? rawReason : "null");

        return TRANSLATION_CACHE.computeIfAbsent(cacheKey, k -> {
            String norm = normalizeFailureKey(errorKey, rawReason);
            if (containsAny(norm, "cooldown", "on_cooldown")) return Component.translatable("arc_quest.gui.gacha.btn.cooldown");
            if (containsAny(norm, "limit", "max_purchase", "max_purchases", "maxed")) return Component.translatable("arc_quest.gui.trade.status.maxed");
            if (containsAny(norm, "condition", "locked", "requirement", "blocked")) return Component.translatable("arc_quest.gui.trade.status.locked");
            if (containsAny(norm, "cannot_afford", "insufficient", "shortfall", "not_enough")) return Component.translatable("arc_quest.gui.gacha.btn.insufficient_funds");
            return Component.translatable("arc_quest.gui.trade.error.shop_closed");
        });
    }

    public static Component resolveGachaFailButtonText(@Nullable String failReason, boolean onCooldown, boolean maxed, boolean locked, boolean insufficientFunds, @Nullable String cooldownText) {
        String cacheKey = "gacha:" + failReason + "|" + onCooldown + "|" + maxed + "|" + locked + "|" + insufficientFunds + "|" + cooldownText;

        return TRANSLATION_CACHE.computeIfAbsent(cacheKey, k -> {
            if (onCooldown) {
                return cooldownText == null || cooldownText.isEmpty()
                        ? Component.translatable("arc_quest.gui.gacha.btn.cooldown")
                        : Component.translatable("arc_quest.gui.trade.tooltip.cooldown", cooldownText);
            }
            if (maxed) return Component.translatable("arc_quest.gui.trade.btn.empty");
            if (locked) return Component.translatable("arc_quest.gui.trade.btn.locked");

            if (insufficientFunds || containsAny(normalizeFailureKey(failReason, null), "cannot_afford", "insufficient", "shortfall")) {
                return Component.translatable("arc_quest.gui.gacha.btn.insufficient_funds");
            }
            return Component.translatable("arc_quest.gui.gacha.btn.unlock_receptacle");
        });
    }

    private static String normalizeFailureKey(@Nullable String primary, @Nullable String fallback) {
        String key = (primary != null && !primary.isEmpty()) ? primary : fallback;
        if (key == null) return "";
        return key.toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean containsAny(String source, String... needles) {
        for (String needle : needles) {
            if (source.contains(needle)) return true;
        }
        return false;
    }
}