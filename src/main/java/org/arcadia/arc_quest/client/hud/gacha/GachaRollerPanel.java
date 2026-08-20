package org.arcadia.arc_quest.client.hud.gacha;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.trade.gacha.api.GachaItem;
import org.arcadia.arc_quest.trade.gacha.network.ClientGachaCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GachaRollerPanel {

    private final GachaScreen parent;
    private final List<GachaItem> rollStrip = new ArrayList<>();
    private final int cardW = 70;
    private final int cardH = 55;
    private final int gap = 25;
    private final int cardTotalW = cardW + gap;
    private int width, height;
    private boolean isRolling = false;
    private State currentState = State.ENTER;
    private float masterAnim = 0f, rollElapsed = 0f, holdElapsed = 0f, exitElapsed = 0f, rollSpeedMult = 1.0f;
    private double scrollX = 0, targetStopX = 0;
    private long rollDuration = 0;
    private int lastTickCard = -1, targetItemIndex = -1;
    private ClientGachaCache.DrawRecord confirmedResult;
    private int targetThemeHighContrast;

    public GachaRollerPanel(GachaScreen parent) {
        this.parent = parent;
    }

    public void init(int w, int h) {
        width = w;
        height = h;
    }

    public void startRoll(ClientGachaCache.DrawRecord result) {
        confirmedResult = result;
        isRolling = true;
        currentState = State.ENTER;
        masterAnim = rollElapsed = holdElapsed = exitElapsed = 0f;
        rollSpeedMult = 1.0f;
        generateRollStrip(result.itemId());
    }

    private void generateRollStrip(String targetItemId) {
        rollStrip.clear();
        List<GachaItem> pool = parent.getShopDef().getGachaPool().getItems();
        Random rand = new Random();

        for (int i = 0; i < 60; i++) rollStrip.add(pool.get(rand.nextInt(pool.size())));
        targetItemIndex = 48 + rand.nextInt(5);
        GachaItem targetItem = pool.stream().filter(i -> i.getItemId().equals(targetItemId)).findFirst().orElse(pool.get(0));
        rollStrip.set(targetItemIndex, targetItem);

        targetThemeHighContrast = HudAnimUtil.blend(parent.getShopDef().getEffectiveThemeColor(targetItem), 0xFFFFFF, 0.15f);

        scrollX = 0;
        lastTickCard = 0;
        targetStopX = (targetItemIndex * cardTotalW + cardW / 2.0) - (width / 2.0);
        rollDuration = 4500 + rand.nextInt(1000);
    }

    // 【终极优化：内联矩形拼接边框】
    private void drawFastFrame(GuiGraphics g, int x, int y, int w, int h, int thickness, int color) {
        g.fill(x, y, x + w, y + thickness, color);
        g.fill(x, y + h - thickness, x + w, y + h, color);
        g.fill(x, y + thickness, x + thickness, y + h - thickness, color);
        g.fill(x + w - thickness, y + thickness, x + w, y + h - thickness, color);
    }

    public void render(GuiGraphics g, float dt) {
        if (!isRolling) return;
        HudCursorManager.requestPointer(currentState == State.ENTER || currentState == State.ROLLING);
        long now = Util.getMillis();

        switch (currentState) {
            case ENTER:
                masterAnim = Math.min(1.0f, masterAnim + dt * 1.25f);
                if (masterAnim >= 1.0f) currentState = State.ROLLING;
                break;
            case ROLLING:
                rollElapsed += (dt * 1000f) * rollSpeedMult;
                if (rollElapsed >= rollDuration) {
                    rollElapsed = rollDuration;
                    currentState = State.HOLD;
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.5f, 1.0f));
                    if (rollSpeedMult > 1.0f)
                        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_LAND, 1.2f, 1.0f));
                }
                break;
            case HOLD:
                holdElapsed += (dt * 1000f);
                if (holdElapsed >= 800) currentState = State.EXIT;
                break;
            case EXIT:
                exitElapsed += (dt * 1000f);
                if (exitElapsed >= 350.0f) {
                    isRolling = false;
                    parent.onRollFinished(confirmedResult);
                    return;
                }
                break;
        }

        float exitProgress = Math.min(1.0f, exitElapsed / 350.0f);
        float easeMaster = masterAnim < 0.5f ? 2.0f * masterAnim * masterAnim : 1.0f - (float) Math.pow(-2.0f * masterAnim + 2.0f, 2.0) / 2.0f;
        float wipeOut = (currentState == State.EXIT) ? (float) Math.pow(exitProgress, 3.0) : 0f;

        int bgAlpha = (int) (0x8C * easeMaster * (1.0f - wipeOut));
        if (bgAlpha > 0) g.fill(0, 0, width, height, bgAlpha << 24);

        int targetBarHeight = (int) (height * 0.10f);
        int barHeight = Math.round(targetBarHeight * easeMaster * (1.0f - wipeOut));

        if (barHeight > 0) {
            g.fill(0, 0, width, barHeight, 0xFF000000);
            g.fill(0, height - barHeight, width, height, 0xFF000000);
        }

        int safeAlpha = (int) (255 * easeMaster);
        if (safeAlpha < 10) return;

        if (currentState == State.ROLLING || currentState == State.ENTER) {
            float t = Math.min(1.0f, rollElapsed / rollDuration);
            scrollX = targetStopX * (1.0f - (float) Math.pow(1.0f - t, 4.0));
            int currentCard = (int) ((scrollX + width / 2.0) / cardTotalW);

            if (currentCard != lastTickCard && t < 1.0f) {
                lastTickCard = currentCard;
                float volume = (0.6f + (t * 0.4f)) * (rollSpeedMult > 1.0f ? 0.35f : 1.0f);
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2f - (t * 0.4f), volume));
            }
        } else {
            scrollX = targetStopX;
        }

        int cx = width / 2, centerY = height / 2;
        g.enableScissor(0, targetBarHeight, width, height - targetBarHeight);

        // =========================================================================
        // PASS 1: 纯 2D 渲染通道
        // =========================================================================
        for (int i = 0; i < rollStrip.size(); i++) {
            double drawX = i * cardTotalW - scrollX;
            if (drawX + cardW < -500 || drawX > width + 500) continue;

            float popFactor = Math.max(0f, 1f - (float) (Math.abs((drawX + cardW / 2.0) - cx) / (cardW * 1.5)));
            float cardScale = 1.0f + popFactor * 0.07f;
            int cardAlpha = safeAlpha;

            if (currentState == State.EXIT) {
                if (i == targetItemIndex) {
                    float centerWipe = Math.min(1.0f, exitProgress * 2.5f);
                    cardScale *= 1.0f - (float) Math.pow(centerWipe, 0.5);
                    cardAlpha = (int) (safeAlpha * (1.0f - centerWipe));
                } else {
                    drawX += ((i < targetItemIndex) ? -1f : 1f) * wipeOut * 600f;
                    cardAlpha = (int) (safeAlpha * (1.0f - wipeOut));
                }
            }

            if (cardAlpha <= 5) continue;
            int themeC = parent.getShopDef().getEffectiveThemeColor(rollStrip.get(i));

            g.pose().pushPose();
            g.pose().translate(drawX + cardW / 2f, centerY, 0);
            g.pose().scale(cardScale, cardScale, 1f);
            g.pose().translate(-(drawX + cardW / 2f), -centerY, 0);

            int drawCardX = (int) drawX, drawCardY = centerY - cardH / 2;

            g.fill(drawCardX, drawCardY, drawCardX + cardW, drawCardY + cardH, HudAnimUtil.withAlpha(0x111111, (int) (cardAlpha * 0.8f)));
            g.fillGradient(drawCardX, drawCardY, drawCardX + cardW, drawCardY + cardH, HudAnimUtil.withAlpha(themeC, (int) (cardAlpha * 0.2f)), 0);
            HudRenderUtil.drawCyberneticEdge(g, drawCardX, drawCardY, cardH, themeC, cardAlpha);

            if (popFactor > 0.1f && currentState != State.EXIT) {
                drawFastFrame(g, drawCardX, drawCardY, cardW, cardH, 1, HudAnimUtil.withAlpha(themeC, (int) (safeAlpha * popFactor * 0.8f)));
            }

            g.pose().popPose();
        }

        // =========================================================================
        // PASS 2: 纯 3D 渲染通道
        // =========================================================================
        for (int i = 0; i < rollStrip.size(); i++) {
            double drawX = i * cardTotalW - scrollX;
            if (drawX + cardW < -500 || drawX > width + 500) continue;

            float popFactor = Math.max(0f, 1f - (float) (Math.abs((drawX + cardW / 2.0) - cx) / (cardW * 1.5)));
            float cardScale = 1.0f + popFactor * 0.07f;
            float iconScale = 1.25f + popFactor * 0.5f;
            int cardAlpha = safeAlpha;

            if (currentState == State.EXIT) {
                if (i == targetItemIndex) {
                    float implode = 1.0f - (float) Math.pow(Math.min(1.0f, exitProgress * 2.5f), 0.5);
                    iconScale *= implode;
                    cardScale *= implode;
                    cardAlpha = (int) (safeAlpha * (1.0f - Math.min(1.0f, exitProgress * 2.5f)));
                } else {
                    drawX += ((i < targetItemIndex) ? -1f : 1f) * wipeOut * 600f;
                    cardAlpha = (int) (safeAlpha * (1.0f - wipeOut));
                }
            }

            if (cardAlpha <= 5) continue;

            g.pose().pushPose();
            g.pose().translate(drawX + cardW / 2f, centerY, 0);
            g.pose().scale(cardScale, cardScale, 1f);
            g.pose().translate(-(drawX + cardW / 2f), -centerY, 0);

            g.pose().pushPose();
            g.pose().translate(drawX + cardW / 2f, centerY, 0);
            g.pose().scale(iconScale, iconScale, 1f);
            g.pose().translate(-8, -8, 0);
            g.renderFakeItem(rollStrip.get(i).getItemStack(), 0, 0);
            g.pose().popPose();

            g.pose().popPose();
        }

        // 准星 (覆盖在最顶层)
        float aimW = (cardW * 1.15f) + 8, aimH = (cardH * 1.15f) + 8;
        if (currentState == State.HOLD || currentState == State.EXIT) {
            float holdT = Math.min(1.0f, (holdElapsed + exitElapsed) / 400f);
            float spring = 1.0f + 0.4f * (float) Math.exp(-holdT * 8f) * (float) Math.cos(holdT * 25f);
            aimW *= spring;
            aimH *= spring;
        }
        if (currentState == State.EXIT) {
            aimW += wipeOut * 300f;
            aimH += wipeOut * 300f;
        }

        int pulseA = currentState == State.EXIT ? (int) ((safeAlpha * (0.6f + 0.4f * Math.sin(now / 150.0))) * (1.0f - wipeOut)) : (int) (safeAlpha * (0.6f + 0.4f * Math.sin(now / 150.0)));
        if (pulseA > 5) {
            int crossColor = HudAnimUtil.withAlpha(targetThemeHighContrast, pulseA), len = 10, thick = 2;
            g.fill((int) (cx - aimW / 2), (int) (centerY - aimH / 2), (int) (cx - aimW / 2 + len), (int) (centerY - aimH / 2 + thick), crossColor);
            g.fill((int) (cx - aimW / 2), (int) (centerY - aimH / 2), (int) (cx - aimW / 2 + thick), (int) (centerY - aimH / 2 + len), crossColor);
            g.fill((int) (cx + aimW / 2 - len), (int) (centerY - aimH / 2), (int) (cx + aimW / 2), (int) (centerY - aimH / 2 + thick), crossColor);
            g.fill((int) (cx + aimW / 2 - thick), (int) (centerY - aimH / 2), (int) (cx + aimW / 2), (int) (centerY - aimH / 2 + len), crossColor);
            g.fill((int) (cx - aimW / 2), (int) (centerY + aimH / 2 - thick), (int) (cx - aimW / 2 + len), (int) (centerY + aimH / 2), crossColor);
            g.fill((int) (cx - aimW / 2), (int) (centerY + aimH / 2 - len), (int) (cx - aimW / 2 + thick), (int) (centerY + aimH / 2), crossColor);
            g.fill((int) (cx + aimW / 2 - len), (int) (centerY + aimH / 2 - thick), (int) (cx + aimW / 2), (int) (centerY + aimH / 2), crossColor);
            g.fill((int) (cx + aimW / 2 - thick), (int) (centerY + aimH / 2 - len), (int) (cx + aimW / 2), (int) (centerY + aimH / 2), crossColor);
        }

        g.disableScissor();
    }

    public boolean mouseClicked() {
        if (!isRolling) return false;
        if (currentState == State.ENTER || currentState == State.ROLLING) {
            if (rollSpeedMult == 1.0f) {
                rollSpeedMult = 8.0f;
                if (currentState == State.ENTER) masterAnim = 1.0f;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.5f, 1.0f));
            } else {
                currentState = State.HOLD;
                rollElapsed = rollDuration;
                scrollX = targetStopX;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.5f, 1.0f));
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_LAND, 1.2f, 2.0f));
            }
            return true;
        }
        return false;
    }

    public void onScreenClose() {
        currentState = State.EXIT;
        exitElapsed = 0f;
    }

    private enum State {ENTER, ROLLING, HOLD, EXIT}
}
