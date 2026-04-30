package org.arcadia.arc_quest.client.hud.shop;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.network.ClientTradeCache;

import java.util.List;

public class TradeGridPanel {

    private final SimpleTradePanel screen;
    private final Font font;
    private float openAnimTime = 0f;
    private float[] hoverAnims;

    public TradeGridPanel(SimpleTradePanel screen, Font font) {
        this.screen = screen;
        this.font = font;
        this.hoverAnims = new float[screen.getEntries().size()];
    }

    public void updateHoverAnimsSize(int size) {
        this.hoverAnims = new float[size];
    }

    public void setHoverAnim(int index, float val) {
        if (index >= 0 && index < hoverAnims.length) hoverAnims[index] = val;
    }

    private Layout computeLayout() {
        List<TradeEntry> entries = screen.getEntries();
        int gap = Math.max(4, screen.width / 80), availW = (int) (screen.width * 0.85f), minCardW = 105;
        int maxPossibleCols = Math.max(1, availW / (minCardW + gap));
        int cols = Math.min(maxPossibleCols, entries.isEmpty() ? 1 : entries.size());
        int cardW = Math.max(minCardW, Math.min(160, (availW - (cols - 1) * gap) / cols));
        int cardH = Math.max(38, (int) (cardW * 0.32f));
        int rows = (entries.size() + cols - 1) / cols;
        int totalW = cols * cardW + (cols - 1) * gap, totalH = rows * cardH + (rows - 1) * gap;
        return new Layout(cols, rows, cardW, cardH, gap, (screen.width - totalW) / 2, (screen.height - totalH) / 2, totalW, totalH);
    }

    private float easeOutBack(float t) {
        float f = t - 1f;
        return 1f + 2.70158f * (f * f * f) + 1.70158f * (f * f);
    }

    public void render(GuiGraphics g, int mx, int my, float pt, float dt, float easeProgress, boolean isClosing) {
        if (!isClosing && dt > 0) openAnimTime += dt;
        Layout l = computeLayout();
        List<TradeEntry> entries = screen.getEntries();
        float alpha = screen.getEffectiveAlpha();

        g.pose().pushPose();
        float titleYSlide = (1f - easeProgress) * -30f;
        g.pose().translate(0, titleYSlide, 0);
        g.drawCenteredString(font, screen.getShop().getDisplayName(), screen.width / 2, l.startY() - 24, HudAnimUtil.withAlpha(screen.getShop().getThemeColor(), (int) (255 * alpha)));
        g.pose().popPose();

        for (int i = 0; i < entries.size(); i++) {
            TradeEntry entry = entries.get(i);
            int gi = ClientTradeCache.INSTANCE.getGlobalIndex(screen.getShopId(), entry.getEntryId());
            if (gi == -1) continue;

            int targetX = l.startX() + (i % l.cols()) * (l.cardW() + l.gap());
            int targetY = l.startY() + (i / l.cols()) * (l.cardH() + l.gap());
            float flyEase, drawX, drawY;

            if (isClosing) {
                flyEase = easeProgress;
                float dir = (targetX + l.cardW() / 2f) < screen.width / 2f ? -1f : 1f;
                drawX = targetX + dir * ((1f - easeProgress) * (screen.width / 2f + 100f));
                drawY = targetY + (1f - easeProgress) * 30f;
            } else {
                flyEase = easeOutBack(Math.max(0, Math.min(1, (openAnimTime - i * 0.025f) / 0.45f)));
                drawX = targetX - ((targetX + l.cardW() / 2f) - screen.width / 2f) * (1f - flyEase);
                drawY = targetY - ((targetY + l.cardH() / 2f) - screen.height / 2f) * (1f - flyEase);
            }

            if (flyEase < 0.01f && !isClosing) continue;

            boolean hov = (!isClosing && screen.getTransitionAnim() >= 0.9f) && mx >= targetX && mx < targetX + l.cardW() && my >= targetY && my < targetY + l.cardH();
            boolean onCd = ClientTradeCache.INSTANCE.isEntryCoolingDown(screen.getShopId(), gi, entry);
            boolean maxed = ClientTradeCache.INSTANCE.isPurchaseLimitReached(screen.getShopId(), gi, entry);
            boolean locked = !onCd && !maxed && ClientTradeCache.INSTANCE.isConditionBlocked(screen.getShopId(), gi, entry);
            boolean canBuy = !onCd && !maxed && !locked;

            hoverAnims[i] = HudAnimUtil.step(hoverAnims[i], hov && canBuy ? 1f : 0f, 10f, dt);
            float hEase = HudAnimUtil.easeOutCubic(hoverAnims[i]), clampedEase = Math.max(0f, Math.min(1f, flyEase));
            float bgScale = (isClosing ? 1.0f : flyEase) + hEase * 0.06f;
            float contentScale = isClosing ? HudAnimUtil.easeInCubic(Math.max(0f, (screen.getTransitionAnim() - 0.4f) / 0.6f)) : bgScale;

            int bRgb = (gi == screen.getLastClickedGi() && screen.getFeedbackAnim() > 0) ? (screen.isFeedbackSuccess() ? 0x55FF55 : 0xFF5555) : screen.getThemeColorForEntry(entry);
            int bgA = (int) ((hov && canBuy ? 0x77 : 0x44) * clampedEase * alpha), bdA = (int) ((hov && canBuy ? 0xCC : 0x66) * clampedEase * alpha);

            ClientTradeCache.FeedbackSnapshot fbs = ClientTradeCache.INSTANCE.feedbackSnapshot(screen.getShopId());
            boolean hasShortfall = fbs != null && entry.getEntryId().equals(fbs.lastFailedEntryId()) && !fbs.shortfallLines().isEmpty();

            if (gi == screen.getLastClickedGi()) {
                if (screen.isFeedbackSuccess() && screen.getFeedbackAnim() > 0) {
                    bdA = Math.min(255, bdA + (int) (170 * screen.getFeedbackAnim() * alpha));
                    bRgb = screen.lerpColor(bRgb, 0x55FF55, screen.getFeedbackAnim());
                } else if (!screen.isFeedbackSuccess() && (screen.getFeedbackAnim() > 0 || hasShortfall)) {
                    float syncBreath = (float) (Math.sin(Util.getMillis() / 150.0) * 0.5 + 0.5);
                    float intensity = Math.max(screen.getFeedbackAnim(), hasShortfall ? (syncBreath * 0.6f + 0.4f) : 0f);
                    bdA = Math.min(255, bdA + (int) (170 * intensity * alpha));
                    bRgb = screen.lerpColor(bRgb, 0xFF3333, intensity);
                }
            }

            g.pose().pushPose();
            g.pose().translate(drawX + l.cardW() / 2f, drawY + l.cardH() / 2f, 0);
            g.pose().scale(bgScale, bgScale, 1f);
            g.pose().translate(-(drawX + l.cardW() / 2f), -(drawY + l.cardH() / 2f), 0);
            g.fill((int) drawX, (int) drawY, (int) (drawX + l.cardW()), (int) (drawY + l.cardH()), (bgA << 24) | 0x05050A);
            HudAnimUtil.drawFrame(g, (int) drawX, (int) drawY, l.cardW(), l.cardH(), 1, (bdA << 24) | (bRgb & 0xFFFFFF));
            g.pose().popPose();

            if (contentScale > 0.01f) {
                g.pose().pushPose();
                g.pose().translate(drawX + l.cardW() / 2f, drawY + l.cardH() / 2f, 0);
                g.pose().scale(contentScale, contentScale, 1f);
                g.pose().translate(-(drawX + l.cardW() / 2f), -(drawY + l.cardH() / 2f), 0);

                int itemDrawY = (int) drawY + (l.cardH() - 16) / 2;
                if (entry.getRewardIcon() != null)
                    screen.drawAdaptiveIcon(g, entry.getRewardIcon(), (int) drawX + 6, itemDrawY, 16, 16, clampedEase * alpha);
                else {
                    ItemStack icon = screen.getIconStackForEntry(entry);
                    if (!icon.isEmpty()) g.renderItem(icon, (int) drawX + 6, itemDrawY);
                }

                if (onCd || maxed || locked) {
                    float pulse = (float) (Math.sin(Util.getMillis() / 200.0) * 0.5 + 0.5);
                    int pA = (int) (255 * clampedEase * alpha * (0.6f + 0.4f * pulse)), pC = onCd ? 0xFF6666 : (maxed ? 0xAAAAAA : 0x4488CC);
                    g.fill((int) drawX, (int) drawY, (int) (drawX + l.cardW()), (int) (drawY + l.cardH()), HudAnimUtil.withAlpha(pC, (int) (pA * 0.12f)));
                    g.fill((int) drawX - 1, (int) drawY - 1, (int) (drawX + l.cardW()) + 1, (int) drawY, HudAnimUtil.withAlpha(pC, pA));
                    g.fill((int) drawX - 1, (int) (drawY + l.cardH()), (int) (drawX + l.cardW()) + 1, (int) (drawY + l.cardH()) + 1, HudAnimUtil.withAlpha(pC, pA));
                    g.fill((int) drawX - 1, (int) drawY, (int) drawX, (int) (drawY + l.cardH()), HudAnimUtil.withAlpha(pC, pA));
                    g.fill((int) (drawX + l.cardW()), (int) drawY, (int) (drawX + l.cardW()) + 1, (int) (drawY + l.cardH()), HudAnimUtil.withAlpha(pC, pA));
                    g.fill((int) drawX, (int) drawY, (int) (drawX + l.cardW()), (int) (drawY + l.cardH()), HudAnimUtil.withAlpha(0x000000, (int) (160 * clampedEase * alpha)));
                }

                int textY = (int) drawY + (l.cardH() - font.lineHeight * 2 - 4) / 2;
                String statusStr = onCd ? ClientTradeCache.INSTANCE.getCooldownText(screen.getShopId(), gi) : (maxed ? Component.translatable("arc_quest.gui.trade.status.maxed").getString() : (locked ? Component.translatable("arc_quest.gui.trade.status.locked").getString() : ""));
                int scColor = onCd ? 0xFF5555 : (maxed ? 0xAAAAAA : 0x4488CC);

                String nameStr = entry.getDisplayName().getString();
                int maxNameW = l.cardW() - 34 - (statusStr.isEmpty() ? 0 : font.width(statusStr) + 4);
                if (font.width(nameStr) > maxNameW) nameStr = font.plainSubstrByWidth(nameStr, maxNameW - 8) + "...";

                g.drawString(font, nameStr, (int) drawX + 26, textY, HudAnimUtil.withAlpha(canBuy ? 0xFFFFFF : 0x999999, (int) (255 * clampedEase * alpha)), true);
                if (!statusStr.isEmpty())
                    g.drawString(font, statusStr, (int) drawX + 26 + font.width(nameStr) + 4, textY, HudAnimUtil.withAlpha(scColor, (int) (255 * clampedEase * alpha)), true);

                int cX = (int) drawX + 26, costY = textY + font.lineHeight + 4;
                for (int j = 0; j < entry.getCosts().size(); j++) {
                    if (cX - ((int) drawX + 26) > l.cardW() - 49) {
                        g.drawString(font, "...", cX, costY, HudAnimUtil.withAlpha(0xFFFFFF, (int) (255 * clampedEase * alpha)), true);
                        break;
                    }
                    ITradeOffer cost = entry.getCosts().get(j);
                    if (j > 0) {
                        g.drawString(font, "+", cX, costY, HudAnimUtil.withAlpha(0x777777, (int) (255 * clampedEase * alpha)), true);
                        cX += font.width("+") + 2;
                    }

                    g.pose().pushPose();
                    g.pose().translate(cX, costY - 1, 0);
                    g.pose().scale(0.6f, 0.6f, 1f);
                    if (cost.getIcon() != null)
                        screen.drawAdaptiveIcon(g, cost.getIcon(), 0, 0, 16, 16, clampedEase * alpha);
                    else {
                        ItemStack cS = screen.getIconStackForOffer(cost);
                        if (!cS.isEmpty()) g.renderItem(cS, 0, 0);
                    }
                    g.pose().popPose();
                    cX += 12;

                    String cDesc = cost.describe().getString();
                    if (font.width(cDesc) > (l.cardW() - 34) - (cX - ((int) drawX + 26)))
                        cDesc = font.plainSubstrByWidth(cDesc, Math.max(1, (l.cardW() - 34) - (cX - ((int) drawX + 26)) - 6)) + "..";
                    g.drawString(font, cDesc, cX, costY, HudAnimUtil.withAlpha(canBuy ? screen.getThemeColorForEntry(entry) : 0x777777, (int) (255 * clampedEase * alpha)), true);
                    cX += font.width(cDesc) + 4;
                }
                g.pose().popPose();
            }
        }
    }

    public TradeEntry getHoveredEntry(int mx, int my) {
        Layout l = computeLayout();
        for (int i = 0; i < screen.getEntries().size(); i++) {
            int tx = l.startX() + (i % l.cols()) * (l.cardW() + l.gap()), ty = l.startY() + (i / l.cols()) * (l.cardH() + l.gap());
            if (mx >= tx && mx < tx + l.cardW() && my >= ty && my < ty + l.cardH()) return screen.getEntries().get(i);
        }
        return null;
    }

    private record Layout(int cols, int rows, int cardW, int cardH, int gap, int startX, int startY, int totalW,
                          int totalH) {
    }
}