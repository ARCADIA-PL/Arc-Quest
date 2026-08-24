package org.arcadia.arc_quest.client.hud.shop;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.trade.api.TradeCategory;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.network.C2SRequestTradePacket;
import org.arcadia.arc_quest.trade.network.ClientTradeCache;
import org.arcadia.arc_quest.trade.network.S2COpenTradePacket;

import java.util.ArrayList;
import java.util.List;

public class TradeScreen extends AbstractTradeScreen {

    private static final int BOTTOM_PADDING = 8;

    private final List<TradeEntry> allEntries;
    private List<TradeEntry> filteredEntries;

    private TradeCategoryPanel categoryPanel;
    private TradeListPanel listPanel;

    public TradeScreen(String shopId) {
        super("arc_quest.gui.trade.full_title", shopId);
        allEntries = shop != null ? new ArrayList<>(shop.getAllEntries()) : List.of();
        filteredEntries = new ArrayList<>(allEntries);
    }

    public List<TradeEntry> getFilteredEntries() {
        return filteredEntries;
    }

    @Override
    protected void init() {
        super.init();
        if (categoryPanel == null) categoryPanel = new TradeCategoryPanel(this, font);
        if (listPanel == null) listPanel = new TradeListPanel(this, font);
        filterEntries(true);
    }

    public void filterEntries() {
        filterEntries(true);
    }

    public void filterEntries(boolean resetVisuals) {
        filteredEntries = new ArrayList<>();
        List<TradeCategory> cats = shop != null ? shop.getCategories() : List.of();
        TradeCategory selected = (categoryPanel.getSelectedIndex() == 0 || cats.isEmpty()) ? null : cats.get(categoryPanel.getSelectedIndex() - 1);
        for (int i = 0; i < allEntries.size(); i++) {
            if (!ClientTradeCache.INSTANCE.isVisible(shopId, i)) continue;
            TradeEntry entry = allEntries.get(i);
            if (selected == null || selected.equals(entry.getCategory())) filteredEntries.add(entry);
        }

        if (resetVisuals) {
            categoryPanel.resetAnims();
            listPanel.resetAnims();
        }
    }

    private TradeScreenLayout.Metrics layout() {
        return TradeScreenLayout.full(width, height);
    }

    private int panelW() {
        return layout().panelWidth();
    }

    private int panelH() {
        return layout().panelHeight();
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float pt) {
        float easeProgress = (isClosing ? HudAnimUtil.easeInCubic(transitionAnim) : HudAnimUtil.easeOutCubic(transitionAnim)) * HudAnimUtil.easeOutCubic(suspendAlpha);
        TradeScreenLayout.Metrics layout = layout();
        int pw = layout.panelWidth(), ph = layout.panelHeight(), px = (width - pw) / 2, py = (height - ph) / 2;
        float slideOffset = (1f - easeProgress) * 200f;

        g.pose().pushPose();
        if (shop == null) {
            g.drawCenteredString(font, Component.translatable("arc_quest.gui.trade.error.shop_closed").getString(), width / 2, height / 2, HudAnimUtil.withAlpha(0xFF5555, (int) (255 * effectiveAlpha)));
            g.pose().popPose();
            return;
        }

        float titleFitScale = Math.min(1f, Math.max(0.45f,
                (pw - 12f) / Math.max(1, font.width(shop.getDisplayName()))));
        float titleScale = (isClosing ? HudAnimUtil.easeInCubic(transitionAnim)
                : (0.95f + 0.05f * easeProgress)) * titleFitScale;
        if (titleScale > 0.01f) {
            g.pose().pushPose();
            g.pose().translate(width / 2f, py + 16, 0);
            g.pose().scale(titleScale, titleScale, 1f);
            g.pose().translate(-width / 2f, -(py + 16), 0);
            g.drawCenteredString(font, shop.getDisplayName(), width / 2, py, HudAnimUtil.withAlpha(shop.getThemeColor(), (int) (255 * effectiveAlpha)));
            g.pose().popPose();
        }

        int lx = px - (int) slideOffset, ly = py + layout.headerHeight(),
                lw = layout.categoryWidth(), lh = ph - layout.headerHeight();
        int rx = px + layout.categoryWidth() + layout.panelGap() + (int) slideOffset,
                ry = py + layout.headerHeight(), rw = layout.listWidth(), rh = ph - layout.headerHeight();

        g.fill(lx, ly, lx + lw, ly + lh, HudAnimUtil.withAlpha(0x05050A, (int) (120 * effectiveAlpha)));
        HudAnimUtil.drawFrame(g, lx, ly, lw, lh, 1, HudAnimUtil.withAlpha(0xFFFFFF, (int) (100 * effectiveAlpha)));
        g.fill(rx, ry, rx + rw, ry + rh, HudAnimUtil.withAlpha(0x05050A, (int) (120 * effectiveAlpha)));
        HudAnimUtil.drawFrame(g, rx, ry, rw, rh, 1, HudAnimUtil.withAlpha(0xFFFFFF, (int) (100 * effectiveAlpha)));

        float fastClose = isClosing ? Math.max(0f, (transitionAnim - 0.4f) / 0.6f) : effectiveAlpha;

        categoryPanel.render(g, lx, ly, lw, lh - BOTTOM_PADDING, mx, my, dt, effectiveAlpha, isClosing, fastClose);
        listPanel.render(g, rx, ry, rw, rh - BOTTOM_PADDING, mx, my, dt, effectiveAlpha, isClosing, fastClose);
        g.pose().popPose();
    }

    @Override
    protected TradeEntry getHoveredEntry(int mx, int my) {
        TradeScreenLayout.Metrics layout = layout();
        int pw = layout.panelWidth(), ph = layout.panelHeight(), px = (width - pw) / 2, py = (height - ph) / 2;
        int rx = px + layout.categoryWidth() + layout.panelGap()
                + (int) ((1f - (HudAnimUtil.easeOutCubic(transitionAnim) * HudAnimUtil.easeOutCubic(suspendAlpha))) * 200f);
        return listPanel.getHoveredEntry(mx, my, rx, py + layout.headerHeight(),
                layout.listWidth(), ph - layout.headerHeight() - BOTTOM_PADDING);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0 || shop == null || isClosing || transitionAnim < 0.9f) {
            return super.mouseClicked(mx, my, btn);
        }

        TradeScreenLayout.Metrics layout = layout();
        int pw = layout.panelWidth(), ph = layout.panelHeight(), px = (width - pw) / 2, py = (height - ph) / 2;

        if (closeIfClickedOutside(mx, my, btn, px, py, pw, ph)) {
            return true;
        }

        float slide = (1f - (HudAnimUtil.easeOutCubic(transitionAnim) * HudAnimUtil.easeOutCubic(suspendAlpha))) * 200f;

        if (categoryPanel.mouseClicked(mx, my, px - (int) slide, py + layout.headerHeight(),
                layout.categoryWidth(), ph - layout.headerHeight() - BOTTOM_PADDING)) {
            return true;
        }

        TradeEntry entry = getHoveredEntry((int) mx, (int) my);
        if (entry != null) {
            int rx = px + layout.categoryWidth() + layout.panelGap() + (int) slide;
            int rw = layout.listWidth();
            int vi = filteredEntries.indexOf(entry);
            int btnX = rx + rw - 100;
            int btnY = py + layout.headerHeight() + (int) (vi * (TradeListPanel.CARD_HEIGHT + 8) - listPanel.getScrollOffset())
                    + 8 + (TradeListPanel.CARD_HEIGHT - 24) / 2;

            if (mx >= btnX && mx < btnX + 80 && my >= btnY && my < btnY + 24) {
                int gi = ClientTradeCache.INSTANCE.getGlobalIndex(shopId, entry.getEntryId());
                lastClickedGi = gi;
                if (ClientTradeCache.INSTANCE.canPurchase(shopId, gi)) {
                    ArcQuestNetwork.sendTradeRequest(
                            ClientTradeCache.INSTANCE.createPurchasePacket(
                                    shopId,
                                    entry.getEntryId(),
                                    C2SRequestTradePacket.ScreenType.FULL
                            )
                    );
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
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (isClosing || dt == 0) return false;
        TradeScreenLayout.Metrics layout = layout();
        int pw = layout.panelWidth(), ph = layout.panelHeight(), px = (width - pw) / 2, py = (height - ph) / 2;
        float slide = (1f - (HudAnimUtil.easeOutCubic(transitionAnim) * HudAnimUtil.easeOutCubic(suspendAlpha))) * 200f;
        int lx = px - (int) slide;
        int panelY = py + layout.headerHeight();
        int panelHeight = ph - layout.headerHeight() - BOTTOM_PADDING;
        if (categoryPanel.mouseScrolled(mx, my, scrollY, lx, panelY, layout.categoryWidth(), panelHeight)) return true;

        int rx = px + layout.categoryWidth() + layout.panelGap() + (int) slide;
        int rw = layout.listWidth();
        if (mx >= rx && mx < rx + rw && my >= panelY && my < panelY + panelHeight) {
            listPanel.mouseScrolled(scrollY, panelHeight);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public void refreshData() {
        super.refreshData();
        TradeEntry hov = getHoveredEntry(-999, -999);

        filterEntries(false);

        if (hov != null) {
            int newIdx = filteredEntries.indexOf(hov);
            if (newIdx != -1) listPanel.setHoverAnim(newIdx, 1f);
        }
    }
}
