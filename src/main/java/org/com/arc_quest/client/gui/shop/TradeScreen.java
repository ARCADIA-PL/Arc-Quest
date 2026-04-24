package org.com.arc_quest.client.gui.shop;

import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.client.gui.dialogue.DialogueScreen;
import org.com.arc_quest.dialogue.network.C2SDialogueChoicePacket;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.trade.api.ITradeOffer;
import org.com.arc_quest.trade.api.TradeCategory;
import org.com.arc_quest.trade.api.TradeEntry;
import org.com.arc_quest.trade.network.C2SRequestTradePacket;
import org.com.arc_quest.trade.network.ClientTradeCache;
import org.com.arc_quest.trade.network.S2COpenTradePacket;

import java.util.ArrayList;
import java.util.List;

public class TradeScreen extends AbstractTradeScreen {

    private static Screen parentScreen;

    private static final int CARD_HEIGHT = 48;
    private static final int CAT_WIDTH = 130;

    private final List<TradeEntry> allEntries;
    private List<TradeEntry> filteredEntries;

    private int selectedCategoryIndex = 0;
    private float selectedCatSlide = 0f;
    private double scrollOffset = 0;
    private double targetScroll = 0;

    private float[] catHoverAnims;
    private float[] entryHoverAnims;

    public TradeScreen(String shopId) {
        super("arc_quest.gui.trade.full_title", shopId);
        this.allEntries = shop != null ? new ArrayList<>(shop.getAllEntries()) : List.of();
        this.filteredEntries = new ArrayList<>(allEntries);
    }

    public static void setParentScreen(Screen screen) { parentScreen = screen; }

    @Override
    public void onClose() {
        if (parentScreen != null) {
            Minecraft mc = Minecraft.getInstance();
            if (parentScreen instanceof DialogueScreen ds) {
                ArcQuestNetwork.sendDialogueChoice(C2SDialogueChoicePacket.restore());
                ds.resetSelectionState();
                ds.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            }
            mc.setScreen(parentScreen);
            parentScreen = null;
        } else {
            super.onClose();
        }
    }

    @Override
    protected void init() {
        super.init();
        filterEntries();
        selectedCatSlide = selectedCategoryIndex;
    }

    @Override protected float getOpenAnimSpeed() { return 0.12f; }
    @Override protected TradeEntry getVisibleEntry(int index) { return (index >= 0 && index < filteredEntries.size()) ? filteredEntries.get(index) : null; }

    private void filterEntries() {
        filteredEntries = new ArrayList<>();
        List<TradeCategory> cats = shop != null ? shop.getCategories() : List.of();
        TradeCategory selected = (selectedCategoryIndex == 0 || cats.isEmpty()) ? null : cats.get(selectedCategoryIndex - 1);

        for (int i = 0; i < allEntries.size(); i++) {
            if (!ClientTradeCache.INSTANCE.isVisible(shopId, i)) continue;
            TradeEntry entry = allEntries.get(i);
            if (selected == null || selected.equals(entry.getCategory())) filteredEntries.add(entry);
        }

        targetScroll = 0; scrollOffset = 0;
        int catCount = shop != null ? shop.getCategories().size() + 1 : 1;
        if (catHoverAnims == null || catHoverAnims.length != catCount) catHoverAnims = new float[catCount];
        entryHoverAnims = new float[filteredEntries.size()];
    }

    private int panelW() { return Math.max(380, Math.min(800, (int)(width * 0.85f))); }
    private int panelH() { return Math.max(220, Math.min(600, (int)(height * 0.85f))); }

    private void clampScroll() {
        int maxScroll = Math.max(0, filteredEntries.size() * (CARD_HEIGHT + 8) - (panelH() - 36));
        targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float pt) {
        float easeProgress = (isClosing ? HudAnimUtil.easeInCubic(transitionAnim) : HudAnimUtil.easeOutCubic(transitionAnim)) * HudAnimUtil.easeOutCubic(suspendAlpha);
        int pw = panelW(), ph = panelH();
        int px = (width - pw) / 2, py = (height - ph) / 2;
        float slideOffset = (1f - easeProgress) * 200f;

        g.pose().pushPose();

        if (shop == null) { g.drawCenteredString(font, Component.translatable("arc_quest.gui.trade.error.shop_closed").getString(), width/2, height/2, HudAnimUtil.withAlpha(0xFF5555, (int)(255*effectiveAlpha))); g.pose().popPose(); return; }

        float titleScale = isClosing ? HudAnimUtil.easeInCubic(transitionAnim) : (0.95f + 0.05f * easeProgress);
        if (titleScale > 0.01f) {
            g.pose().pushPose();
            g.pose().translate(width / 2f, py + 16, 0); g.pose().scale(titleScale, titleScale, 1f); g.pose().translate(-width / 2f, -(py + 16), 0);
            g.drawCenteredString(font, shop.getDisplayName(), width / 2, py, HudAnimUtil.withAlpha(shop.getThemeColor(), (int)(255*effectiveAlpha)));
            g.pose().popPose();
        }

        clampScroll();
        scrollOffset += (targetScroll - scrollOffset) * Math.min(1.0, dt * 12.0);

        int lx = px - (int) slideOffset, ly = py + 36, lw = CAT_WIDTH, lh = ph - 36;
        int rx = px + CAT_WIDTH + 16 + (int) slideOffset, ry = py + 36, rw = pw - CAT_WIDTH - 16, rh = ph - 36;

        g.fill(lx, ly, lx + lw, ly + lh, HudAnimUtil.withAlpha(0x05050A, (int)(120 * effectiveAlpha)));
        HudAnimUtil.drawFrame(g, lx, ly, lw, lh, 1, HudAnimUtil.withAlpha(0xFFFFFF, (int)(100 * effectiveAlpha)));

        g.fill(rx, ry, rx + rw, ry + rh, HudAnimUtil.withAlpha(0x05050A, (int)(120 * effectiveAlpha)));
        HudAnimUtil.drawFrame(g, rx, ry, rw, rh, 1, HudAnimUtil.withAlpha(0xFFFFFF, (int)(100 * effectiveAlpha)));

        renderCategories(g, lx, ly, lw, lh, mx, my);
        renderEntries(g, rx, ry, rw, rh, mx, my);
        g.pose().popPose();
    }

    private void renderCategories(GuiGraphics g, int lx, int ly, int lw, int lh, int mx, int my) {
        selectedCatSlide = HudAnimUtil.lerp(selectedCatSlide, selectedCategoryIndex, 0.2f, dt);
        int hlY = ly + 10 + (int)(selectedCatSlide * 36);
        g.fill(lx + 4, hlY, lx + 7, hlY + 28, HudAnimUtil.withAlpha(selectedCategoryIndex == 0 ? shop.getThemeColor() : (shop.getCategories().isEmpty() ? shop.getThemeColor() : shop.getCategories().get(Math.max(0, selectedCategoryIndex-1)).getThemeColor()), (int)(255 * effectiveAlpha)));

        int cy = ly + 10;
        drawCatRow(g, lx, cy, lw, Component.translatable("arc_quest.trade.category.all").getString(), selectedCategoryIndex == 0, 0, mx, my);
        cy += 36;
        for (int i = 0; i < shop.getCategories().size(); i++) {
            drawCatRow(g, lx, cy, lw, shop.getCategories().get(i).getDisplayName().getString(), selectedCategoryIndex == i + 1, i + 1, mx, my);
            cy += 36;
        }
    }

    private void drawCatRow(GuiGraphics g, int x, int y, int w, String text, boolean sel, int idx, int mx, int my) {
        boolean hov = !isClosing && dt > 0 && mx >= x && mx < x + w && my >= y && my < y + 28;
        catHoverAnims[idx] = HudAnimUtil.step(catHoverAnims[idx], hov ? 1f : 0f, 8f, dt);
        float hEase = HudAnimUtil.easeOutCubic(catHoverAnims[idx]);

        float fastClose = isClosing ? Math.max(0f, (transitionAnim - 0.4f) / 0.6f) : 1.0f;
        float contentScale = isClosing ? HudAnimUtil.easeInCubic(fastClose) : 1.0f;

        int textX = x + 16 + (sel ? 6 : (int)(4 * hEase));
        int c = sel ? 0xFFFFFF : Math.round(150 + 105 * hEase);

        if (contentScale > 0.01f) {
            g.pose().pushPose();
            g.pose().translate(x + w/2f, y + 14, 0);
            g.pose().scale(contentScale, contentScale, 1f);
            g.pose().translate(-(x + w/2f), -(y + 14), 0);
            g.drawString(font, text, textX, y + 10, HudAnimUtil.withAlpha((c<<16)|(c<<8)|c, (int)(255*effectiveAlpha)), true);
            g.pose().popPose();
        }
    }

    private void renderEntries(GuiGraphics g, int rx, int ry, int rw, int rh, int mx, int my) {
        g.enableScissor(rx, ry, rx + rw, ry + rh);

        float fastClose = isClosing ? Math.max(0f, (transitionAnim - 0.4f) / 0.6f) : effectiveAlpha;
        float contentScale = isClosing ? HudAnimUtil.easeInCubic(fastClose) : 1.0f;

        ClientTradeCache cache = ClientTradeCache.INSTANCE;

        for (int i = 0; i < filteredEntries.size(); i++) {
            TradeEntry entry = filteredEntries.get(i);
            int gi = cache.getGlobalIndex(shopId, entry.getEntryId());
            if (gi == -1) continue;
            int drawY = ry + (int) (i * (CARD_HEIGHT + 8) - scrollOffset) + 8;
            if (drawY + CARD_HEIGHT < ry || drawY > ry + rh) continue;

            boolean hov = !isClosing && dt > 0 && mx >= rx && mx < rx + rw && my >= drawY && my < drawY + CARD_HEIGHT && my >= ry && my <= ry + rh;

            boolean onCd = cache.isEntryCoolingDown(shopId, gi, entry);
            boolean maxed = cache.isPurchaseLimitReached(shopId, gi, entry);
            boolean conditionNotMet = !onCd && !maxed && cache.isConditionBlocked(shopId, gi, entry);
            boolean canBuy = !onCd && !maxed && !conditionNotMet;

            entryHoverAnims[i] = HudAnimUtil.step(entryHoverAnims[i], hov && canBuy ? 1f : 0f, 6f, dt);
            float hEase = HudAnimUtil.easeOutCubic(entryHoverAnims[i]);

                        int bgA = (int) ((0x22 + 0x33 * hEase) * effectiveAlpha);
            int bdA = (int) ((0x44 + 0x66 * hEase) * effectiveAlpha);
            int bRgb = hov && canBuy ? getThemeColorForEntry(entry) : 0xFFFFFF;

            // 获取当前卡片是否处于"资金不足"警告期
            boolean hasShortfall = shortfallTooltipTimer > 0f && !ClientTradeCache.INSTANCE.getShortfall(shopId, entry.getEntryId()).isEmpty();

            // 完美的全局帧级同步
            if (gi == lastClickedGi) {
                if (feedbackSuccess && feedbackAnim > 0) {
                    bdA = Math.min(255, bdA + (int)(180 * feedbackAnim * effectiveAlpha));
                    bRgb = lerpColor(bRgb, 0x55FF55, feedbackAnim);
                } else if (!feedbackSuccess && (feedbackAnim > 0 || hasShortfall)) {
                    float syncBreath = (float) (Math.sin(Util.getMillis() / 150.0) * 0.5 + 0.5);
                    float intensity = Math.max(feedbackAnim, hasShortfall ? (syncBreath * 0.6f + 0.4f) : 0f);
                    
                    bdA = Math.min(255, bdA + (int)(180 * intensity * effectiveAlpha));
                    bRgb = lerpColor(bRgb, 0xFF3333, intensity);
                }
            }

            int cx = rx + 8, cy = drawY, cw = rw - 16, ch = CARD_HEIGHT;

            g.fill(cx, cy, cx + cw, cy + ch, (bgA << 24) | 0x05050A);
            HudAnimUtil.drawFrame(g, cx, cy, cw, ch, 1, (bdA << 24) | (bRgb & 0xFFFFFF));

            if (contentScale > 0.01f) {
                g.pose().pushPose();
                g.pose().translate(cx + cw/2f, cy + ch/2f, 0);
                float animScale = contentScale + hEase * 0.02f;
                g.pose().scale(animScale, animScale, 1f);
                g.pose().translate(-(cx + cw/2f), -(cy + ch/2f), 0);

                if (entry.getRewardIcon() != null) {
                    drawAdaptiveIcon(g, entry.getRewardIcon(), cx + 7, cy + 16, 16, 16, effectiveAlpha);
                } else {
                    ItemStack is = getIconStackForEntry(entry);
                    if (!is.isEmpty()) {
                        g.pose().pushPose(); g.pose().translate(cx + 12, cy + 16, 0); g.pose().scale(1.2f, 1.2f, 1f); g.renderItem(is, 0, 0); g.pose().popPose();
                    }
                }

                if (onCd || maxed || conditionNotMet) {
                    float pulse = (float) (Math.sin(Util.getMillis() / 200.0) * 0.5 + 0.5);
                    int pulseAlpha = (int) (255 * (0.6f + 0.4f * pulse) * effectiveAlpha);
                    int pulseColor = onCd ? 0xFF6666 : (maxed ? 0xAAAAAA : 0x4488CC);

                    g.fill(cx, cy, cx + cw, cy + ch, HudAnimUtil.withAlpha(pulseColor, (int)(pulseAlpha * 0.1f)));
                    g.fill(cx - 1, cy - 1, cx + cw + 1, cy, HudAnimUtil.withAlpha(pulseColor, pulseAlpha));
                    g.fill(cx - 1, cy + ch, cx + cw + 1, cy + ch + 1, HudAnimUtil.withAlpha(pulseColor, pulseAlpha));
                    g.fill(cx - 1, cy, cx, cy + ch, HudAnimUtil.withAlpha(pulseColor, pulseAlpha));
                    g.fill(cx + cw, cy, cx + cw + 1, cy + ch, HudAnimUtil.withAlpha(pulseColor, pulseAlpha));

                    g.fill(cx, cy, cx + cw, cy + ch, HudAnimUtil.withAlpha(0x000000, (int)(160 * effectiveAlpha)));
                }

                int textX = cx + 52 + (int)(4 * hEase);

                String statusStr = "";
                int scColor = 0xFF5555;
                if (onCd) {
                    statusStr = cache.getCooldownText(shopId, gi);
                    if (statusStr.isEmpty()) statusStr = "...";
                } else if (maxed) {
                    statusStr = Component.translatable("arc_quest.gui.trade.status.maxed").getString();
                    scColor = 0xAAAAAA;
                } else if (conditionNotMet) {
                    statusStr = Component.translatable("arc_quest.gui.trade.status.locked").getString();
                    scColor = 0x4488CC;
                }

                String nameStr = entry.getDisplayName().getString();
                int maxNameW = cw - 140;
                if (!statusStr.isEmpty()) maxNameW -= (font.width(statusStr) + 6);
                if (font.width(nameStr) > maxNameW) nameStr = font.plainSubstrByWidth(nameStr, maxNameW - 8) + "...";
                int nameDrawW = font.width(nameStr);

                g.drawString(font, nameStr, textX, cy + 10, HudAnimUtil.withAlpha(canBuy ? 0xFFFFFF : 0x999999, (int)(255*effectiveAlpha)), true);

                if (!statusStr.isEmpty()) {
                    g.drawString(font, statusStr, textX + nameDrawW + 6, cy + 10, HudAnimUtil.withAlpha(scColor, (int)(255*effectiveAlpha)), true);
                }

                int currentCostX = textX;
                int costY = cy + 26;

                for (int j = 0; j < entry.getCosts().size(); j++) {
                    ITradeOffer cost = entry.getCosts().get(j);
                    if (j > 0) {
                        g.drawString(font, "+", currentCostX, costY, HudAnimUtil.withAlpha(0x777777, (int)(255*effectiveAlpha)), true);
                        currentCostX += font.width("+") + 2;
                    }

                    ResourceLocation costIconLoc = cost.getIcon();
                    g.pose().pushPose();
                    float iconScale = 0.6f;
                    g.pose().translate(currentCostX, costY - 1, 0);
                    g.pose().scale(iconScale, iconScale, 1f);

                    if (costIconLoc != null) {
                        drawAdaptiveIcon(g, costIconLoc, 0, 0, 16, 16, effectiveAlpha);
                    } else {
                        ItemStack costStack = getIconStackForOffer(cost);
                        if (!costStack.isEmpty()) g.renderItem(costStack, 0, 0);
                    }
                    g.pose().popPose();

                    currentCostX += 12;

                    String costDesc = cost.describe().getString();
                    g.drawString(font, costDesc, currentCostX, costY, HudAnimUtil.withAlpha(canBuy ? 0xDDDDDD : 0x777777, (int)(255*effectiveAlpha)), true);
                    currentCostX += font.width(costDesc) + 4;

                    if (currentCostX > cx + cw - 100) {
                        g.drawString(font, "...", currentCostX, costY, HudAnimUtil.withAlpha(0x777777, (int)(255*effectiveAlpha)), true);
                        break;
                    }
                }

                int btnW = 80, btnH = 24;
                int btnX = cx + cw - btnW - 12, btnY = cy + (ch - btnH) / 2;
                String btnText;
                int btnC;
                if (canBuy) {
                    btnText = Component.translatable("arc_quest.gui.trade.btn.purchase").getString();
                    btnC = (hov && mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH) ? 0xFFFFFF : getThemeColorForEntry(entry);
                } else if (onCd) {
                    btnText = Component.translatable("arc_quest.gui.trade.btn.wait").getString();
                    btnC = 0x888888;
                } else if (conditionNotMet) {
                    btnText = Component.translatable("arc_quest.gui.trade.btn.locked").getString();
                    btnC = 0x4488CC;
                } else {
                    btnText = Component.translatable("arc_quest.gui.trade.btn.empty").getString();
                    btnC = 0x888888;
                }

                g.fill(btnX, btnY, btnX + btnW, btnY + btnH, HudAnimUtil.withAlpha(btnC, (int)(40 * effectiveAlpha)));
                HudAnimUtil.drawFrame(g, btnX, btnY, btnW, btnH, 1, HudAnimUtil.withAlpha(btnC, (int)(200 * effectiveAlpha)));
                g.drawCenteredString(font, btnText, btnX + btnW / 2, btnY + 8, HudAnimUtil.withAlpha(btnC, (int)(255*effectiveAlpha)));

                g.pose().popPose();
            }
        }
        g.disableScissor();

        int maxScroll = Math.max(0, filteredEntries.size() * (CARD_HEIGHT + 8) - rh);
        if (maxScroll > 0) {
            int th = Math.max(16, (int) (((float) rh / (filteredEntries.size() * (CARD_HEIGHT + 8))) * rh));
            int ty = ry + (int) ((scrollOffset / maxScroll) * (rh - th));
            g.fill(rx + rw - 6, ty, rx + rw - 4, ty + th, HudAnimUtil.withAlpha(0xFFFFFF, (int) (180 * effectiveAlpha)));
        }
    }

    @Override
    protected int getHoveredEntryIndex(int mx, int my) {
        int pw = panelW(), ph = panelH(), px = (width - pw) / 2, py = (height - ph) / 2;
        int rx = px + CAT_WIDTH + 16 + (int)((1f - (HudAnimUtil.easeOutCubic(transitionAnim)* HudAnimUtil.easeOutCubic(suspendAlpha))) * 200f);
        int ry = py + 36, rw = pw - CAT_WIDTH - 16, rh = ph - 36;

        if (mx >= rx && mx < rx + rw && my >= ry && my <= ry + rh) {
            int vi = (int) ((my - ry + scrollOffset - 8) / (CARD_HEIGHT + 8));
            if (vi >= 0 && vi < filteredEntries.size() && my >= ry + (int) (vi * (CARD_HEIGHT + 8) - scrollOffset) + 8 && my < ry + (int) (vi * (CARD_HEIGHT + 8) - scrollOffset) + 8 + CARD_HEIGHT) return vi;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0 || shop == null || isClosing || transitionAnim < 0.9f) return super.mouseClicked(mx, my, btn);

        int pw = panelW(), ph = panelH(), px = (width - pw) / 2, py = (height - ph) / 2;
        float slide = (1f - (HudAnimUtil.easeOutCubic(transitionAnim)* HudAnimUtil.easeOutCubic(suspendAlpha))) * 200f;

        if (mx >= px - (int)slide && mx < px - (int)slide + CAT_WIDTH) {
            int cy = py + 46;
            if (my >= cy && my < cy + 28) { selectedCategoryIndex = 0; filterEntries(); playClick(); return true; }
            cy += 36;
            for (int i = 0; i < shop.getCategories().size(); i++) {
                if (my >= cy && my < cy + 28) { selectedCategoryIndex = i + 1; filterEntries(); playClick(); return true; }
                cy += 36;
            }
        }

        int vi = getHoveredEntryIndex((int)mx, (int)my);
        if (vi != -1) {
            int rx = px + CAT_WIDTH + 16 + (int)slide;
            int btnX = rx + (pw - CAT_WIDTH - 16) - 100, btnY = py + 36 + (int) (vi * (CARD_HEIGHT + 8) - scrollOffset) + 8 + (CARD_HEIGHT - 24) / 2;
            if (mx >= btnX && mx < btnX + 80 && my >= btnY && my < btnY + 24) {
                TradeEntry e = filteredEntries.get(vi);
                int gi = ClientTradeCache.INSTANCE.getGlobalIndex(shopId, e.getEntryId());
                lastClickedGi = gi;

                if (ClientTradeCache.INSTANCE.canPurchase(shopId, gi)) {
                    ArcQuestNetwork.sendTradeRequest(C2SRequestTradePacket.purchaseWithScreenType(shopId, e.getEntryId(), C2SRequestTradePacket.ScreenType.FULL));
                    playClick();
                } else {
                    onTradeFail(S2COpenTradePacket.FailReason.GENERIC, "blocked");
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        if (isClosing || dt == 0) return false;
        targetScroll -= d * (CARD_HEIGHT + 8); clampScroll(); return true;
    }

    @Override
    public void refreshData() {
        super.refreshData();

        TradeEntry hoveredEntry = (hoveredTooltipIndex != -1 && hoveredTooltipIndex < filteredEntries.size())
                ? filteredEntries.get(hoveredTooltipIndex) : null;

        filterEntries();

        if (hoveredEntry != null) {
            int newIndex = filteredEntries.indexOf(hoveredEntry);
            if (newIndex != -1 && entryHoverAnims != null && newIndex < entryHoverAnims.length) {
                entryHoverAnims[newIndex] = 1f;
            }
        }
    }
}