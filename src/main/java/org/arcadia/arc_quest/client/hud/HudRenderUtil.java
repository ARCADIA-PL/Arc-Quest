package org.arcadia.arc_quest.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 共享渲染工具集 (稳定极速版)
 */
public final class HudRenderUtil {

    private HudRenderUtil() {
    }

    public static float getUniversalUiScale(int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return 1.0f;
        double guiScale = mc.getWindow().getGuiScale();
        if (guiScale == 0) guiScale = 1.0;

        float scale = (float) (3.0 / guiScale);
        float sw = screenWidth / scale;
        float sh = screenHeight / scale;

        float minW = 480f;
        float minH = 260f;

        if (sw < minW) {
            scale = screenWidth / minW;
            sh = screenHeight / scale;
        }
        if (sh < minH) {
            scale = screenHeight / minH;
        }

        return scale;
    }

    public static void drawGlassPanel(GuiGraphics g, int x, int y, int w, int h,
                                      int bgColor, int bgAlpha,
                                      int accentColor, int accentAlpha, int accentWidth) {
        g.fill(x, y, x + w, y + h, (bgAlpha << 24) | (bgColor & 0x00FFFFFF));
        drawCyberneticEdge(g, x, y, h, accentColor, accentAlpha);
    }

    public static void drawToastPanel(GuiGraphics g, int x, int y, int w, int h,
                                      int bgColor, int bgAlpha,
                                      int accentColor, int accentAlpha, int accentWidth,
                                      int lineAlpha) {
        g.fill(x, y, x + w, y + h, (bgAlpha << 24) | (bgColor & 0x00FFFFFF));
        drawCyberneticEdge(g, x, y, h, accentColor, accentAlpha);
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

        // 【修复】保留了 pushPose 机制，这能确保浮点数 x/y 平移时的高精度对齐，避免锯齿错位
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

        // 【修复】废弃容易遗漏混合状态的底边重绘，恢复最稳妥的多次 g.fill
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

    public static void drawCyberneticEdge(GuiGraphics g, int x, int y, int height, int themeColor, int alpha) {
        if (alpha < 5) return;
        int coreColor = themeColor & 0xFFFFFF;
        int topAlpha = alpha;
        int botAlpha = (int) (alpha * 0.15f);

        int colorTop = coreColor | (topAlpha << 24);
        int colorBot = coreColor | (botAlpha << 24);

        g.fillGradient(x, y, x + 3, y + height, colorTop, colorBot);

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

    public static List<String> wrapText(String text, int maxWidth, Font font) {
        // 【修复】保留原版的可靠切分逻辑，避免优化版的自循环切分吞掉前置空格和特殊符号！
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

        if (containsAny(normalized, "cooldown", "on_cooldown")) {
            return Component.translatable("arc_quest.gui.gacha.btn.cooldown");
        }
        if (containsAny(normalized, "limit", "max_purchase", "max_purchases", "maxed")) {
            return Component.translatable("arc_quest.gui.trade.status.maxed");
        }
        if (containsAny(normalized, "condition", "locked", "requirement", "blocked")) {
            return Component.translatable("arc_quest.gui.trade.status.locked");
        }
        if (containsAny(normalized, "cannot_afford", "insufficient", "shortfall", "not_enough")) {
            return Component.translatable("arc_quest.gui.gacha.btn.insufficient_funds");
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
        if (locked) {
            return Component.translatable("arc_quest.gui.trade.btn.locked");
        }
        if (insufficientFunds || containsAny(normalizeFailureKey(failReason, null), "cannot_afford", "insufficient", "shortfall")) {
            return Component.translatable("arc_quest.gui.gacha.btn.insufficient_funds");
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
            if (source.contains(needle)) return true;
        }
        return false;
    }
}