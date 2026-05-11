package org.arcadia.arc_quest.client.hud.shop;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.network.C2SRequestTradePacket;
import org.arcadia.arc_quest.trade.network.ClientTradeCache;
import org.arcadia.arc_quest.trade.network.S2COpenTradePacket;

import java.util.ArrayList;
import java.util.List;

public class SimpleTradePanel extends AbstractTradeScreen {

    private final List<TradeEntry> entries;
    private TradeGridPanel gridPanel;

    public SimpleTradePanel(String shopId) {
        super("arc_quest.gui.trade.quick_title", shopId);
        entries = new ArrayList<>();
        if (shop != null) {
            int i = 0;
            for (TradeEntry e : shop.getAllEntries()) {
                if (ClientTradeCache.INSTANCE.isVisible(shopId, i)) entries.add(e);
                i++;
            }
        }
    }

    public List<TradeEntry> getEntries() {
        return entries;
    }

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
    protected TradeEntry getHoveredEntry(int mx, int my) {
        return gridPanel.getHoveredEntry(mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0 || shop == null || isClosing || transitionAnim < 0.9f) {
            return super.mouseClicked(mx, my, btn);
        }

        int panelW = Math.max(220, (int) (width * 0.85f));
        int panelH = Math.max(140, (int) (height * 0.70f));
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;

        if (closeIfClickedOutside(mx, my, btn, panelX, panelY, panelW, panelH)) {
            return true;
        }

        TradeEntry entry = getHoveredEntry((int) mx, (int) my);
        if (entry != null) {
            int gi = ClientTradeCache.INSTANCE.getGlobalIndex(shopId, entry.getEntryId());
            lastClickedGi = gi;
            if (ClientTradeCache.INSTANCE.canPurchase(shopId, gi)) {
                ArcQuestNetwork.sendTradeRequest(
                        C2SRequestTradePacket.purchaseWithScreenType(
                                shopId,
                                entry.getEntryId(),
                                C2SRequestTradePacket.ScreenType.SIMPLE
                        )
                );
                playClick();
            } else {
                onTradeFail(S2COpenTradePacket.FailReason.GENERIC, "blocked");
            }
            return true;
        }

        onClose();
        return true;
    }

    @Override
    public void refreshData() {
        super.refreshData();
        TradeEntry hov = getHoveredEntry(-999, -999);
        entries.clear();
        if (shop != null) {
            List<TradeEntry> all = new ArrayList<>(shop.getAllEntries());
            for (int i = 0; i < all.size(); i++)
                if (ClientTradeCache.INSTANCE.isVisible(shopId, i)) entries.add(all.get(i));
        }
        gridPanel.updateHoverAnimsSize(entries.size());
        if (hov != null) {
            int idx = entries.indexOf(hov);
            if (idx != -1) gridPanel.setHoverAnim(idx, 1f);
        }
    }
}