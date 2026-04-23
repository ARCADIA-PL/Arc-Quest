package org.com.arc_quest.client.gui.gacha;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.trade.gacha.api.GachaItem;
import org.com.arc_quest.trade.gacha.network.ClientGachaCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GachaRollerPanel {

    private final GachaScreen parent;
    private int width, height;

    private boolean isRolling = false;

    private enum State { ENTER, ROLLING, HOLD, EXIT }
    private State currentState = State.ENTER;

    private float masterAnim = 0f;

    private float rollElapsed = 0f;
    private float holdElapsed = 0f;
    private float exitElapsed = 0f;
    private float rollSpeedMult = 1.0f;

    private double scrollX = 0;
    private double targetStopX = 0;
    private long rollDuration = 0;
    private int lastTickCard = -1;

    private final List<GachaItem> rollStrip = new ArrayList<>();
    private int targetItemIndex = -1;
    private ClientGachaCache.DrawRecord confirmedResult;
    private int targetThemeHighContrast;

    private final int cardW = 70;
    private final int cardH = 55;
    private final int gap = 25;
    private final int cardTotalW = cardW + gap;

    public GachaRollerPanel(GachaScreen parent) {
        this.parent = parent;
    }

    public void init(int w, int h) {
        this.width = w;
        this.height = h;
    }

    public void startRoll(ClientGachaCache.DrawRecord result) {
        this.confirmedResult = result;
        this.isRolling = true;
        this.currentState = State.ENTER;

        this.masterAnim = 0f;
        this.rollElapsed = 0f;
        this.holdElapsed = 0f;
        this.exitElapsed = 0f;
        this.rollSpeedMult = 1.0f;

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

        int rawTheme = parent.getShopDef().getEffectiveThemeColor(targetItem);
        this.targetThemeHighContrast = HudAnimUtil.blend(rawTheme, 0xFFFFFF, 0.15f);

        scrollX = 0;
        lastTickCard = 0;
        double absoluteCenter = targetItemIndex * cardTotalW + cardW / 2.0;
        targetStopX = absoluteCenter - (width / 2.0);
        rollDuration = 4500 + rand.nextInt(1000);
    }

    public void render(GuiGraphics g, float dt) {
        if (!isRolling) return;

        long now = Util.getMillis();

        switch (currentState) {
            case ENTER:
                masterAnim = Math.min(1.0f, masterAnim + dt * 1.25f);
                if (masterAnim >= 1.0f) {
                    currentState = State.ROLLING;
                }
                break;
            case ROLLING:
                rollElapsed += (dt * 1000f) * rollSpeedMult;
                if (rollElapsed >= rollDuration) {
                    rollElapsed = rollDuration;
                    currentState = State.HOLD;
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.5f, 1.0f));
                    if (rollSpeedMult > 1.0f) {
                        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_LAND, 1.2f, 1.0f));
                    }
                }
                break;
            case HOLD:
                holdElapsed += (dt * 1000f);
                if (holdElapsed >= 800) {
                    currentState = State.EXIT;
                }
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
        float easeMaster = masterAnim < 0.5f ?
                2.0f * masterAnim * masterAnim : 1.0f - (float)Math.pow(-2.0f * masterAnim + 2.0f, 2.0) / 2.0f;
        float wipeOut = (currentState == State.EXIT) ? (float)Math.pow(exitProgress, 3.0) : 0f;

        int bgAlpha = (int)(0x8C * easeMaster * (1.0f - wipeOut));
        if (bgAlpha > 0) g.fill(0, 0, width, height, bgAlpha << 24);

        int targetBarHeight = (int) (this.height * 0.10f);
        int barHeight = Math.round(targetBarHeight * easeMaster * (1.0f - wipeOut));

        if (barHeight > 0) {
            g.fill(0, 0, this.width, barHeight, 0xFF000000);
            g.fill(0, this.height - barHeight, this.width, this.height, 0xFF000000);
        }

        int safeAlpha = (int)(255 * easeMaster);
        if (safeAlpha < 10) return;

        if (currentState == State.ROLLING || currentState == State.ENTER) {
            float t = Math.min(1.0f, rollElapsed / rollDuration);
            float rollEase = 1.0f - (float)Math.pow(1.0f - t, 4.0);
            scrollX = targetStopX * rollEase;

            double pointerAbsoluteX = scrollX + width / 2.0;
            int currentCard = (int) (pointerAbsoluteX / cardTotalW);
            if (currentCard != lastTickCard && t < 1.0f) {
                lastTickCard = currentCard;
                float pitch = 1.2f - (t * 0.4f);
                float volume = 0.6f + (t * 0.4f);
                // 快进时降低音量，形成低沉极速的摩擦音，保护耳朵且更带感
                if (rollSpeedMult > 1.0f) volume *= 0.35f;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), pitch, volume));
            }
        } else {
            scrollX = targetStopX;
        }

        int cx = width / 2;
        int centerY = height / 2;

        g.enableScissor(0, targetBarHeight, width, height - targetBarHeight);

        for (int i = 0; i < rollStrip.size(); i++) {
            double drawX = i * cardTotalW - scrollX;
            if (drawX + cardW < -500 || drawX > width + 500) continue;

            GachaItem item = rollStrip.get(i);
            int themeC = parent.getShopDef().getEffectiveThemeColor(item);

            double centerDist = Math.abs((drawX + cardW / 2.0) - cx);
            float popFactor = Math.max(0f, 1f - (float)(centerDist / (cardW * 1.5)));

            float cardScale = 1.0f + popFactor * 0.07f;
            float iconScale = 1.25f + popFactor * 0.5f;
            int cardAlpha = safeAlpha;

            if (currentState == State.EXIT) {
                if (i == targetItemIndex) {
                    float centerWipe = Math.min(1.0f, exitProgress * 2.5f);
                    float implode = 1.0f - (float)Math.pow(centerWipe, 0.5);
                    iconScale *= implode;
                    cardScale *= implode;
                    cardAlpha = (int)(safeAlpha * (1.0f - centerWipe));
                } else {
                    float dir = (i < targetItemIndex) ? -1f : 1f;
                    drawX += dir * wipeOut * 600f;
                    cardAlpha = (int)(safeAlpha * (1.0f - wipeOut));
                }
            }

            if (cardAlpha <= 5) continue;

            g.pose().pushPose();
            g.pose().translate(drawX + cardW/2f, centerY, 0);
            g.pose().scale(cardScale, cardScale, 1f);
            g.pose().translate(-(drawX + cardW/2f), -centerY, 0);

            int drawCardX = (int)drawX;
            int drawCardY = centerY - cardH/2;

            g.fill(drawCardX, drawCardY, drawCardX + cardW, drawCardY + cardH, HudAnimUtil.withAlpha(0x111111, (int)(cardAlpha * 0.8f)));
            g.fillGradient(drawCardX, drawCardY, drawCardX + cardW, drawCardY + cardH, HudAnimUtil.withAlpha(themeC, (int)(cardAlpha * 0.2f)), 0);

            int coreColor = themeC & 0xFFFFFF;
            int topAlpha = cardAlpha;
            int botAlpha = (int)(cardAlpha * 0.15f);
            int colorTop = coreColor | (topAlpha << 24);
            int colorBot = coreColor | (botAlpha << 24);
            g.fillGradient(drawCardX, drawCardY, drawCardX + 4, drawCardY + cardH, colorTop, colorBot);
            int glowAlpha = (int)(topAlpha * 0.8f);
            int colorGlow = 0xFFFFFF | (glowAlpha << 24);
            g.fillGradient(drawCardX, drawCardY, drawCardX + 1, drawCardY + (cardH / 2), colorGlow, colorTop);

            if (popFactor > 0.1f && currentState != State.EXIT) {
                HudAnimUtil.drawFrame(g, drawCardX, drawCardY, cardW, cardH, 1, HudAnimUtil.withAlpha(themeC, (int)(safeAlpha * popFactor * 0.8f)));
            }

            g.pose().pushPose();
            g.pose().translate(drawX + cardW/2f, centerY, 0);
            g.pose().scale(iconScale, iconScale, 1f);
            g.pose().translate(-8, -8, 0);
            g.renderItem(item.getItemStack(), 0, 0);
            g.pose().popPose();

            g.pose().popPose();
        }

        float aimW = (cardW * 1.15f) + 8;
        float aimH = (cardH * 1.15f) + 8;

        if (currentState == State.HOLD || currentState == State.EXIT) {
            float holdT = Math.min(1.0f, (holdElapsed + exitElapsed) / 400f);
            float spring = 1.0f + 0.4f * (float)Math.exp(-holdT * 8f) * (float)Math.cos(holdT * 25f);
            aimW *= spring;
            aimH *= spring;
        }

        if (currentState == State.EXIT) {
            aimW += wipeOut * 300f;
            aimH += wipeOut * 300f;
        }

        int pulseA = (int)(safeAlpha * (0.6f + 0.4f * Math.sin(now / 150.0)));
        if (currentState == State.EXIT) pulseA = (int)(pulseA * (1.0f - wipeOut));

        if (pulseA > 5) {
            int crossColor = HudAnimUtil.withAlpha(targetThemeHighContrast, pulseA);
            int len = 10;
            int thick = 2;
            g.fill((int)(cx - aimW/2), (int)(centerY - aimH/2), (int)(cx - aimW/2 + len), (int)(centerY - aimH/2 + thick), crossColor);
            g.fill((int)(cx - aimW/2), (int)(centerY - aimH/2), (int)(cx - aimW/2 + thick), (int)(centerY - aimH/2 + len), crossColor);
            g.fill((int)(cx + aimW/2 - len), (int)(centerY - aimH/2), (int)(cx + aimW/2), (int)(centerY - aimH/2 + thick), crossColor);
            g.fill((int)(cx + aimW/2 - thick), (int)(centerY - aimH/2), (int)(cx + aimW/2), (int)(centerY - aimH/2 + len), crossColor);
            g.fill((int)(cx - aimW/2), (int)(centerY + aimH/2 - thick), (int)(cx - aimW/2 + len), (int)(centerY + aimH/2), crossColor);
            g.fill((int)(cx - aimW/2), (int)(centerY + aimH/2 - len), (int)(cx - aimW/2 + thick), (int)(centerY + aimH/2), crossColor);
            g.fill((int)(cx + aimW/2 - len), (int)(centerY + aimH/2 - thick), (int)(cx + aimW/2), (int)(centerY + aimH/2), crossColor);
            g.fill((int)(cx + aimW/2 - thick), (int)(centerY + aimH/2 - len), (int)(cx + aimW/2), (int)(centerY + aimH/2), crossColor);
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
        this.currentState = State.EXIT;
        this.exitElapsed = 0f;
    }
}