package org.arcadia.arc_quest.client.hud.shop;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.network.ClientTradeCache;

import java.util.List;

public class TradeListPanel {
    private final TradeScreen screen;
    private final Font font;
    public static final int CARD_HEIGHT = 48;
    private double scrollOffset = 0, targetScroll = 0;
    private float[] entryHoverAnims;

    public TradeListPanel(TradeScreen screen, Font font) { this.screen = screen; this.font = font; resetAnims(); }
    public void resetAnims() { targetScroll = 0; scrollOffset = 0; entryHoverAnims = new float[screen.getFilteredEntries().size()]; }
    public void setHoverAnim(int index, float val) { if (index >= 0 && index < entryHoverAnims.length) entryHoverAnims[index] = val; }

    public void clampScroll(int listHeight) {
        int maxScroll = Math.max(0, screen.getFilteredEntries().size() * (CARD_HEIGHT + 8) + 4 - listHeight);
        targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));
    }

    public void mouseScrolled(double d, int listHeight) { targetScroll -= d * (CARD_HEIGHT + 8); clampScroll(listHeight); }
    public double getScrollOffset() { return scrollOffset; }

    public void render(GuiGraphics g, int rx, int ry, int rw, int rh, int mx, int my, float dt, float alpha, boolean isClosing, float fastClose) {
        clampScroll(rh);
        scrollOffset += (targetScroll - scrollOffset) * Math.min(1.0, dt * 12.0);
        g.enableScissor(rx, ry, rx + rw, ry + rh);
        float contentScale = isClosing ? HudAnimUtil.easeInCubic(fastClose) : 1.0f;
        ClientTradeCache cache = ClientTradeCache.INSTANCE;
        List<TradeEntry> entries = screen.getFilteredEntries();

        for (int i = 0; i < entries.size(); i++) {
            TradeEntry entry = entries.get(i);
            int gi = cache.getGlobalIndex(screen.getShopId(), entry.getEntryId());
            if (gi == -1) continue;
            int drawY = ry + (int) (i * (CARD_HEIGHT + 8) - scrollOffset) + 8;
            if (drawY + CARD_HEIGHT < ry || drawY > ry + rh) continue;

            boolean hov = !isClosing && dt > 0 && mx >= rx && mx < rx + rw && my >= drawY && my < drawY + CARD_HEIGHT && my >= ry && my <= ry + rh;
            boolean onCd = cache.isEntryCoolingDown(screen.getShopId(), gi, entry);
            boolean maxed = cache.isPurchaseLimitReached(screen.getShopId(), gi, entry);
            boolean locked = !onCd && !maxed && cache.isConditionBlocked(screen.getShopId(), gi, entry);
            boolean canBuy = !onCd && !maxed && !locked;

            entryHoverAnims[i] = HudAnimUtil.step(entryHoverAnims[i], hov && canBuy ? 1f : 0f, 6f, dt);
            float hEase = HudAnimUtil.easeOutCubic(entryHoverAnims[i]);
            int bgA = (int) ((0x22 + 0x33 * hEase) * alpha);
            int bdA = (int) ((0x44 + 0x66 * hEase) * alpha);
            int bRgb = hov && canBuy ? screen.getThemeColorForEntry(entry) : 0xFFFFFF;

            ClientTradeCache.FeedbackSnapshot fbs = cache.feedbackSnapshot(screen.getShopId());
            boolean hasShortfall = fbs != null && entry.getEntryId().equals(fbs.lastFailedEntryId()) && !fbs.shortfallLines().isEmpty();

            if (gi == screen.getLastClickedGi()) {
                if (screen.isFeedbackSuccess() && screen.getFeedbackAnim() > 0) {
                    bdA = Math.min(255, bdA + (int) (180 * screen.getFeedbackAnim() * alpha));
                    bRgb = screen.lerpColor(bRgb, 0x55FF55, screen.getFeedbackAnim());
                } else if (!screen.isFeedbackSuccess() && screen.getFeedbackAnim() > 0) {
                    float intensity = screen.getFeedbackAnim();
                    bdA = Math.min(255, bdA + (int) (180 * intensity * alpha));
                    bRgb = screen.lerpColor(bRgb, 0xFF3333, intensity);
                } else if (hasShortfall) {
                    bdA = Math.min(255, bdA + (int) (40 * alpha));
                    bRgb = screen.lerpColor(bRgb, 0xAA4444, 0.35f);
                }
            }

            int cx = rx + 8, cy = drawY, cw = rw - 16, ch = CARD_HEIGHT;
            g.fill(cx, cy, cx + cw, cy + ch, (bgA << 24) | 0x05050A);
            HudAnimUtil.drawFrame(g, cx, cy, cw, ch, 1, (bdA << 24) | (bRgb & 0xFFFFFF));

            if (contentScale > 0.01f) {
                g.pose().pushPose();
                g.pose().translate(cx + cw / 2f, cy + ch / 2f, 0);
                float aScale = contentScale + hEase * 0.02f;
                g.pose().scale(aScale, aScale, 1f);
                g.pose().translate(-(cx + cw / 2f), -(cy + ch / 2f), 0);

                if (entry.getRewardIcon() != null) screen.drawAdaptiveIcon(g, entry.getRewardIcon(), cx + 7, cy + 16, 16, 16, alpha);
                else {
                    ItemStack is = screen.getIconStackForEntry(entry);
                    if (!is.isEmpty()) {
                        g.pose().pushPose();
                        g.pose().translate(cx + 12, cy + 16, 0);
                        g.pose().scale(1.2f, 1.2f, 1f);
                        g.renderItem(is, 0, 0);
                        g.pose().popPose();
                    }
                }

                if (onCd || maxed || locked) {
                    int pA = (int) (255 * alpha);
                    int pC = onCd ? 0xFF6666 : (maxed ? 0xAAAAAA : 0x4488CC);
                    g.fill(cx, cy, cx + cw, cy + ch, HudAnimUtil.withAlpha(pC, (int) (pA * 0.1f)));
                    g.fill(cx - 1, cy - 1, cx + cw + 1, cy, HudAnimUtil.withAlpha(pC, pA));
                    g.fill(cx - 1, cy + ch, cx + cw + 1, cy + ch + 1, HudAnimUtil.withAlpha(pC, pA));
                    g.fill(cx - 1, cy, cx, cy + ch, HudAnimUtil.withAlpha(pC, pA));
                    g.fill(cx + cw, cy, cx + cw + 1, cy + ch, HudAnimUtil.withAlpha(pC, pA));
                    g.fill(cx, cy, cx + cw, cy + ch, HudAnimUtil.withAlpha(0x000000, (int) (160 * alpha)));
                }

                int textX = cx + 52 + (int) (4 * hEase);
                String statStr = onCd ? cache.getCooldownText(screen.getShopId(), gi)
                        : (maxed ? Component.translatable("arc_quest.gui.trade.status.maxed").getString()
                        : (locked ? Component.translatable("arc_quest.gui.trade.status.locked").getString() : ""));
                int scColor = onCd ? 0xFF5555 : (maxed ? 0xAAAAAA : 0x4488CC);
                String nStr = entry.getDisplayName().getString();
                int mNW = cw - 140 - (statStr.isEmpty() ? 0 : font.width(statStr) + 6);
                if (font.width(nStr) > mNW) nStr = font.plainSubstrByWidth(nStr, mNW - 8) + "...";

                g.drawString(font, nStr, textX, cy + 10, HudAnimUtil.withAlpha(canBuy ? 0xFFFFFF : 0x999999, (int) (255 * alpha)), true);
                if (!statStr.isEmpty()) g.drawString(font, statStr, textX + font.width(nStr) + 6, cy + 10, HudAnimUtil.withAlpha(scColor, (int) (255 * alpha)), true);

                int cX = textX, costY = cy + 26;
                for (int j = 0; j < entry.getCosts().size(); j++) {
                    ITradeOffer cost = entry.getCosts().get(j);
                    if (j > 0) {
                        g.drawString(font, "+", cX, costY, HudAnimUtil.withAlpha(0x777777, (int) (255 * alpha)), true);
                        cX += font.width("+") + 2;
                    }
                    g.pose().pushPose();
                    g.pose().translate(cX, costY - 1, 0);
                    g.pose().scale(0.6f, 0.6f, 1f);
                    if (cost.getIcon() != null) screen.drawAdaptiveIcon(g, cost.getIcon(), 0, 0, 16, 16, alpha);
                    else {
                        ItemStack cS = screen.getIconStackForOffer(cost);
                        if (!cS.isEmpty()) g.renderItem(cS, 0, 0);
                    }
                    g.pose().popPose();
                    cX += 12;

                    String cDesc = cost.describe().getString();
                    g.drawString(font, cDesc, cX, costY, HudAnimUtil.withAlpha(canBuy ? 0xDDDDDD : 0x777777, (int) (255 * alpha)), true);
                    cX += font.width(cDesc) + 4;
                    if (cX > cx + cw - 100) {
                        g.drawString(font, "...", cX, costY, HudAnimUtil.withAlpha(0x777777, (int) (255 * alpha)), true);
                        break;
                    }
                }

                int btnW = 80, btnH = 24, btnX = cx + cw - btnW - 12, btnY = cy + (ch - btnH) / 2;
                String btnText = canBuy
                        ? Component.translatable("arc_quest.gui.trade.btn.purchase").getString()
                        : (onCd ? Component.translatable("arc_quest.gui.trade.btn.wait").getString()
                        : (locked ? Component.translatable("arc_quest.gui.trade.btn.locked").getString()
                        : Component.translatable("arc_quest.gui.trade.btn.empty").getString()));
                int btnC = canBuy
                        ? ((hov && mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH) ? 0xFFFFFF : screen.getThemeColorForEntry(entry))
                        : (locked ? 0x4488CC : 0x888888);

                g.fill(btnX, btnY, btnX + btnW, btnY + btnH, HudAnimUtil.withAlpha(btnC, (int) (40 * alpha)));
                HudAnimUtil.drawFrame(g, btnX, btnY, btnW, btnH, 1, HudAnimUtil.withAlpha(btnC, (int) (200 * alpha)));
                g.drawCenteredString(font, btnText, btnX + btnW / 2, btnY + 8, HudAnimUtil.withAlpha(btnC, (int) (255 * alpha)));
                g.pose().popPose();
            }
        }
        g.disableScissor();

        int maxScroll = Math.max(0, entries.size() * (CARD_HEIGHT + 8) + 4 - rh);
        if (maxScroll > 0) {
            int th = Math.max(16, (int) (((float) rh / (entries.size() * (CARD_HEIGHT + 8) + 4)) * rh));
            int ty = ry + (int) ((scrollOffset / maxScroll) * (rh - th));
            g.fill(rx + rw - 6, ty, rx + rw - 4, ty + th, HudAnimUtil.withAlpha(0xFFFFFF, (int) (180 * alpha)));
        }
    }

    public TradeEntry getHoveredEntry(int mx, int my, int rx, int ry, int rw, int rh) {
        if (mx >= rx && mx < rx + rw && my >= ry && my <= ry + rh) {
            int vi = (int) ((my - ry + scrollOffset - 8) / (CARD_HEIGHT + 8));
            if (vi >= 0 && vi < screen.getFilteredEntries().size() && my >= ry + (int) (vi * (CARD_HEIGHT + 8) - scrollOffset) + 8 && my < ry + (int) (vi * (CARD_HEIGHT + 8) - scrollOffset) + 8 + CARD_HEIGHT) {
                return screen.getFilteredEntries().get(vi);
            }
        }
        return null;
    }
}