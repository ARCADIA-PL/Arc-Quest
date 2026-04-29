package org.com.arc_quest.client.hud.quest.toast;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.com.arc_quest.client.hud.HudAnimUtil;
import org.com.arc_quest.client.hud.HudRenderUtil;

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

        // 【终极注入】：调用全剧统一的 3A 级自适应缩放！
        Minecraft mc = Minecraft.getInstance();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        float uiScale = HudRenderUtil.getUniversalUiScale(screenWidth, screenHeight);
        float sw = screenWidth / uiScale;

        // 统一右侧边距和Y坐标为虚拟坐标
        int vMarginRight = 16;
        int vSlotY = (int) (slotY / uiScale);

        float alpha, slideX;
        float slideDistance = TOAST_WIDTH + vMarginRight + 30f;

        if (elapsed < ENTER) {
            float t = elapsed / ENTER;
            float ease = HudAnimUtil.easeOutQuintic(t);
            alpha = ease;
            slideX = (1f - ease) * slideDistance;
        } else if (elapsed < ENTER + HOLD) {
            alpha = 1f;
            slideX = 0f;
        } else {
            float t = Math.min(1f, (elapsed - ENTER - HOLD) / EXIT);
            float ease = HudAnimUtil.easeInQuartic(t);
            alpha = 1f - (float) Math.pow(t, 6);
            slideX = ease * slideDistance;
        }

        if (alpha < 0.01f) return;

        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        int toastX = (int) (sw - TOAST_WIDTH - vMarginRight + slideX);

        int bgAlpha = (int) (0x88 * alpha);
        int accentAlpha = (int) (255 * alpha);
        int lineAlpha = (int) (80 * alpha);
        HudRenderUtil.drawToastPanel(g, toastX, vSlotY, TOAST_WIDTH, TOAST_HEIGHT,
                0x121212, bgAlpha,
                type.accentColor & 0x00FFFFFF, accentAlpha, 3,
                lineAlpha);

        int textAlpha = (int) (255 * alpha);
        if (textAlpha > 8) {
            String subtitle = type.prefix;
            String nameStr = font.plainSubstrByWidth(text, TOAST_WIDTH - 16);

            int subColor = HudAnimUtil.withAlpha(type.accentColor, textAlpha);
            int titleColor = HudAnimUtil.withAlpha(0xFFFFFF, textAlpha);
            HudRenderUtil.drawDualText(g, font, toastX + 8, vSlotY + 4,
                    subtitle, nameStr, subColor, titleColor, 1.0f);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        g.pose().popPose();
    }
}