package org.arcadia.arc_quest.client.hud.gacha;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.trade.gacha.api.GachaItem;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.arcadia.arc_quest.trade.gacha.network.ClientGachaCache;

public class GachaResultRenderer {

    public static final GachaResultRenderer INSTANCE = new GachaResultRenderer();
    private static final float TIME_ENTER = 800f;
    private static final float TIME_EXIT = 600f;
    private boolean active = false;
    private GachaShopDefinition shopDef;
    private ClientGachaCache.DrawRecord result;
    private GachaScreen parentScreen;
    private State currentState = State.ENTER;
    private long startTime = 0;
    private long exitStartTime = 0;
    private boolean rewardConfirmed = false;
    private GachaItem resolvedTargetItem;
    private ItemStack resolvedItemStack = ItemStack.EMPTY;
    private int resolvedThemeColor = 0xFFFFFF;
    private String cachedResultName = "";
    private String cachedAcknowledgeText = "";

    private GachaResultRenderer() {
    }

    public void showResult(GachaScreen parent, GachaShopDefinition shopDef, ClientGachaCache.DrawRecord result) {
        this.parentScreen = parent;
        this.shopDef = shopDef;
        this.result = result;
        this.active = true;
        this.currentState = State.ENTER;
        this.startTime = Util.getMillis();
        this.rewardConfirmed = false;
        resolveResultCache();
        Minecraft.getInstance().mouseHandler.releaseMouse();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2f));
    }

    public boolean isActive() {
        return active;
    }

    public void render(GuiGraphics g, int screenWidth, int screenHeight, float dt) {
        if (!active || result == null) return;

        long now = Util.getMillis();
        long elapsed = now - startTime;

        if (currentState == State.ENTER && elapsed >= TIME_ENTER) {
            currentState = State.HOLD;
            if (!rewardConfirmed && parentScreen != null) {
                rewardConfirmed = true;
                parentScreen.confirmDrawAndSync();
            }
        }

        if (currentState == State.EXIT) {
            if (now - exitStartTime >= TIME_EXIT) {
                forceCloseAndConfirm();
                return;
            }
        }

        int frameW = 200, frameH = 100;
        float baseScale = 1.3f, revealProgress = 1.0f, wipeProgress = 0.0f, driftX = 0f, alpha = 1.0f;

        if (currentState == State.ENTER) {
            float t = Math.min(1.0f, elapsed / TIME_ENTER);
            float easeInOut = t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3f) / 2f;
            revealProgress = easeInOut;
            driftX = -25f * (1f - easeInOut);
            alpha = easeInOut;
        } else if (currentState == State.EXIT) {
            float t = Math.min(1.0f, (now - exitStartTime) / TIME_EXIT);
            float easeInOut = t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3f) / 2f;
            wipeProgress = easeInOut;
            driftX = 50f * easeInOut;
            alpha = 1.0f - (float) Math.pow(t, 2.0);
        }

        float scaledW = frameW * baseScale, cx = screenWidth / 2f + driftX, cy = screenHeight / 2f;
        int scX1 = (int) (cx - scaledW / 2f - 5), scX2 = (int) (cx + scaledW / 2f + 5);

        if (currentState == State.ENTER) scX2 = (int) (cx - scaledW / 2f + scaledW * revealProgress);
        else if (currentState == State.EXIT) scX1 = (int) (cx - scaledW / 2f + scaledW * wipeProgress);

        if (scX2 <= scX1) return;
        g.enableScissor(scX1, -1000, scX2, 10000);

        // =========================================================================
        // PASS 1: 纯 2D 渲染通道
        // =========================================================================
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(baseScale, baseScale, 1f);
        g.pose().translate(-frameW / 2f, -frameH / 2f, 0);

        int safeAlpha = Math.max(0, Math.min(255, (int) (alpha * 255)));
        g.fill(0, 0, frameW, frameH, ((int) (safeAlpha * 0.6f) << 24) | 0x05050A);
        g.fillGradient(0, 0, frameW, frameH, HudAnimUtil.withAlpha(resolvedThemeColor, (int) (safeAlpha * 0.3f)), 0x00000000);
        HudRenderUtil.drawCyberneticEdge(g, 0, 0, frameH, resolvedThemeColor, safeAlpha);

        if (safeAlpha > 10 && resolvedTargetItem != null) {
            int textX = 90;
            g.pose().pushPose();
            g.pose().scale(0.7f, 0.7f, 1f);
            g.drawString(Minecraft.getInstance().font, "// DECRYPTED", (int) (textX / 0.7f), (int) (15 / 0.7f), HudAnimUtil.withAlpha(0xAAAAAA, safeAlpha), false);
            g.pose().popPose();

            g.pose().pushPose();
            g.pose().scale(1.0f, 1.0f, 1f);
            g.drawString(Minecraft.getInstance().font, cachedResultName, textX, 35, HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha), false);
            g.pose().popPose();

            if (result.pityTriggered()) {
                g.pose().pushPose();
                g.pose().scale(0.8f, 0.8f, 1f);
                g.drawString(Minecraft.getInstance().font, "[ GUARANTEED ]", (int) (textX / 0.8f), (int) (55 / 0.8f), HudAnimUtil.withAlpha(0xFFD700, safeAlpha), false);
                g.pose().popPose();
            }

            int blinkA = (int) (safeAlpha * (0.3f + 0.7f * (float) (Math.sin(now / 200.0) * 0.5 + 0.5)));
            g.pose().pushPose();
            g.pose().scale(0.7f, 0.7f, 1f);
            g.drawString(Minecraft.getInstance().font, cachedAcknowledgeText, (int) (textX / 0.7f), (int) ((frameH - 15) / 0.7f), HudAnimUtil.withAlpha(resolvedThemeColor, blinkA), false);
            g.pose().popPose();
        }

        // =========================================================================
        // PASS 2: 纯 3D 渲染通道
        // =========================================================================
        if (resolvedTargetItem != null) {
            g.pose().pushPose();
            g.pose().translate(45, frameH / 2f, 0);
            g.pose().scale(3.0f, 3.0f, 1f);
            g.pose().translate(-8, -8, 0);
            g.renderItem(resolvedItemStack, 0, 0);
            g.pose().popPose();
        }

        g.pose().popPose();
        g.disableScissor();
    }

    private void resolveResultCache() {
        resolvedTargetItem = shopDef == null || result == null ? null : shopDef.getGachaPool().getItems().stream().filter(i -> i.getItemId().equals(result.itemId())).findFirst().orElse(null);
        resolvedThemeColor = resolvedTargetItem != null ? shopDef.getEffectiveThemeColor(resolvedTargetItem) : 0xFFFFFF;
        resolvedItemStack = resolvedTargetItem != null ? resolvedTargetItem.getItemStack() : ItemStack.EMPTY;
        cachedAcknowledgeText = Component.translatable("arc_quest.gui.gacha.result.acknowledge").getString();
        cachedResultName = !resolvedItemStack.isEmpty() ? Minecraft.getInstance().font.plainSubstrByWidth(">_" + resolvedItemStack.getHoverName().getString() + " x" + result.actualCount(), 200 - 90 - 5) : "";
    }

    public boolean mouseClicked() {
        if (!active || currentState == State.EXIT || (currentState == State.ENTER && (Util.getMillis() - startTime < TIME_ENTER)))
            return false;
        this.currentState = State.EXIT;
        this.exitStartTime = Util.getMillis();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.5f));
        return true;
    }

    public void forceCloseAndConfirm() {
        active = false;
        if (!rewardConfirmed && parentScreen != null) {
            rewardConfirmed = true;
            parentScreen.confirmDrawAndSync();
        }
    }

    private enum State {ENTER, HOLD, EXIT}
}