package org.com.arc_quest.client.gui.shop;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.client.gui.HudRenderUtil;
import org.com.arc_quest.client.gui.render.QuestSplashRenderer;
import org.com.arc_quest.dialogue.network.C2SDialogueChoicePacket;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.trade.api.ITradeOffer;
import org.com.arc_quest.trade.api.TradeEntry;
import org.com.arc_quest.trade.api.TradeShopDefinition;
import org.com.arc_quest.trade.network.C2SRequestTradePacket;
import org.com.arc_quest.trade.network.C2SRequestTradeSyncPacket;
import org.com.arc_quest.trade.network.ClientTradeCache;
import org.com.arc_quest.trade.network.S2COpenTradePacket;
import org.com.arc_quest.trade.api.CostShortfallLine;
import org.com.arc_quest.trade.offer.ItemTradeOffer;
import org.com.arc_quest.trade.registry.TradeRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public abstract class AbstractTradeScreen extends Screen {

    protected final String shopId;
    protected final TradeShopDefinition shop;

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
    protected float shortfallTooltipTimer = 0f;
    private static final float SHORTFALL_TOOLTIP_DURATION = 1.6f;
    protected Component lastTradeFailMessage = Component.empty();
    protected float tradeFailMessageTimer = 0f;
    private static final float TRADE_FAIL_MESSAGE_DURATION = 1.8f;
    private int authorityRefreshTicker = 0;
    private static final int AUTHORITY_REFRESH_INTERVAL_TICKS = 10;

    public AbstractTradeScreen(String title, String shopId) {
        super(Component.translatable(title));
        this.shopId = shopId;
        this.shop = TradeRegistry.get(shopId);
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
    }

    public void onTradeFail(S2COpenTradePacket.FailReason reason, String errorKey) {
        feedbackSuccess = false;
        feedbackAnim = 1f;
        feedbackShake = 6f;
        this.lastTradeFailMessage = HudRenderUtil.resolveTradeFailMessage(errorKey, reason != null ? reason.name() : null);
        this.tradeFailMessageTimer = TRADE_FAIL_MESSAGE_DURATION;
        if (isCannotAffordFailure(reason, errorKey)) {
            shortfallTooltipTimer = SHORTFALL_TOOLTIP_DURATION;
        }
    }

    private static boolean isCannotAffordFailure(S2COpenTradePacket.FailReason reason, String errorKey) {
        if (reason == S2COpenTradePacket.FailReason.CANNOT_AFFORD) {
            return true;
        }
        if (errorKey == null || errorKey.isEmpty()) {
            return false;
        }
        String key = errorKey.toLowerCase(Locale.ROOT);
        return key.contains("cannot_afford") || key.contains("afford") || key.contains("insufficient");
    }

    public String getShopId() { return shopId; }

    public void refreshData() {
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

    protected C2SRequestTradePacket.ScreenType getCurrentScreenType() {
        return this instanceof SimpleTradePanel
                ? C2SRequestTradePacket.ScreenType.SIMPLE
                : C2SRequestTradePacket.ScreenType.FULL;
    }

    @Override
    public void tick() {
        super.tick();

        if (isClosing || shop == null || minecraft == null || minecraft.player == null) {
            return;
        }

        authorityRefreshTicker++;
        if (authorityRefreshTicker < AUTHORITY_REFRESH_INTERVAL_TICKS) {
            return;
        }
        authorityRefreshTicker = 0;

        ArcQuestNetwork.CHANNEL.sendToServer(
                new C2SRequestTradeSyncPacket(shopId, getCurrentScreenType())
        );
    }
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

        if (shortfallTooltipTimer > 0f && dt > 0f) {
            shortfallTooltipTimer = Math.max(0f, shortfallTooltipTimer - dt);
        }
        if (tradeFailMessageTimer > 0f && dt > 0f) {
            tradeFailMessageTimer = Math.max(0f, tradeFailMessageTimer - dt);
        }

        transitionAnim = HudAnimUtil.lerp(transitionAnim, isClosing ? 0f : 1f, isClosing ? 0.14f : getOpenAnimSpeed(), dt);
        if (isClosing && transitionAnim <= 0.01f) {
            if (minecraft != null) minecraft.setScreen(null);
            return;
        }

        effectiveAlpha = transitionAnim * suspendAlpha;
        if (feedbackAnim > 0) feedbackAnim = Math.max(0, feedbackAnim - dt * 2.5f);

        int safeAlpha = (int) (255 * effectiveAlpha);
        g.fill(0, 0, this.width, this.height, ((int) (140 * effectiveAlpha) << 24));

        if (safeAlpha <= 5) return;

        renderContent(g, mx, my, pt);
        renderTradeFailToast(g);

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

    private void renderTradeFailToast(GuiGraphics g) {
        if (tradeFailMessageTimer <= 0f || lastTradeFailMessage == null || lastTradeFailMessage.getString().isEmpty()) {
            return;
        }

        float alpha = Math.min(1f, tradeFailMessageTimer / TRADE_FAIL_MESSAGE_DURATION);
        int safeA = (int) (210 * alpha * effectiveAlpha);
        if (safeA <= 5) {
            return;
        }

        String msg = lastTradeFailMessage.getString();
        int padX = 10;
        int w = font.width(msg) + padX * 2;
        int h = 18;
        int x = (width - w) / 2;
        int y = Math.max(8, height / 2 - 90);

        g.fill(x, y, x + w, y + h, HudAnimUtil.withAlpha(0x160A0A, safeA));
        HudAnimUtil.drawFrame(g, x, y, w, h, 1, HudAnimUtil.withAlpha(0xFF6666, safeA));
        g.drawCenteredString(font, msg, x + w / 2, y + 5, HudAnimUtil.withAlpha(0xFFD0D0, safeA));
    }

    private static class TooltipData {
        int x, y, w, h;
        List<FormattedCharSequence> descLines;
        boolean onCd;
        int purchases, maxP;
        int themeColor;

        boolean showShortfall;
        List<CostShortfallLine> shortfalls;
    }

    private TooltipData calcTooltipData(TradeEntry entry, int gi, int mx, int my) {
        TooltipData d = new TooltipData();
        d.themeColor = getThemeColorForEntry(entry);

        ClientTradeCache cache = ClientTradeCache.INSTANCE;
        d.maxP = entry.getMaxPurchases();
        d.purchases = cache.getPurchaseCount(shopId, gi);
        d.onCd = cache.isOnCooldown(shopId, gi);

        int padding = 10;
        d.descLines = entry.getDescription() != null ? font.split(entry.getDescription(), 180) : new ArrayList<>();

        d.shortfalls = ClientTradeCache.INSTANCE.getShortfall(shopId, entry.getEntryId());
        d.showShortfall = shortfallTooltipTimer > 0f && !d.shortfalls.isEmpty();

        int titleW = font.width(entry.getDisplayName());
        int totalW = Math.max(188, titleW + 40);

        if (d.showShortfall) {
            totalW = Math.max(totalW, font.width(Component.translatable("arc_quest.gui.trade.tooltip.shortfall_summary")) + 40);
            for (CostShortfallLine sf : d.shortfalls) {
                int lineW = font.width(sf.label()) + font.width("-" + sf.missing()) + 50;
                totalW = Math.max(totalW, lineW);
            }
        } else {
            for (var line : d.descLines) totalW = Math.max(totalW, font.width(line));
        }
        d.w = totalW + padding * 2;

        int totalH = padding * 2 + 16;
        if (d.showShortfall) {
            totalH += 18 + (d.shortfalls.size() * 18);
        } else {
            if (!d.descLines.isEmpty()) totalH += 6 + d.descLines.size() * font.lineHeight;
            if (d.maxP > 0) totalH += 18;
            if (d.onCd) totalH += font.lineHeight + 4;
        }
        d.h = totalH;

        int yOffset = 18;
        d.x = mx - (d.w / 2);
        if (d.x < 5) d.x = 5;
        if (d.x + d.w > width - 5) d.x = width - d.w - 5;
        d.y = my + yOffset;
        if (d.y + d.h > height - 5) d.y = my - d.h - yOffset;
        if (d.y < 5) d.y = 5;

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

        float scale = isClosing ? HudAnimUtil.easeInCubic(tooltipAlpha) : HudAnimUtil.easeOutCubic(tooltipAlpha);
        if (scale < 0.01f) return;

        int drawX = (int)animBgX + currentShake;
        int drawY = (int)animBgY;
        int drawW = (int)animBgW;
        int drawH = (int)animBgH;

        float finalScale = isClosing ? scale : (scale * feedbackScale);

        g.pose().pushPose();
        g.pose().translate(0, 0, 400f);

        float centerX = drawX + drawW / 2f;
        float centerY = drawY + drawH / 2f;
        g.pose().translate(centerX, centerY, 0);
        g.pose().scale(finalScale, finalScale, 1f);
        g.pose().translate(-centerX, -centerY, 0);

        int baseA = (int) (0x99 * tooltipAlpha);
        int borderA = (int) (0xFF * tooltipAlpha);
        int currentThemeColor = animThemeColor;

        if (gi == lastClickedGi) {
            if (feedbackSuccess && feedbackAnim > 0) {
                borderA = Math.min(255, borderA + (int)(180 * feedbackAnim));
                currentThemeColor = lerpColor(animThemeColor, 0x55FF55, feedbackAnim);
            } else if (!feedbackSuccess && (feedbackAnim > 0 || target.showShortfall)) {
                float syncBreath = (float) (Math.sin(Util.getMillis() / 150.0) * 0.5 + 0.5);
                float intensity = Math.max(feedbackAnim, target.showShortfall ? (syncBreath * 0.6f + 0.4f) : 0f);

                borderA = Math.min(255, borderA + (int)(180 * intensity));
                currentThemeColor = lerpColor(animThemeColor, 0xFF3333, intensity);
            }
        }

        int borderColor = (borderA << 24) | (currentThemeColor & 0xFFFFFF);

        g.fill(drawX, drawY, drawX + drawW, drawY + drawH, (baseA << 24) | 0x050508);
        int glowTop = HudAnimUtil.withAlpha(currentThemeColor, (int)(65 * tooltipAlpha));
        int glowBot = HudAnimUtil.withAlpha(currentThemeColor, 0);
        g.fillGradient(drawX, drawY, drawX + drawW, drawY + drawH, glowTop, glowBot);

        HudAnimUtil.drawFrame(g, drawX, drawY, drawW, drawH, 1, borderColor);

        boolean useScissor = Math.abs(finalScale - 1.0f) < 0.01f && currentShake == 0;
        if (useScissor) g.enableScissor(drawX, drawY, drawX + drawW, drawY + drawH);

        int padding = 10;
        int currentY = drawY + padding;

        int safeAlpha = (int)(255 * scale);
        int titleColor = HudAnimUtil.withAlpha(animThemeColor, safeAlpha);

        ResourceLocation iconLoc = entry.getRewardIcon();
        if (iconLoc != null) {
            drawAdaptiveIcon(g, iconLoc, drawX + padding, currentY, 16, 16, scale);
            g.drawString(font, entry.getDisplayName(), drawX + padding + 22, currentY + 4, titleColor, true);
        } else {
            ItemStack stack = getIconStackForEntry(entry);
            if (!stack.isEmpty()) {
                g.renderItem(stack, drawX + padding, currentY);
                g.drawString(font, entry.getDisplayName(), drawX + padding + 22, currentY + 4, titleColor, true);
            } else {
                g.drawString(font, entry.getDisplayName(), drawX + padding, currentY + 4, titleColor, true);
            }
        }
        currentY += 20;

        // === 中间分隔线与警示头 ===
        if (!target.descLines.isEmpty() || target.maxP > 0 || target.onCd || target.showShortfall) {
            if (target.showShortfall) {
                // 机能风红色警告分割线：长暗红底槽 + 左侧亮红指示标
                g.fill(drawX + padding, currentY, drawX + target.w - padding, currentY + 1, HudAnimUtil.withAlpha(0xFF3333, (int)(safeAlpha * 0.2f)));
                g.fill(drawX + padding, currentY, drawX + padding + 40, currentY + 1, HudAnimUtil.withAlpha(0xFF3333, safeAlpha));
                currentY += 6;

                // 提取摘要文本作为醒目标题
                Component summaryTitle = Component.translatable("arc_quest.gui.trade.tooltip.shortfall_summary");
                g.drawString(font, summaryTitle, drawX + padding, currentY, HudAnimUtil.withAlpha(0xFF5555, safeAlpha), true);
                currentY += 12;
            } else {
                // 普通主题色分割线
                g.fill(drawX + padding, currentY, drawX + target.w - padding, currentY + 1, HudAnimUtil.withAlpha(animThemeColor, (int)(safeAlpha * 0.3f)));
                currentY += 6;
            }
        }

        if (target.showShortfall) {
            for (CostShortfallLine sf : target.shortfalls) {
                g.fill(drawX + padding, currentY + 3, drawX + padding + 2, currentY + 7, HudAnimUtil.withAlpha(0xFF4444, safeAlpha));

                g.drawString(font, sf.label(), drawX + padding + 6, currentY, HudAnimUtil.withAlpha(0xDDDDDD, safeAlpha), true);

                if (sf.missing() > 0) {
                    String missingTxt = "-" + sf.missing();
                    int mWidth = font.width(missingTxt);
                    g.drawString(font, missingTxt, drawX + target.w - padding - mWidth, currentY, HudAnimUtil.withAlpha(0xFF3333, safeAlpha), true);
                }

                currentY += 10;

                if (sf.required() > 0) {
                    String metaTxt = sf.owned() + " / " + sf.required();

                    g.pose().pushPose();
                    g.pose().translate(drawX + padding + 6, currentY, 0);
                    g.pose().scale(0.8f, 0.8f, 1f);
                    g.drawString(font, metaTxt, 0, 0, HudAnimUtil.withAlpha(0x888888, safeAlpha), false);
                    g.pose().popPose();

                    int metaWidth = (int)(font.width(metaTxt) * 0.8f);
                    int barX = drawX + padding + 6 + metaWidth + 6;
                    int barW = target.w - padding * 2 - (barX - drawX) - 5;

                    if (barW > 10) {
                        float pct = Math.min(1f, (float)sf.owned() / sf.required());
                        int fillW = (int)(barW * pct);
                        g.fill(barX, currentY + 2, barX + barW, currentY + 4, HudAnimUtil.withAlpha(0x442222, safeAlpha));
                        if (fillW > 0) {
                            g.fill(barX, currentY + 2, barX + fillW, currentY + 4, HudAnimUtil.withAlpha(0xAA3333, safeAlpha));
                        }
                    }
                }
                currentY += 8;
            }
            currentY += 2;
        }
        else if (!target.descLines.isEmpty()) {
            for (var line : target.descLines) {
                g.drawString(font, line, drawX + padding, currentY, HudAnimUtil.withAlpha(0xBBBBBB, safeAlpha), true);
                currentY += font.lineHeight;
            }
            currentY += 4;
        }


        if (!target.showShortfall && target.maxP > 0) {
            String limitStr = Component.translatable("arc_quest.gui.trade.tooltip.limit", target.purchases, target.maxP).getString();
            g.drawString(font, limitStr, drawX + padding, currentY, HudAnimUtil.withAlpha(0xDDDDDD, safeAlpha), true);
            currentY += font.lineHeight + 2;

            float progress = animProgress;
            int barW = target.w - padding * 2;
            int barH = 3;
            g.fill(drawX + padding, currentY, drawX + padding + barW, currentY + barH, HudAnimUtil.withAlpha(0x333333, safeAlpha));

            int barColor = (progress >= 0.99f) ? 0xAA3333 : (progress >= 0.75f ? 0xDD9933 : 0x33AA33);

            if (progress > 0.01f) {
                int filledW = (int)(barW * progress);
                g.fill(drawX + padding, currentY, drawX + padding + filledW, currentY + barH, HudAnimUtil.withAlpha(barColor, safeAlpha));
                if (filledW > 2) {
                    g.fill(drawX + padding + filledW - 2, currentY, drawX + padding + filledW, currentY + barH, HudAnimUtil.withAlpha(0xFFFFFF, (int)(safeAlpha * 0.6f)));
                }
            }
            currentY += barH + 6;
        }

        if (!target.showShortfall && target.onCd) {
            String cdText = ClientTradeCache.INSTANCE.getCooldownText(shopId, gi);
            g.drawString(font, Component.translatable("arc_quest.gui.trade.tooltip.cooldown", cdText).getString(), drawX + padding, currentY, HudAnimUtil.withAlpha(0xFF5555, safeAlpha), true);
        }

        if (useScissor) g.disableScissor();
        g.pose().popPose();
    }

    protected void drawAdaptiveIcon(GuiGraphics g, ResourceLocation loc, int x, int y, int w, int h, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        g.blit(loc, x, y, w, h, 0f, 0f, 1, 1, 1, 1);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    protected ItemStack getIconStackForEntry(TradeEntry entry) {
        if (!entry.getRewards().isEmpty() && entry.getRewards().get(0) instanceof ItemTradeOffer ito) return new ItemStack(ito.getItem(), Math.min(ito.getCount(), 64));
        if (!entry.getCosts().isEmpty() && entry.getCosts().get(0) instanceof ItemTradeOffer ito) return new ItemStack(ito.getItem(), Math.min(ito.getCount(), 64));
        return ItemStack.EMPTY;
    }

    protected ItemStack getIconStackForOffer(ITradeOffer offer) {
        if (offer instanceof ItemTradeOffer ito) return new ItemStack(ito.getItem(), Math.min(ito.getCount(), 64));
        return ItemStack.EMPTY;
    }
}