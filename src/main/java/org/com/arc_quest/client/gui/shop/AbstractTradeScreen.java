package org.com.arc_quest.client.gui.shop;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
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
import org.com.arc_quest.trade.offer.ItemTradeOffer;
import org.com.arc_quest.trade.registry.TradeRegistry;
import org.jetbrains.annotations.NotNull;

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

    private int authorityRefreshTicker = 0;
    private TradeTooltipRenderer tooltipRenderer;

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
        if(tooltipRenderer == null) tooltipRenderer = new TradeTooltipRenderer(this, font);
    }

    // --- 公共 API 及 Getter ---
    public String getShopId() { return shopId; }
    public TradeShopDefinition getShop() { return shop; }
    public float getTransitionAnim() { return transitionAnim; }
    public float getDt() { return dt; }
    public float getEffectiveAlpha() { return effectiveAlpha; }
    public int getLastClickedGi() { return lastClickedGi; }
    public float getFeedbackAnim() { return feedbackAnim; }
    public boolean isFeedbackSuccess() { return feedbackSuccess; }

    public int getThemeColorForEntry(TradeEntry entry) {
        int c = entry.getThemeColor();
        return (c != -1) ? c : (shop != null ? shop.getThemeColor() : 0xFFFFFF);
    }

    public int lerpColor(int c1, int c2, float t) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int)(r1 + (r2 - r1) * t) << 16) | ((int)(g1 + (g2 - g1) * t) << 8) | (int)(b1 + (b2 - b1) * t);
    }

    public void playClick() { if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)); }

    public void onTradeSuccess() { feedbackSuccess = true; feedbackAnim = 1f; if(tooltipRenderer!=null) tooltipRenderer.triggerTradeSuccess(); }
    public void onTradeFail(S2COpenTradePacket.FailReason reason, String errorKey) { feedbackSuccess = false; feedbackAnim = 1f; if(tooltipRenderer!=null) tooltipRenderer.triggerTradeFail(); }

    public void refreshData() { if(tooltipRenderer!=null) tooltipRenderer.forceRefresh(); }

    @Override
    public void onClose() {
        if (!isClosing) {
            isClosing = true;
            ArcQuestNetwork.sendDialogueChoice(new C2SDialogueChoicePacket(C2SDialogueChoicePacket.RESTORE_DIALOGUE));
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    protected C2SRequestTradePacket.ScreenType getCurrentScreenType() { return this instanceof SimpleTradePanel ? C2SRequestTradePacket.ScreenType.SIMPLE : C2SRequestTradePacket.ScreenType.FULL; }

    @Override
    public void tick() {
        super.tick();
        if (isClosing || shop == null || minecraft == null || minecraft.player == null) return;
        if (++authorityRefreshTicker >= 10) {
            authorityRefreshTicker = 0;
            ArcQuestNetwork.CHANNEL.sendToServer(new C2SRequestTradeSyncPacket(shopId, getCurrentScreenType()));
        }
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (QuestSplashRenderer.isActive()) return true;
        if (k == 256 || k == 69) { onClose(); return true; }
        return super.keyPressed(k, s, m);
    }

    protected abstract void renderContent(GuiGraphics g, int mx, int my, float pt);
    protected abstract TradeEntry getHoveredEntry(int mx, int my);

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        float realDt = Math.min(0.1f, (now - lastRenderTime) / 1000f);
        lastRenderTime = now;

        if (QuestSplashRenderer.isActive()) {
            suspendAlpha = Math.max(0f, suspendAlpha - realDt * 6f);
            dt = 0f;
        } else {
            suspendAlpha = Math.min(1f, suspendAlpha + realDt * 4f);
            dt = realDt;
        }

        if (!isClosing) {
            transitionAnim = Math.min(1f, transitionAnim + dt / 0.18f);
        } else {
            transitionAnim = Math.max(0f, transitionAnim - dt / 0.12f);
            if (transitionAnim <= 0.001f && minecraft != null) {
                minecraft.setScreen(null);
                return;
            }
        }

        float transEase = isClosing
                ? HudAnimUtil.easeInCubic(transitionAnim)
                : HudAnimUtil.easeOutCubic(transitionAnim);
        effectiveAlpha = transEase * HudAnimUtil.easeOutCubic(suspendAlpha);

        if (feedbackAnim > 0) feedbackAnim = Math.max(0, feedbackAnim - dt * 2.5f);

        int safeAlpha = (int) (255 * effectiveAlpha);
        g.fill(0, 0, this.width, this.height, ((int) (140 * effectiveAlpha) << 24));
        if (safeAlpha <= 5) return;

        renderContent(g, mx, my, pt);
        renderTradeFailToast(g);

        if (tooltipRenderer != null) {
            tooltipRenderer.updateAndRender(g, getHoveredEntry(mx, my), mx, my, dt, isClosing);
        }
    }

    private void renderTradeFailToast(GuiGraphics g) {
        ClientTradeCache.FeedbackSnapshot feedback = ClientTradeCache.INSTANCE.feedbackSnapshot(shopId);
        if (feedback == null || (feedback.errorKey() == null && feedback.failReason() == null)) return;
        String msg = HudRenderUtil.resolveTradeFailMessage(feedback.errorKey(), feedback.failReason() != null ? feedback.failReason().name() : null).getString();
        if (msg.isEmpty()) return;

        int safeA = (int) (210 * effectiveAlpha);
        if (safeA <= 5) return;
        int w = font.width(msg) + 20, h = 18, x = (width - w) / 2, y = Math.max(8, height / 2 - 90);
        g.fill(x, y, x + w, y + h, HudAnimUtil.withAlpha(0x160A0A, safeA));
        HudAnimUtil.drawFrame(g, x, y, w, h, 1, HudAnimUtil.withAlpha(0xFF6666, safeA));
        g.drawCenteredString(font, msg, x + w / 2, y + 5, HudAnimUtil.withAlpha(0xFFD0D0, safeA));
    }

    public void drawAdaptiveIcon(GuiGraphics g, ResourceLocation loc, int x, int y, int w, int h, float alpha) {
        RenderSystem.enableBlend(); RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        g.blit(loc, x, y, w, h, 0f, 0f, 1, 1, 1, 1);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    public ItemStack getIconStackForEntry(TradeEntry entry) {
        if (!entry.getRewards().isEmpty() && entry.getRewards().get(0) instanceof ItemTradeOffer ito) return new ItemStack(ito.getItem(), Math.min(ito.getCount(), 64));
        if (!entry.getCosts().isEmpty() && entry.getCosts().get(0) instanceof ItemTradeOffer ito) return new ItemStack(ito.getItem(), Math.min(ito.getCount(), 64));
        return ItemStack.EMPTY;
    }
    public ItemStack getIconStackForOffer(ITradeOffer offer) { return offer instanceof ItemTradeOffer ito ? new ItemStack(ito.getItem(), Math.min(ito.getCount(), 64)) : ItemStack.EMPTY; }
}