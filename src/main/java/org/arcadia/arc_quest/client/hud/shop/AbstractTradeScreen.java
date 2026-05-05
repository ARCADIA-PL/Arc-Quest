package org.arcadia.arc_quest.client.hud.shop;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.mutil.animation.ArcAnimClock;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;
import org.arcadia.arc_quest.client.hud.dialogue.DialogueScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.splash.ArcQuestSplashManager;
import org.arcadia.arc_quest.dialogue.network.C2SDialogueChoicePacket;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.network.C2SRequestTradePacket;
import org.arcadia.arc_quest.trade.network.C2SRequestTradeSyncPacket;
import org.arcadia.arc_quest.trade.network.ClientTradeCache;
import org.arcadia.arc_quest.trade.network.S2COpenTradePacket;
import org.arcadia.arc_quest.trade.offer.ItemTradeOffer;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public abstract class AbstractTradeScreen extends Screen {

    private static final int AUTHORITY_REFRESH_INTERVAL_TICKS = 40;
    public static Screen pendingParentScreen = null;
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
    // 记录是否已触发父界面的联动进出场动画
    protected boolean triggeredParentClose = false;
    protected boolean triggeredParentReopen = false;
    private int authorityRefreshTicker = 0;
    private TradeTooltipRenderer tooltipRenderer;
    private final Map<String, ItemStack> entryIconStackCache = new HashMap<>();
    private final Map<ITradeOffer, ItemStack> offerIconStackCache = new IdentityHashMap<>();

    public AbstractTradeScreen(String title, String shopId) {
        super(Component.translatable(title));
        this.shopId = shopId;
        this.shop = TradeRegistry.get(shopId);
    }

    public static void setParentScreen(Screen screen) {
        pendingParentScreen = screen;
    }

    @Override
    protected void init() {
        super.init();
        if (this.lastRenderTime == 0) {
            this.transitionAnim = 0f;
            this.suspendAlpha = 1.0f;
        }
        if (tooltipRenderer == null) tooltipRenderer = new TradeTooltipRenderer(this, font);
    }

    public String getShopId() {
        return shopId;
    }

    public TradeShopDefinition getShop() {
        return shop;
    }

    public float getTransitionAnim() {
        return transitionAnim;
    }

    public float getDt() {
        return dt;
    }

    public float getEffectiveAlpha() {
        return effectiveAlpha;
    }

    public int getLastClickedGi() {
        return lastClickedGi;
    }

    public float getFeedbackAnim() {
        return feedbackAnim;
    }

    public boolean isFeedbackSuccess() {
        return feedbackSuccess;
    }

    public int getThemeColorForEntry(TradeEntry entry) {
        int c = entry.getThemeColor();
        return (c != -1) ? c : (shop != null ? shop.getThemeColor() : 0xFFFFFF);
    }

    public int lerpColor(int c1, int c2, float t) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) (r1 + (r2 - r1) * t) << 16) | ((int) (g1 + (g2 - g1) * t) << 8) | (int) (b1 + (b2 - b1) * t);
    }

    public void playClick() {
        if (minecraft != null)
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    public void onTradeSuccess() {
        feedbackSuccess = true;
        feedbackAnim = 1f;
        if (tooltipRenderer != null) tooltipRenderer.triggerTradeSuccess();
    }

    public void onTradeFail(S2COpenTradePacket.FailReason reason, String errorKey) {
        feedbackSuccess = false;
        feedbackAnim = 1f;
        if (tooltipRenderer != null) tooltipRenderer.triggerTradeFail();
    }

    public void refreshData() {
        if (tooltipRenderer != null) tooltipRenderer.forceRefresh();
    }

    @Override
    public void onClose() {
        if (!isClosing) {
            isClosing = true;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected C2SRequestTradePacket.ScreenType getCurrentScreenType() {
        return this instanceof SimpleTradePanel ? C2SRequestTradePacket.ScreenType.SIMPLE : C2SRequestTradePacket.ScreenType.FULL;
    }

    @Override
    public void tick() {
        super.tick();
        if (isClosing || shop == null || minecraft == null || minecraft.player == null) return;
        if (++authorityRefreshTicker >= AUTHORITY_REFRESH_INTERVAL_TICKS) {
            authorityRefreshTicker = 0;
            ArcQuestNetwork.CHANNEL.sendToServer(new C2SRequestTradeSyncPacket(shopId, getCurrentScreenType()));
        }
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (ArcQuestSplashManager.isActive()) return true;
        if (k == 256 || k == 69) {
            onClose();
            return true;
        }
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

        if (ArcQuestSplashManager.isActive()) {
            suspendAlpha = Math.max(0f, suspendAlpha - realDt * 6f);
            dt = 0f;
        } else {
            suspendAlpha = Math.min(1f, suspendAlpha + realDt * 4f);
            dt = realDt;
        }

        // ================= 【架构师 3A 级无缝交叉渐变矩阵】 =================
        if (pendingParentScreen != null) {
            if (pendingParentScreen instanceof QuestJournalScreen qjs) {
                // 商店刚开启时，强制向背后默默渲染的日志界面下达“淡出”指令，避免穿模！
                if (!triggeredParentClose && !isClosing) {
                    qjs.onClose();
                    triggeredParentClose = true;
                }
                // 商店开始退出时，提前向日志界面下达“入场”指令，这样商店消散时日志已经无缝显现！
                if (isClosing && !triggeredParentReopen) {
                    qjs.triggerEntranceAnimation();
                    triggeredParentReopen = true;
                }
            } else if (pendingParentScreen instanceof DialogueScreen ds) {
                if (!triggeredParentClose && !isClosing) {
                    ds.startCloseAnimation();
                    triggeredParentClose = true;
                }
            }
            // 强行把父界面当作背景渲染，并通过传入 -999 阻断其任何鼠标判定！
            pendingParentScreen.render(g, -999, -999, pt);
        }
        // ====================================================================

        if (!isClosing) {
            transitionAnim = Math.min(1f, transitionAnim + dt / 0.35f);
        } else {
            transitionAnim = Math.max(0f, transitionAnim - dt / 0.25f);
            if (transitionAnim <= 0.001f && minecraft != null) {
                if (pendingParentScreen != null) {
                    if (pendingParentScreen instanceof DialogueScreen ds) {
                        ArcQuestNetwork.sendDialogueChoice(C2SDialogueChoicePacket.restore());
                        ds.resetSelectionState();
                        ds.init(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
                    }
                    // QuestJournalScreen 已经被提前触发了入场动画，直接恢复为其为主界面！
                    minecraft.setScreen(pendingParentScreen);
                    pendingParentScreen = null;
                } else {
                    minecraft.setScreen(null);
                }
                return;
            }
        }

        float transEase = isClosing
                ? ArcAnimClock.easeInCubic(transitionAnim)
                : ArcAnimClock.easeOutCubic(transitionAnim);
        effectiveAlpha = transEase * ArcAnimClock.easeOutCubic(suspendAlpha);

        if (feedbackAnim > 0) {
            feedbackAnim = Math.max(0, feedbackAnim - dt * 0.5f);
            if (feedbackAnim <= 0) {
                ClientTradeCache.INSTANCE.clearFeedback(shopId);
            }
        }

        int safeAlpha = (int) (255 * effectiveAlpha);

        // 渲染商店自身半透明遮罩
        g.fill(0, 0, this.width, this.height, ((int) (140 * effectiveAlpha) << 24));
        if (safeAlpha <= 5) return;

        renderContent(g, mx, my, pt);
        /*renderTradeFailToast(g);*/

        if (tooltipRenderer != null) {
            tooltipRenderer.updateAndRender(g, getHoveredEntry(mx, my), mx, my, dt, isClosing);
        }
    }

    private void renderTradeFailToast(GuiGraphics g) {
        ClientTradeCache.FeedbackSnapshot feedback = ClientTradeCache.INSTANCE.feedbackSnapshot(shopId);
        if (feedback == null || (feedback.errorKey() == null && feedback.failReason() == null)) return;
        String msg = resolveTradeFailMessage(feedback.errorKey(), feedback.failReason() != null ? feedback.failReason().name() : null).getString();
        if (msg.isEmpty()) return;

        int safeA = (int) (210 * effectiveAlpha);
        if (safeA <= 5) return;
        int w = font.width(msg) + 20, h = 18, x = (width - w) / 2, y = Math.max(8, height / 2 - 90);
        g.fill(x, y, x + w, y + h, ArcDrawUtil.withAlpha(0x160A0A, safeA));
        ArcDrawUtil.drawFrame(g, x, y, w, h, 1, ArcDrawUtil.withAlpha(0xFF6666, safeA));
        g.drawCenteredString(font, msg, x + w / 2, y + 5, ArcDrawUtil.withAlpha(0xFFD0D0, safeA));
    }

    private Component resolveTradeFailMessage(String errorKey, String rawReason) {
        String normalized = normalizeFailureKey(errorKey, rawReason);
        if (containsAny(normalized, "cooldown", "on_cooldown")) return Component.translatable("arc_quest.gui.gacha.btn.cooldown");
        if (containsAny(normalized, "limit", "max_purchase", "max_purchases", "maxed")) return Component.translatable("arc_quest.gui.trade.status.maxed");
        if (containsAny(normalized, "condition", "locked", "requirement", "blocked")) return Component.translatable("arc_quest.gui.trade.status.locked");
        if (containsAny(normalized, "cannot_afford", "insufficient", "shortfall", "not_enough")) return Component.translatable("arc_quest.gui.gacha.btn.insufficient_funds");
        return Component.translatable("arc_quest.gui.trade.error.shop_closed");
    }

    private String normalizeFailureKey(String primary, String fallback) {
        String key = primary;
        if (key == null || key.isEmpty()) key = fallback;
        return key == null ? "" : key.toLowerCase(java.util.Locale.ROOT);
    }

    private boolean containsAny(String source, String... needles) {
        for (String needle : needles) {
            if (source.contains(needle)) return true;
        }
        return false;
    }

    public void drawAdaptiveIcon(GuiGraphics g, ResourceLocation loc, int x, int y, int w, int h, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        g.blit(loc, x, y, w, h, 0f, 0f, 1, 1, 1, 1);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    public ItemStack getIconStackForEntry(TradeEntry entry) {
        return entryIconStackCache.computeIfAbsent(entry.getEntryId(), id -> createIconStackForEntry(entry));
    }

    private ItemStack createIconStackForEntry(TradeEntry entry) {
        if (!entry.getRewards().isEmpty() && entry.getRewards().get(0) instanceof ItemTradeOffer ito)
            return new ItemStack(ito.getItem(), Math.min(ito.getCount(), 64));
        if (!entry.getCosts().isEmpty() && entry.getCosts().get(0) instanceof ItemTradeOffer ito)
            return new ItemStack(ito.getItem(), Math.min(ito.getCount(), 64));
        return ItemStack.EMPTY;
    }

    public ItemStack getIconStackForOffer(ITradeOffer offer) {
        return offerIconStackCache.computeIfAbsent(offer, this::createIconStackForOffer);
    }

    private ItemStack createIconStackForOffer(ITradeOffer offer) {
        return offer instanceof ItemTradeOffer ito ? new ItemStack(ito.getItem(), Math.min(ito.getCount(), 64)) : ItemStack.EMPTY;
    }

    protected boolean closeIfClickedOutside(double mx, double my, int button, int x, int y, int w, int h) {
        if (button != 0 || isClosing || transitionAnim < 0.9f) return false;
        boolean inside = mx >= x && mx < x + w && my >= y && my < y + h;
        if (!inside) {
            onClose();
            return true;
        }
        return false;
    }
}