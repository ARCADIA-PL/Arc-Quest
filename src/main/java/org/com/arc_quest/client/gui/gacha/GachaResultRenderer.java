package org.com.arc_quest.client.gui.gacha;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.trade.gacha.api.GachaItem;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.com.arc_quest.trade.gacha.network.ClientGachaCache;

public class GachaResultRenderer {

    public static final GachaResultRenderer INSTANCE = new GachaResultRenderer();

    private boolean active = false;
    private GachaShopDefinition shopDef;
    private ClientGachaCache.DrawRecord result;
    private GachaScreen parentScreen; // ★ 接入代理

    private enum State { ENTER, HOLD, EXIT }
    private State currentState = State.ENTER;

    private static final float TIME_ENTER = 700f;
    private static final float TIME_EXIT = 450f;

    private long startTime = 0;
    private long exitStartTime = 0;
    private boolean rewardConfirmed = false;

    private GachaResultRenderer() {}

    public void showResult(GachaScreen parent, GachaShopDefinition shopDef, ClientGachaCache.DrawRecord result) {
        this.parentScreen = parent;
        this.shopDef = shopDef;
        this.result = result;
        this.active = true;
        this.currentState = State.ENTER;
        this.startTime = Util.getMillis();
        this.rewardConfirmed = false;
        Minecraft.getInstance().mouseHandler.releaseMouse();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2f));
    }


    public boolean isActive() { return active; }

    public void render(GuiGraphics g, int screenWidth, int screenHeight, float dt) {
        if (!active || result == null) return;

        long now = Util.getMillis();
        long elapsed = now - startTime;

        if (currentState == State.ENTER && elapsed >= TIME_ENTER) {
            currentState = State.HOLD;
            // ★ 正常流程：弹窗稳住瞬间，触发中心结算
            if (!rewardConfirmed && parentScreen != null) {
                rewardConfirmed = true;
                parentScreen.confirmDrawAndSync();
            }
        }

        if (currentState == State.EXIT) {
            long exitElapsed = now - exitStartTime;
            if (exitElapsed >= TIME_EXIT) {
                forceCloseAndConfirm();
                return;
            }
        }

        int frameW = 200;
        int frameH = 100;
        float baseScale = 1.3f; // 整体放大，更有冲击力

        float revealProgress = 1.0f;
        float wipeProgress = 0.0f;
        float driftX = 0f;
        float alpha = 1.0f;

        // 【核心动效注入】：复刻 GenesisSkinScreen 的进出场与水平漂移逻辑
        if (currentState == State.ENTER) {
            float t = Math.min(1.0f, elapsed / TIME_ENTER);
            float easeOut = HudAnimUtil.easeOutCubic(t);
            revealProgress = easeOut;
            driftX = -25f * (1f - easeOut); // 从左侧微微滑入
            alpha = easeOut;
        } else if (currentState == State.EXIT) {
            float t = Math.min(1.0f, (now - exitStartTime) / TIME_EXIT);
            float easeIn = (float) Math.pow(t, 4.0); // 锐利的二次加速退出
            wipeProgress = easeIn;
            driftX = 40f * easeIn; // 向右滑动同时被擦除
            alpha = 1.0f - (float) Math.pow(t, 2.0);
        }

        float scaledW = frameW * baseScale;
        float cx = screenWidth / 2f + driftX;
        float cy = screenHeight / 2f;

        // 计算 Scissor 裁剪矩阵
        int scX1 = (int) (cx - scaledW / 2f - 5);
        int scX2 = (int) (cx + scaledW / 2f + 5);

        if (currentState == State.ENTER) {
            scX2 = (int) (cx - scaledW / 2f + scaledW * revealProgress);
        } else if (currentState == State.EXIT) {
            scX1 = (int) (cx - scaledW / 2f + scaledW * wipeProgress);
        }

        // 极限防越界
        if (scX2 <= scX1) return;

        GachaItem targetItem = shopDef.getGachaPool().getItems().stream().filter(i -> i.getItemId().equals(result.itemId())).findFirst().orElse(null);
        int themeC = targetItem != null ? shopDef.getEffectiveThemeColor(targetItem) : 0xFFFFFF;

        // 开启底层裁剪！这才是 3A 的浪漫！
        g.enableScissor(scX1, -1000, scX2, 10000);

        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(baseScale, baseScale, 1f);
        g.pose().translate(-frameW / 2f, -frameH / 2f, 0);

        int safeAlpha = Math.max(0, Math.min(255, (int)(alpha * 255)));

        // 极简机能风底板
        int bgBase = ((int)(safeAlpha * 0.6f) << 24) | 0x05050A; // 加深一点不透明度，防透
        g.fill(0, 0, frameW, frameH, bgBase);
        g.fillGradient(0, 0, frameW, frameH, HudAnimUtil.withAlpha(themeC, (int)(safeAlpha * 0.3f)), 0x00000000);
        g.fill(0, 0, 4, frameH, HudAnimUtil.withAlpha(themeC, safeAlpha));

        if (targetItem != null) {
            g.pose().pushPose();
            g.pose().translate(45, frameH / 2f, 0);
            g.pose().scale(3.0f, 3.0f, 1f);
            g.pose().translate(-8, -8, 0);
            g.renderItem(targetItem.getItemStack(), 0, 0);
            g.pose().popPose();
        }

        if (safeAlpha > 10 && targetItem != null) {
            int textX = 90;

            g.pose().pushPose();
            g.pose().scale(0.7f, 0.7f, 1f);
            g.drawString(Minecraft.getInstance().font, "// DECRYPTED", (int)(textX / 0.7f), (int)(15 / 0.7f), HudAnimUtil.withAlpha(0xAAAAAA, safeAlpha), true);
            g.pose().popPose();

            g.pose().pushPose();
            g.pose().scale(1.0f, 1.0f, 1f);
            String nameStr = ">_" + targetItem.getItemStack().getHoverName().getString() + " x" + result.actualCount();
            int maxW = frameW - textX - 5;
            nameStr = Minecraft.getInstance().font.plainSubstrByWidth(nameStr, maxW);
            g.drawString(Minecraft.getInstance().font, nameStr, textX, 35, HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha), true);
            g.pose().popPose();

            if (result.pityTriggered()) {
                g.pose().pushPose();
                g.pose().scale(0.8f, 0.8f, 1f);
                g.drawString(Minecraft.getInstance().font, "[ GUARANTEED ]", (int)(textX / 0.8f), (int)(55 / 0.8f), HudAnimUtil.withAlpha(0xFFD700, safeAlpha), true);
                g.pose().popPose();
            }

            int blinkA = (int)(safeAlpha * (0.4f + 0.6f * Math.sin(now / 150.0)));
            g.pose().pushPose();
            g.pose().scale(0.7f, 0.7f, 1f);
            g.drawString(Minecraft.getInstance().font, "CLICK TO ACKNOWLEDGE", (int)(textX / 0.7f), (int)((frameH - 15) / 0.7f), HudAnimUtil.withAlpha(themeC, blinkA), true);
            g.pose().popPose();
        }

        g.pose().popPose();

        // 关闭裁剪
        g.disableScissor();
    }

    public boolean mouseClicked() {
        if (!active || currentState == State.EXIT) return false;
        if (currentState == State.ENTER && (Util.getMillis() - startTime < TIME_ENTER)) return false;

        this.currentState = State.EXIT;
        this.exitStartTime = Util.getMillis();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.5f));
        return true;
    }

    public void forceCloseAndConfirm() {
        active = false;
        // ★ 异常退出流程：强制触发中心结算
        if (!rewardConfirmed && parentScreen != null) {
            rewardConfirmed = true;
            parentScreen.confirmDrawAndSync();
        }
    }
}