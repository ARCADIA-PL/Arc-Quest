package org.com.arc_quest.client.hud.shop;

import net.minecraft.client.gui.GuiGraphics;
import org.com.arc_quest.client.hud.HudAnimUtil;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.trade.api.TradeEntry;
import org.com.arc_quest.trade.network.C2SRequestTradePacket;
import org.com.arc_quest.trade.network.ClientTradeCache;
import org.com.arc_quest.trade.network.S2COpenTradePacket;

import java.util.ArrayList;
import java.util.List;

public class SimpleTradePanel extends AbstractTradeScreen {

    private final List<TradeEntry> entries;
    private TradeGridPanel gridPanel;

    public SimpleTradePanel(String shopId) {
        super("arc_quest.gui.trade.quick_title", shopId);
        this.entries = new ArrayList<>();
        if (shop != null) {
            int i = 0;
            for (TradeEntry e : shop.getAllEntries()) {
                if (ClientTradeCache.INSTANCE.isVisible(shopId, i)) entries.add(e);
                i++;
            }
        }
    }

    public List<TradeEntry> getEntries() { return entries; }

    @Override
    protected void init() {
        super.init();
        if (gridPanel == null) gridPanel = new TradeGridPanel(this, font);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float pt) {
        if (shop == null) return;
        float easeProgress = (isClosing ? HudAnimUtil.easeInCubic(transitionAnim) : HudAnimUtil.easeOutCubic(transitionAnim)) * HudAnimUtil.easeOutCubic(suspendAlpha);
        gridPanel.render(g, mx, my, pt, dt, easeProgress, isClosing);
    }

    @Override
    protected TradeEntry getHoveredEntry(int mx, int my) { return gridPanel.getHoveredEntry(mx, my); }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0 || shop == null || isClosing || transitionAnim < 0.9f) return super.mouseClicked(mx, my, btn);
        TradeEntry entry = getHoveredEntry((int)mx, (int)my);
        if (entry != null) {
            int gi = ClientTradeCache.INSTANCE.getGlobalIndex(shopId, entry.getEntryId());
            lastClickedGi = gi;
            if (ClientTradeCache.INSTANCE.canPurchase(shopId, gi)) {
                ArcQuestNetwork.sendTradeRequest(C2SRequestTradePacket.purchaseWithScreenType(shopId, entry.getEntryId(), C2SRequestTradePacket.ScreenType.SIMPLE));
                playClick();
            } else onTradeFail(S2COpenTradePacket.FailReason.GENERIC, "blocked");
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public void refreshData() {
        super.refreshData();
        TradeEntry hov = getHoveredEntry(-999, -999);
        this.entries.clear();
        if (shop != null) {
            List<TradeEntry> all = new ArrayList<>(shop.getAllEntries());
            for (int i = 0; i < all.size(); i++) if (ClientTradeCache.INSTANCE.isVisible(shopId, i)) entries.add(all.get(i));
        }
        gridPanel.updateHoverAnimsSize(this.entries.size());
        if (hov != null) {
            int idx = entries.indexOf(hov);
            if (idx != -1) gridPanel.setHoverAnim(idx, 1f);
        }
    }
}