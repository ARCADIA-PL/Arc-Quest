package org.com.arc_quest.client.gui.quest;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.client.gui.HudRenderUtil;

public class QuestNotificationToast {
    public static final int TOAST_WIDTH = 220;
    public static final int TOAST_HEIGHT = 28;

    private static final float ENTER = 400f;
    private static final float HOLD = 3000f;
    private static final float EXIT = 350f;

    private final QuestToastManager.ToastType type;
    private final String text;
    private long startTime;
    private long lastUpdateTime;

    public QuestNotificationToast(QuestToastManager.ToastType type, String text) {
        this.type = type;
        this.text = text;
        long now = Util.getMillis();
        this.startTime = now;
        this.lastUpdateTime = now;
    }

    public void tick(boolean isFrozen) {
        long now = Util.getMillis();
        long dt = now - lastUpdateTime;
        this.lastUpdateTime = now;

        if (isFrozen) {
            this.startTime += dt;
        }
    }

    public boolean isExpired() {
        return (Util.getMillis() - startTime) >= (ENTER + HOLD + EXIT);
    }

    public void render(GuiGraphics g, Font font, int screenWidth, int slotY, int marginRight) {
        long elapsed = Util.getMillis() - startTime;
        float alpha, slideX;

        if (elapsed < ENTER) {
            float t = elapsed / ENTER;
            float ease = HudAnimUtil.easeOutQuintic(t);
            alpha = ease;
            slideX = (1f - ease) * (TOAST_WIDTH + marginRight + 30);
        } else if (elapsed < ENTER + HOLD) {
            alpha = 1f;
            slideX = 0f;
        } else {
            float t = Math.min(1f, (elapsed - ENTER - HOLD) / EXIT);
            float ease = HudAnimUtil.easeInQuartic(t);
            alpha = 1f - (float) Math.pow(t, 6);
            slideX = ease * (TOAST_WIDTH + marginRight + 30);
        }

        if (alpha < 0.01f) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        int toastX = (int) (screenWidth - TOAST_WIDTH - marginRight + slideX);

        int bgAlpha = (int) (0x88 * alpha);
        int accentAlpha = (int) (255 * alpha);
        int lineAlpha = (int) (80 * alpha);
        HudRenderUtil.drawToastPanel(g, toastX, slotY, TOAST_WIDTH, TOAST_HEIGHT,
                0x121212, bgAlpha,
                type.accentColor & 0x00FFFFFF, accentAlpha, 3,
                lineAlpha);

        int textAlpha = (int) (255 * alpha);
        if (textAlpha > 8) {
            String subtitle = type.prefix;
            String nameStr = font.plainSubstrByWidth(text, TOAST_WIDTH - 16);

            int subColor = HudAnimUtil.withAlpha(type.accentColor, textAlpha);
            int titleColor = HudAnimUtil.withAlpha(0xFFFFFF, textAlpha);
            HudRenderUtil.drawDualText(g, font, toastX + 8, slotY + 4,
                    subtitle, nameStr, subColor, titleColor, 1.0f);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}