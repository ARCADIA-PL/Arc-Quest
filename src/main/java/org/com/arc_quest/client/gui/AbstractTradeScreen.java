package org.com.arc_quest.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.com.arc_quest.client.gui.render.QuestSplashRenderer;
import org.com.arc_quest.client.util.ClientCooldownHelper;
import org.com.arc_quest.dialogue.network.C2SDialogueChoicePacket;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.trade.api.TradeEntry;
import org.com.arc_quest.trade.api.TradeShopDefinition;
import org.com.arc_quest.trade.offer.ItemTradeOffer;
import org.com.arc_quest.trade.registry.TradeRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractTradeScreen extends Screen {

    protected final String shopId;
    protected final TradeShopDefinition shop;
    protected int[] purchaseCounts;
    protected int[] maxPurchases;
    protected long[] lastPurchaseTimes;
    protected long[] purchaseGameTimes;
    protected long[] purchaseDayTimes;
    protected int[] cooldownTypes;
    protected long[] cooldownValues;
    protected int[] resetTimeTicks;
    protected boolean[] visibility;
    protected boolean[] canBuyConditions;

    protected float transitionAnim = 0f;
    protected boolean isClosing = false;
    protected long lastRenderTime = 0;
    protected float dt = 0f;
    protected float suspendAlpha = 1.0f;
    protected float effectiveAlpha = 0f;

    protected float feedbackAnim = 0f;
    protected boolean feedbackSuccess = false;
    protected int lastClickedGi = -1;

    protected int hoveredTooltipIndex = -1;
    private float hoverTimer = 0f;
    private static final float HOVER_DELAY = 0.05f;

    private int activeTooltipIndex = -1;
    private float tooltipAlpha = 0f;
    private float animBgX = 0, animBgY = 0, animBgW = 0, animBgH = 0;
    private int animThemeColor = -1;
    private float animProgress = 0f;
    private float feedbackScale = 1.0f;
    private float feedbackShake = 0f;

    public AbstractTradeScreen(String title, String shopId, int[] purchaseCounts, int[] maxPurchases,
                               long[] lastPurchaseTimes, long[] purchaseGameTimes, long[] purchaseDayTimes,
                               int[] cooldownTypes, long[] cooldownValues, int[] resetTimeTicks, boolean[] visibility) {
        this(title, shopId, purchaseCounts, maxPurchases, lastPurchaseTimes, purchaseGameTimes, purchaseDayTimes,
                cooldownTypes, cooldownValues, resetTimeTicks, visibility, new boolean[0]);
    }

    public AbstractTradeScreen(String title, String shopId, int[] purchaseCounts, int[] maxPurchases,
                               long[] lastPurchaseTimes, long[] purchaseGameTimes, long[] purchaseDayTimes,
                               int[] cooldownTypes, long[] cooldownValues, int[] resetTimeTicks, boolean[] visibility,
                               boolean[] canBuyConditions) {
        super(Component.translatable(title));
        this.shopId = shopId;
        this.shop = TradeRegistry.get(shopId);
        this.purchaseCounts = purchaseCounts;
        this.maxPurchases = maxPurchases;
        this.lastPurchaseTimes = lastPurchaseTimes;
        this.purchaseGameTimes = purchaseGameTimes;
        this.purchaseDayTimes = purchaseDayTimes;
        this.cooldownTypes = cooldownTypes;
        this.cooldownValues = cooldownValues;
        this.resetTimeTicks = resetTimeTicks;
        this.visibility = visibility;
        this.canBuyConditions = canBuyConditions != null ? canBuyConditions : new boolean[0];
    }

    @Override
    protected void init() {
        super.init();
        if (this.lastRenderTime == 0) {
            this.transitionAnim = 0f;
            this.suspendAlpha = 1.0f;
        }
    }

    protected int getThemeColorForEntry(TradeEntry entry) {
        int entryColor = entry.getThemeColor();
        return (entryColor != -1) ? entryColor : (shop != null ? shop.getThemeColor() : 0xFFFFFF);
    }

    protected int lerpColor(int c1, int c2, float t) {
        int r1 = (c1 >> 16) & 0xFF; int g1 = (c1 >> 8) & 0xFF; int b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF; int g2 = (c2 >> 8) & 0xFF; int b2 = c2 & 0xFF;
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (r << 16) | (g << 8) | b;
    }

    protected void playClick() {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    public void onTradeSuccess() {
        feedbackSuccess = true;
        feedbackAnim = 1f;
        feedbackScale = 1.15f;
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f));
    }

    public void onTradeFail(String key) {
        feedbackSuccess = false;
        feedbackAnim = 1f;
        feedbackShake = 6f; 
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1f));
    }

    public String getShopId() { return shopId; }

    public void updateData(int[] purchaseCounts, int[] maxPurchases, long[] lastPurchaseTimes, long[] purchaseGameTimes, long[] purchaseDayTimes, int[] cooldownTypes, long[] cooldownValues, int[] resetTimeTicks, boolean[] visibility) {
        updateData(purchaseCounts, maxPurchases, lastPurchaseTimes, purchaseGameTimes, purchaseDayTimes, cooldownTypes, cooldownValues, resetTimeTicks, visibility, new boolean[0]);
    }

    public void updateData(int[] purchaseCounts, int[] maxPurchases, long[] lastPurchaseTimes, long[] purchaseGameTimes, long[] purchaseDayTimes, int[] cooldownTypes, long[] cooldownValues, int[] resetTimeTicks, boolean[] visibility, boolean[] canBuyConditions) {
        this.purchaseCounts = purchaseCounts; this.maxPurchases = maxPurchases; this.lastPurchaseTimes = lastPurchaseTimes;
        this.purchaseGameTimes = purchaseGameTimes; this.purchaseDayTimes = purchaseDayTimes; this.cooldownTypes = cooldownTypes;
        this.cooldownValues = cooldownValues; this.resetTimeTicks = resetTimeTicks; this.visibility = visibility;
        this.canBuyConditions = canBuyConditions != null ? canBuyConditions : new boolean[0];

        if (activeTooltipIndex != -1 && tooltipAlpha > 0.1f) {
            TradeEntry entry = getVisibleEntry(activeTooltipIndex);
            if (entry != null) {
                int gi = new ArrayList<>(shop.getAllEntries()).indexOf(entry);
                TooltipData target = calcTooltipData(entry, gi, width / 2, height / 2);
                animBgW = 0;
            }
        }
    }

    @Override
    public void onClose() {
        if (!isClosing) {
            isClosing = true;
            ArcQuestNetwork.sendDialogueChoice(new C2SDialogueChoicePacket(C2SDialogueChoicePacket.RESTORE_DIALOGUE));
        }
    }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (QuestSplashRenderer.isActive()) return true;
        if (k == 256 || k == 69) { onClose(); return true; }
        return super.keyPressed(k, s, m);
    }

    protected abstract void renderContent(GuiGraphics g, int mx, int my, float pt);
    protected abstract int getHoveredEntryIndex(int mx, int my);
    protected abstract TradeEntry getVisibleEntry(int index);
    protected abstract float getOpenAnimSpeed();

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        float realDt = (now - lastRenderTime) / 1000f;
        lastRenderTime = now;
        if (realDt > 0.1f) realDt = 0.1f;

        if (QuestSplashRenderer.isActive()) {
            suspendAlpha = Math.max(0f, suspendAlpha - realDt * 6f);
            dt = 0f;
        } else {
            suspendAlpha = Math.min(1f, suspendAlpha + realDt * 4f);
            dt = realDt;
        }

        transitionAnim = QuestAnimUtil.lerp(transitionAnim, isClosing ? 0f : 1f, isClosing ? 0.14f : getOpenAnimSpeed(), dt);
        if (isClosing && transitionAnim <= 0.01f) {
            if (minecraft != null) {
                minecraft.setScreen(null);
            }
            return;
        }

        effectiveAlpha = transitionAnim * suspendAlpha;
        if (feedbackAnim > 0) feedbackAnim = Math.max(0, feedbackAnim - dt * 2.5f);

        int safeAlpha = (int) (255 * effectiveAlpha);
        g.fill(0, 0, this.width, this.height, ((int) (140 * effectiveAlpha) << 24));

        if (safeAlpha <= 5) return;

        renderContent(g, mx, my, pt);

        int newHoveredIndex = getHoveredEntryIndex(mx, my);

        if (newHoveredIndex != hoveredTooltipIndex) {
            if (newHoveredIndex != -1 && tooltipAlpha > 0.5f) {
                hoveredTooltipIndex = newHoveredIndex;
                activeTooltipIndex = newHoveredIndex;
                hoverTimer = HOVER_DELAY;
            } else {
                hoveredTooltipIndex = newHoveredIndex;
                hoverTimer = 0f;
            }
        }

        if (hoveredTooltipIndex != -1 && !isClosing && transitionAnim >= 0.9f) {
            if (hoverTimer < HOVER_DELAY) hoverTimer += dt;
            if (hoverTimer >= HOVER_DELAY) activeTooltipIndex = hoveredTooltipIndex;
        } else {
            hoverTimer = 0f;
        }

        float targetAlpha = (hoveredTooltipIndex != -1 && hoverTimer >= HOVER_DELAY && !isClosing) ? 1f : 0f;
        tooltipAlpha += (targetAlpha - tooltipAlpha) * Math.min(1f, dt * 15f);

        if (tooltipAlpha > 0.02f && activeTooltipIndex != -1) {
            TradeEntry hoveredEntry = getVisibleEntry(activeTooltipIndex);
            if (hoveredEntry != null) {
                int gi = new ArrayList<>(shop.getAllEntries()).indexOf(hoveredEntry);
                renderMorphingTooltip(g, hoveredEntry, gi, mx, my);
            }
        } else {
            animBgW = 0;
            activeTooltipIndex = -1;
        }
    }

    private class TooltipData {
        int x, y, w, h;
        List<FormattedCharSequence> descLines;
        boolean onCd;
        int purchases, maxP;
        int themeColor;
    }

    private TooltipData calcTooltipData(TradeEntry entry, int gi, int mx, int my) {
        TooltipData d = new TooltipData();
        d.themeColor = getThemeColorForEntry(entry);
        d.maxP = entry.getMaxPurchases();
        d.purchases = gi >= 0 && gi < purchaseCounts.length ? purchaseCounts[gi] : 0;
        d.onCd = (gi >= 0) && (gi < lastPurchaseTimes.length) && ClientCooldownHelper.isOnCooldown(
                lastPurchaseTimes[gi], gi >= 0 && gi < purchaseGameTimes.length ? purchaseGameTimes[gi] : 0,
                gi >= 0 && gi < purchaseDayTimes.length ? purchaseDayTimes[gi] : 0,
                cooldownTypes[gi], cooldownValues[gi], resetTimeTicks[gi]);

        int padding = 10;
        d.descLines = entry.getDescription() != null ? font.split(entry.getDescription(), 180) : new ArrayList<>();

        int titleW = font.width(entry.getDisplayName());
        int totalW = Math.max(140, titleW + 32);
        for(var line : d.descLines) totalW = Math.max(totalW, font.width(line));
        d.w = totalW + padding * 2;

        int totalH = padding * 2 + 16;
        if (!d.descLines.isEmpty()) totalH += 6 + d.descLines.size() * font.lineHeight;
        if (d.maxP > 0) totalH += 18;
        if (d.onCd) totalH += font.lineHeight + 4;
        d.h = totalH;

        d.x = mx + 10;
        d.y = my + 14;
        if (d.x + d.w > width) d.x = mx - d.w - 6;
        if (d.y + d.h > height) d.y = my - d.h - 6;
        return d;
    }

    private void renderMorphingTooltip(GuiGraphics g, TradeEntry entry, int gi, int mx, int my) {
        TooltipData target = calcTooltipData(entry, gi, mx, my);

        if (!isClosing) {
            if (animBgW == 0 || Math.abs(animBgW - target.w) > 20) {
                animBgX = target.x; animBgY = target.y;
                animBgW = target.w; animBgH = target.h;
                animThemeColor = target.themeColor;
            } else {
                float morphSpeed = 15f;
                animBgX += (target.x - animBgX) * Math.min(1f, dt * morphSpeed);
                animBgY += (target.y - animBgY) * Math.min(1f, dt * morphSpeed);
                animBgW += (target.w - animBgW) * Math.min(1f, dt * morphSpeed);
                animBgH += (target.h - animBgH) * Math.min(1f, dt * morphSpeed);
                animThemeColor = lerpColor(animThemeColor, target.themeColor, Math.min(1f, dt * 10f));
            }
        }

        float targetProgress = target.maxP > 0 ? Math.min(1f, (float)target.purchases / target.maxP) : 0f;
        animProgress += (targetProgress - animProgress) * Math.min(1f, dt * 8f);

        if (feedbackScale > 1.0f) {
            feedbackScale += (1.0f - feedbackScale) * Math.min(1f, dt * 12f);
            if (Math.abs(feedbackScale - 1.0f) < 0.01f) feedbackScale = 1.0f;
        }

        
        int currentShake = 0;
        if (Math.abs(feedbackShake) > 0.1f) {
            currentShake = (int) (Math.sin(Util.getMillis() / 30.0) * feedbackShake);
            feedbackShake *= Math.max(0, 1f - dt * 15f);
        } else {
            feedbackShake = 0f;
        }

        float scale = isClosing ? QuestAnimUtil.easeInCubic(tooltipAlpha) : QuestAnimUtil.easeOutCubic(tooltipAlpha);
        if (scale < 0.01f) return;

        int drawX = (int)animBgX + currentShake; 
        int drawY = (int)animBgY;
        int drawW = (int)animBgW;
        int drawH = (int)animBgH;

        float finalScale = isClosing ? scale : (scale * feedbackScale);

        g.pose().pushPose();

        
        float centerX = drawX + drawW / 2f;
        float centerY = drawY + drawH / 2f;
        g.pose().translate(centerX, centerY, 0);
        g.pose().scale(finalScale, finalScale, 1f);
        g.pose().translate(-centerX, -centerY, 0);

        int bgA = (int) (0xDD * tooltipAlpha);
        int borderA = (int) (0xFF * tooltipAlpha);
        int currentThemeColor = animThemeColor;

        
        if (feedbackAnim > 0 && gi == lastClickedGi) {
            borderA = Math.min(255, borderA + (int)(180 * feedbackAnim));
            currentThemeColor = lerpColor(animThemeColor, feedbackSuccess ? 0x55FF55 : 0xFF5555, feedbackAnim);
        }

        int borderColor = (borderA << 24) | (currentThemeColor & 0xFFFFFF);

        g.fill(drawX, drawY, drawX + drawW, drawY + drawH, (bgA << 24) | 0x0A0A10);
        QuestAnimUtil.drawFrame(g, drawX, drawY, drawW, drawH, 1, borderColor);

        
        boolean useScissor = Math.abs(finalScale - 1.0f) < 0.01f && currentShake == 0;
        if (useScissor) {
            g.enableScissor(drawX, drawY, drawX + drawW, drawY + drawH);
        }

        int padding = 10;
        int currentY = drawY + padding;
        ItemStack icon = getIconStackForEntry(entry);
        int safeAlpha = (int)(255 * scale);
        int titleColor = QuestAnimUtil.withAlpha(animThemeColor, safeAlpha);

        if (entry.getIconOverride() != null || !icon.isEmpty()) {
            if (entry.getIconOverride() != null) {
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1f, 1f, 1f, isClosing ? 1f : tooltipAlpha);
                g.blit(entry.getIconOverride(), drawX + padding, currentY, 0, 0, 16, 16, 16, 16);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            } else {
                g.renderItem(icon, drawX + padding, currentY);
            }
            g.drawString(font, entry.getDisplayName(), drawX + padding + 22, currentY + 4, titleColor, true);
        } else {
            g.drawString(font, entry.getDisplayName(), drawX + padding, currentY + 4, titleColor, true);
        }
        currentY += 20;

        if (!target.descLines.isEmpty() || target.maxP > 0 || target.onCd) {
            g.fill(drawX + padding, currentY, drawX + target.w - padding, currentY + 1, QuestAnimUtil.withAlpha(animThemeColor, (int)(safeAlpha * 0.3f)));
            currentY += 5;
        }

        if (!target.descLines.isEmpty()) {
            for (var line : target.descLines) {
                g.drawString(font, line, drawX + padding, currentY, QuestAnimUtil.withAlpha(0xBBBBBB, safeAlpha), true);
                currentY += font.lineHeight;
            }
            currentY += 4;
        }

        if (target.maxP > 0) {
            String limitStr = Component.translatable("arc_quest.gui.trade.tooltip.limit", target.purchases, target.maxP).getString();
            g.drawString(font, limitStr, drawX + padding, currentY, QuestAnimUtil.withAlpha(0xDDDDDD, safeAlpha), true);
            currentY += font.lineHeight + 2;

            float progress = animProgress;
            int barW = target.w - padding * 2;
            int barH = 3;
            g.fill(drawX + padding, currentY, drawX + padding + barW, currentY + barH, QuestAnimUtil.withAlpha(0x333333, safeAlpha));

            int barColor;
            if (progress >= 0.99f) barColor = 0xAA3333;
            else if (progress >= 0.75f) barColor = 0xDD9933;
            else barColor = 0x33AA33;

            if (progress > 0.01f) {
                int filledW = (int)(barW * progress);
                g.fill(drawX + padding, currentY, drawX + padding + filledW, currentY + barH, QuestAnimUtil.withAlpha(barColor, safeAlpha));
                if (filledW > 2) {
                    g.fill(drawX + padding + filledW - 2, currentY, drawX + padding + filledW, currentY + barH, QuestAnimUtil.withAlpha(0xFFFFFF, (int)(safeAlpha * 0.6f)));
                }
            }
            currentY += barH + 6;
        }

        if (target.onCd) {
            String cdText = ClientCooldownHelper.getCooldownText(
                    lastPurchaseTimes[gi], purchaseGameTimes[gi], purchaseDayTimes[gi],
                    cooldownTypes[gi], cooldownValues[gi], resetTimeTicks[gi]);
            g.drawString(font, Component.translatable("arc_quest.gui.trade.tooltip.cooldown", cdText).getString(), drawX + padding, currentY, QuestAnimUtil.withAlpha(0xFF5555, safeAlpha), true);
        }

        if (useScissor) {
            g.disableScissor();
        }
        g.pose().popPose();
    }

    protected ItemStack getIconStackForEntry(TradeEntry entry) {
        if (!entry.getRewards().isEmpty() && entry.getRewards().get(0) instanceof ItemTradeOffer ito) return new ItemStack(ito.getItem(), Math.min(ito.getCount(), 64));
        if (!entry.getCosts().isEmpty() && entry.getCosts().get(0) instanceof ItemTradeOffer ito) return new ItemStack(ito.getItem(), Math.min(ito.getCount(), 64));
        return ItemStack.EMPTY;
    }
}