package org.com.arc_quest.client.gui.shop;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.trade.api.TradeCategory;
import org.com.arc_quest.trade.api.TradeEntry;
import org.com.arc_quest.trade.network.C2SRequestTradePacket;
import org.com.arc_quest.trade.network.ClientTradeCache;
import org.com.arc_quest.trade.network.S2COpenTradePacket;

import java.util.ArrayList;
import java.util.List;

public class TradeScreen extends AbstractTradeScreen {

    private static final int CAT_WIDTH = 130;

    private final List<TradeEntry> allEntries;
    private List<TradeEntry> filteredEntries;

    private TradeCategoryPanel categoryPanel;
    private TradeListPanel listPanel;

    public TradeScreen(String shopId) {
        super("arc_quest.gui.trade.full_title", shopId);
        this.allEntries = shop != null ? new ArrayList<>(shop.getAllEntries()) : List.of();
        this.filteredEntries = new ArrayList<>(allEntries);
    }

    public List<TradeEntry> getFilteredEntries() { return filteredEntries; }

    @Override
    protected void init() {
        super.init();
        if (categoryPanel == null) categoryPanel = new TradeCategoryPanel(this, font);
        if (listPanel == null) listPanel = new TradeListPanel(this, font);
        filterEntries();
    }

    public void filterEntries() {
        filteredEntries = new ArrayList<>();
        List<TradeCategory> cats = shop != null ? shop.getCategories() : List.of();
        TradeCategory selected = (categoryPanel.getSelectedIndex() == 0 || cats.isEmpty()) ? null : cats.get(categoryPanel.getSelectedIndex() - 1);
        for (int i = 0; i < allEntries.size(); i++) {
            if (!ClientTradeCache.INSTANCE.isVisible(shopId, i)) continue;
            TradeEntry entry = allEntries.get(i);
            if (selected == null || selected.equals(entry.getCategory())) filteredEntries.add(entry);
        }
        categoryPanel.resetAnims();
        listPanel.resetAnims();
    }

    private int panelW() { return Math.max(380, Math.min(800, (int)(width * 0.85f))); }
    private int panelH() { return Math.max(220, Math.min(600, (int)(height * 0.85f))); }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float pt) {
        float easeProgress = (isClosing ? HudAnimUtil.easeInCubic(transitionAnim) : HudAnimUtil.easeOutCubic(transitionAnim)) * HudAnimUtil.easeOutCubic(suspendAlpha);
        int pw = panelW(), ph = panelH(), px = (width - pw) / 2, py = (height - ph) / 2;
        float slideOffset = (1f - easeProgress) * 200f;

        g.pose().pushPose();
        if (shop == null) { g.drawCenteredString(font, Component.translatable("arc_quest.gui.trade.error.shop_closed").getString(), width/2, height/2, HudAnimUtil.withAlpha(0xFF5555, (int)(255*effectiveAlpha))); g.pose().popPose(); return; }

        float titleScale = isClosing ? HudAnimUtil.easeInCubic(transitionAnim) : (0.95f + 0.05f * easeProgress);
        if (titleScale > 0.01f) {
            g.pose().pushPose(); g.pose().translate(width / 2f, py + 16, 0); g.pose().scale(titleScale, titleScale, 1f); g.pose().translate(-width / 2f, -(py + 16), 0);
            g.drawCenteredString(font, shop.getDisplayName(), width / 2, py, HudAnimUtil.withAlpha(shop.getThemeColor(), (int)(255*effectiveAlpha))); g.pose().popPose();
        }

        int lx = px - (int) slideOffset, ly = py + 36, lw = CAT_WIDTH, lh = ph - 36;
        int rx = px + CAT_WIDTH + 16 + (int) slideOffset, ry = py + 36, rw = pw - CAT_WIDTH - 16, rh = ph - 36;

        g.fill(lx, ly, lx + lw, ly + lh, HudAnimUtil.withAlpha(0x05050A, (int)(120 * effectiveAlpha)));
        HudAnimUtil.drawFrame(g, lx, ly, lw, lh, 1, HudAnimUtil.withAlpha(0xFFFFFF, (int)(100 * effectiveAlpha)));
        g.fill(rx, ry, rx + rw, ry + rh, HudAnimUtil.withAlpha(0x05050A, (int)(120 * effectiveAlpha)));
        HudAnimUtil.drawFrame(g, rx, ry, rw, rh, 1, HudAnimUtil.withAlpha(0xFFFFFF, (int)(100 * effectiveAlpha)));

        float fastClose = isClosing ? Math.max(0f, (transitionAnim - 0.4f) / 0.6f) : effectiveAlpha;
        categoryPanel.render(g, lx, ly, lw, lh, mx, my, dt, effectiveAlpha, isClosing, fastClose);
        listPanel.render(g, rx, ry, rw, rh, mx, my, dt, effectiveAlpha, isClosing, fastClose);
        g.pose().popPose();
    }

    @Override
    protected TradeEntry getHoveredEntry(int mx, int my) {
        int pw = panelW(), ph = panelH(), px = (width - pw) / 2, py = (height - ph) / 2;
        int rx = px + CAT_WIDTH + 16 + (int)((1f - (HudAnimUtil.easeOutCubic(transitionAnim)* HudAnimUtil.easeOutCubic(suspendAlpha))) * 200f);
        return listPanel.getHoveredEntry(mx, my, rx, py + 36, pw - CAT_WIDTH - 16, ph - 36);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0 || shop == null || isClosing || transitionAnim < 0.9f) return super.mouseClicked(mx, my, btn);
        int pw = panelW(), ph = panelH(), px = (width - pw) / 2, py = (height - ph) / 2;
        float slide = (1f - (HudAnimUtil.easeOutCubic(transitionAnim)* HudAnimUtil.easeOutCubic(suspendAlpha))) * 200f;

        if (categoryPanel.mouseClicked(mx, my, px - (int)slide, py + 36, CAT_WIDTH, ph - 36)) return true;

        TradeEntry entry = getHoveredEntry((int)mx, (int)my);
        if (entry != null) {
            int rx = px + CAT_WIDTH + 16 + (int)slide, vi = filteredEntries.indexOf(entry);
            int btnX = rx + (pw - CAT_WIDTH - 16) - 100, btnY = py + 36 + (int) (vi * (TradeListPanel.CARD_HEIGHT + 8) - 0) + 8 + (TradeListPanel.CARD_HEIGHT - 24) / 2;
            if (mx >= btnX && mx < btnX + 80 && my >= btnY && my < btnY + 24) {
                int gi = ClientTradeCache.INSTANCE.getGlobalIndex(shopId, entry.getEntryId());
                lastClickedGi = gi;
                if (ClientTradeCache.INSTANCE.canPurchase(shopId, gi)) { ArcQuestNetwork.sendTradeRequest(C2SRequestTradePacket.purchaseWithScreenType(shopId, entry.getEntryId(), C2SRequestTradePacket.ScreenType.FULL)); playClick(); }
                else onTradeFail(S2COpenTradePacket.FailReason.GENERIC, "blocked");
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        if (isClosing || dt == 0) return false;
        listPanel.mouseScrolled(d, panelH() - 36); return true;
    }

    @Override
    public void refreshData() {
        super.refreshData();
        TradeEntry hov = getHoveredEntry(-999, -999);
        filterEntries();
        if (hov != null) {
            int newIdx = filteredEntries.indexOf(hov);
            if (newIdx != -1) listPanel.setHoverAnim(newIdx, 1f);
        }
    }
}