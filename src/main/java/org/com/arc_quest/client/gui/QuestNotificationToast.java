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

        int bgAlpha = (int) (0x88 * alpha);
        int bgColor = (bgAlpha << 24) | 0x121212;
        g.fill(toastX, slotY, toastX + TOAST_WIDTH, slotY + TOAST_HEIGHT, bgColor);

        int accentAlpha = (int) (255 * alpha);
        int accentColor = (accentAlpha << 24) | (type.accentColor & 0x00FFFFFF);
        g.fill(toastX, slotY, toastX + 3, slotY + TOAST_HEIGHT, accentColor);

        int lineAlpha = (int) (80 * alpha);
        g.fill(toastX + 3, slotY + TOAST_HEIGHT - 1, toastX + TOAST_WIDTH, slotY + TOAST_HEIGHT, (lineAlpha << 24) | (type.accentColor & 0x00FFFFFF));

        int textAlpha = (int) (255 * alpha);
        if (textAlpha > 8) {
            g.pose().pushPose();
            g.pose().translate(toastX + 8, slotY + 4, 0);
            g.pose().scale(0.7f, 0.7f, 1f);
            g.drawString(font, type.prefix, 0, 0, QuestAnimUtil.withAlpha(type.accentColor, textAlpha), true);
            g.pose().popPose();

            String nameStr = font.plainSubstrByWidth(text, TOAST_WIDTH - 16);
            g.drawString(font, nameStr, toastX + 8, slotY + TOAST_HEIGHT - font.lineHeight - 3, QuestAnimUtil.withAlpha(0xFFFFFF, textAlpha), true);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}