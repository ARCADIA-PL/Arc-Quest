package org.com.arc_quest.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 右侧精美弹幕实体类，独立负责自身的缓动、存活期与渲染。
 */
public class QuestNotificationToast {
    public static final int TOAST_WIDTH = 220;
    public static final int TOAST_HEIGHT = 28;

    private static final float ENTER = 400f;
    private static final float HOLD = 3000f;
    private static final float EXIT = 350f;

    private final QuestToastManager.ToastType type;
    private final String text;
    private long startTime;
    private long lastRenderTime;

    public QuestNotificationToast(QuestToastManager.ToastType type, String text) {
        this.type = type;
        this.text = text;
        this.startTime = Util.getMillis();
        this.lastRenderTime = this.startTime;
    }

    public boolean isExpired() {
        return (Util.getMillis() - startTime) >= (ENTER + HOLD + EXIT);
    }

    public void render(GuiGraphics g, Font font, int screenWidth, int slotY, int marginRight, boolean isFrozen) {
        long now = Util.getMillis();
        long dt = now - lastRenderTime;
        this.lastRenderTime = now;

        if (isFrozen) {
            this.startTime += dt;
            return;
        }

        long elapsed = now - startTime;
        float alpha, slideX;

        if (elapsed < ENTER) {
            float t = elapsed / ENTER;
            float ease = QuestAnimUtil.easeOutQuintic(t);
            alpha = ease;
            slideX = (1f - ease) * (TOAST_WIDTH + marginRight + 30);
        } else if (elapsed < ENTER + HOLD) {
            alpha = 1f;
            slideX = 0f;
        } else {
            float t = Math.min(1f, (elapsed - ENTER - HOLD) / EXIT);
            float ease = QuestAnimUtil.easeInQuartic(t);
            alpha = 1f - (float) Math.pow(t, 6);
            slideX = ease * (TOAST_WIDTH + marginRight + 30);
        }

        if (alpha < 0.01f) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        int toastX = (int) (screenWidth - TOAST_WIDTH - marginRight + slideX);

        // 使用共用渲染方法绘制Toast面板
        int bgAlpha = (int) (0x88 * alpha);
        int accentAlpha = (int) (255 * alpha);
        int lineAlpha = (int) (80 * alpha);
        QuestRenderUtil.drawToastPanel(g, toastX, slotY, TOAST_WIDTH, TOAST_HEIGHT,
                0x121212, bgAlpha,
                type.accentColor & 0x00FFFFFF, accentAlpha, 3,
                lineAlpha);

        int textAlpha = (int) (255 * alpha);
        if (textAlpha > 8) {
            String subtitle = type.prefix;
            String nameStr = font.plainSubstrByWidth(text, TOAST_WIDTH - 16);
            
            // 使用共用方法绘制双行文本
            int subColor = QuestAnimUtil.withAlpha(type.accentColor, textAlpha);
            int titleColor = QuestAnimUtil.withAlpha(0xFFFFFF, textAlpha);
            QuestRenderUtil.drawDualText(g, font, toastX + 8, slotY + 4,
                    subtitle, nameStr, subColor, titleColor, 1.0f);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}