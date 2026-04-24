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
import org.com.arc_quest.trade.api.TradeEntry;
import org.com.arc_quest.trade.network.C2SRequestTradePacket;
import org.com.arc_quest.trade.network.ClientTradeCache;
import org.com.arc_quest.trade.network.S2COpenTradePacket;

import java.util.ArrayList;
import java.util.List;

public class SimpleTradePanel extends AbstractTradeScreen {

    private static Screen parentScreen;

    private final List<TradeEntry> entries;
    private float openAnimTime = 0f;
    private float[] hoverAnims;

    // 【极简构造器】仅需传入 ShopId
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
        this.hoverAnims = new float[this.entries.size()];
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
        if (this.lastRenderTime == 0) this.openAnimTime = 0f;
    }

    @Override protected float getOpenAnimSpeed() { return 0.18f; }
    @Override protected TradeEntry getVisibleEntry(int index) { return (index >= 0 && index < entries.size()) ? entries.get(index) : null; }

    private record Layout(int cols, int rows, int cardW, int cardH, int gap, int startX, int startY, int totalW, int totalH) {}

    private Layout computeLayout() {
        int gap = Math.max(4, width / 80);
        int availW = (int) (width * 0.85f);
        int minCardW = 105;
        int maxPossibleCols = Math.max(1, availW / (minCardW + gap));
        int cols = Math.min(maxPossibleCols, entries.isEmpty() ? 1 : entries.size());
        int flexW = (availW - (cols - 1) * gap) / cols;
        int cardW = Math.max(minCardW, Math.min(160, flexW));
        int cardH = Math.max(38, (int) (cardW * 0.32f));
        int rows = (entries.size() + cols - 1) / cols;
        int totalW = cols * cardW + (cols - 1) * gap;
        int totalH = rows * cardH + (rows - 1) * gap;
        return new Layout(cols, rows, cardW, cardH, gap, (width - totalW) / 2, (height - totalH) / 2, totalW, totalH);
    }

    private float easeOutBack(float t) {
        float c1 = 1.70158f;
        float f = t - 1f;
        return 1f + (c1 + 1f) * (f * f * f) + c1 * (f * f);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float pt) {
        if (!isClosing && dt > 0) openAnimTime += dt;
        float easeProgress = (isClosing ? HudAnimUtil.easeInCubic(transitionAnim) : HudAnimUtil.easeOutCubic(transitionAnim)) * HudAnimUtil.easeOutCubic(suspendAlpha);

        Layout l = computeLayout();
        g.pose().pushPose();

        if (shop == null) {
            g.drawCenteredString(font, Component.translatable("arc_quest.gui.trade.error.unknown_shop").getString(), width / 2, height / 2, HudAnimUtil.withAlpha(0xFF5555, (int)(255*effectiveAlpha)));
            g.pose().popPose(); return;
        }

        float titleYSlide = (1f - easeProgress) * -30f;
        g.pose().pushPose();
        g.pose().translate(0, titleYSlide, 0);
        g.drawCenteredString(font, shop.getDisplayName(), width / 2, l.startY() - 24, HudAnimUtil.withAlpha(shop.getThemeColor(), (int)(255*effectiveAlpha)));
        g.pose().popPose();

        List<TradeEntry> allE = new ArrayList<>(shop.getAllEntries());

        for (int i = 0; i < entries.size(); i++) {
            TradeEntry entry = entries.get(i);
            int gi = ClientTradeCache.INSTANCE.getGlobalIndex(shopId, entry.getEntryId());
            if (gi == -1) continue;
            int targetX = l.startX() + (i % l.cols()) * (l.cardW() + l.gap());
            int targetY = l.startY() + (i / l.cols()) * (l.cardH() + l.gap());

            float flyEase;
            float drawX, drawY;

            if (isClosing) {
                flyEase = easeProgress;
                float cardCenterX = targetX + l.cardW() / 2f;
                float slideDir = cardCenterX < width / 2f ? -1f : (cardCenterX > width / 2f ? 1f : (i % 2 == 0 ? -1f : 1f));
                float slideDist = (1f - easeProgress) * (width / 2f + 100f);
                drawX = targetX + slideDir * slideDist;
                drawY = targetY + (1f - easeProgress) * 30f;
            } else {
                flyEase = easeOutBack(Math.max(0, Math.min(1, (openAnimTime - i * 0.025f) / 0.45f)));
                float vectorX = (targetX + l.cardW() / 2f) - width / 2f;
                float vectorY = (targetY + l.cardH() / 2f) - height / 2f;
                drawX = targetX - vectorX * (1f - flyEase);
                drawY = targetY - vectorY * (1f - flyEase);
            }

            if (flyEase < 0.01f && !isClosing) continue;

            boolean hov = (!isClosing && transitionAnim >= 0.9f) && mx >= targetX && mx < targetX + l.cardW() && my >= targetY && my < targetY + l.cardH();

            boolean onCd = ClientTradeCache.INSTANCE.isEntryCoolingDown(shopId, gi, entry);
            boolean maxed = ClientTradeCache.INSTANCE.isPurchaseLimitReached(shopId, gi, entry);
            boolean conditionNotMet = !onCd && !maxed && ClientTradeCache.INSTANCE.isConditionBlocked(shopId, gi, entry);
            boolean canBuy = !onCd && !maxed && !conditionNotMet;

            hoverAnims[i] = HudAnimUtil.step(hoverAnims[i], hov && canBuy ? 1f : 0f, 10f, dt);
            float hEase = HudAnimUtil.easeOutCubic(hoverAnims[i]);
            float clampedEase = Math.max(0f, Math.min(1f, flyEase));

            float bgScale = (isClosing ? 1.0f : flyEase) + hEase * 0.06f;
            float contentScale = isClosing ? HudAnimUtil.easeInCubic(Math.max(0f, (transitionAnim - 0.4f) / 0.6f)) : bgScale;
            int bRgb = (gi == lastClickedGi && feedbackAnim > 0) ? (feedbackSuccess ? 0x55FF55 : 0xFF5555) : getThemeColorForEntry(entry);

            g.pose().pushPose();
            g.pose().translate(drawX + l.cardW()/2f, drawY + l.cardH()/2f, 0);
            g.pose().scale(bgScale, bgScale, 1f);
            g.pose().translate(-(drawX + l.cardW()/2f), -(drawY + l.cardH()/2f), 0);

            int bgA = (int) ((hov && canBuy ? 0x77 : 0x44) * clampedEase * effectiveAlpha);
            int bdA = (int) ((hov && canBuy ? 0xCC : 0x66) * clampedEase * effectiveAlpha);
            
            ClientTradeCache.FeedbackSnapshot feedback = ClientTradeCache.INSTANCE.feedbackSnapshot(shopId);
            boolean hasShortfall = feedback != null
                    && entry.getEntryId().equals(feedback.lastFailedEntryId())
                    && !feedback.shortfallLines().isEmpty();

            if (gi == lastClickedGi) {
                if (feedbackSuccess && feedbackAnim > 0) {
                    bdA = Math.min(255, bdA + (int)(170 * feedbackAnim * effectiveAlpha));
                    bRgb = lerpColor(bRgb, 0x55FF55, feedbackAnim);
                } else if (!feedbackSuccess && (feedbackAnim > 0 || hasShortfall)) {
                    float syncBreath = (float) (Math.sin(Util.getMillis() / 150.0) * 0.5 + 0.5);
                    float intensity = Math.max(feedbackAnim, hasShortfall ? (syncBreath * 0.6f + 0.4f) : 0f);
                    
                    bdA = Math.min(255, bdA + (int)(170 * intensity * effectiveAlpha));
                    bRgb = lerpColor(bRgb, 0xFF3333, intensity);
                }
            }

            g.fill((int)drawX, (int)drawY, (int)(drawX + l.cardW()), (int)(drawY + l.cardH()), (bgA << 24) | 0x05050A);
            HudAnimUtil.drawFrame(g, (int)drawX, (int)drawY, l.cardW(), l.cardH(), 1, (bdA << 24) | (bRgb & 0xFFFFFF));
            g.pose().popPose();

            if (contentScale > 0.01f) {
                g.pose().pushPose();
                g.pose().translate(drawX + l.cardW()/2f, drawY + l.cardH()/2f, 0);
                g.pose().scale(contentScale, contentScale, 1f);
                g.pose().translate(-(drawX + l.cardW()/2f), -(drawY + l.cardH()/2f), 0);

                int itemDrawY = (int)drawY + (l.cardH() - 16) / 2;
                if (entry.getRewardIcon() != null) {
                    drawAdaptiveIcon(g, entry.getRewardIcon(), (int)drawX + 6, itemDrawY, 16, 16, clampedEase * effectiveAlpha);
                } else {
                    ItemStack icon = getIconStackForEntry(entry);
                    if (!icon.isEmpty()) g.renderItem(icon, (int)drawX + 6, itemDrawY);
                }

                if (onCd || maxed || conditionNotMet) {
                    int cardAlpha = (int) (255 * clampedEase * effectiveAlpha);
                    float pulse = (float) (Math.sin(Util.getMillis() / 200.0) * 0.5 + 0.5);
                    int pulseAlpha = (int) (cardAlpha * (0.6f + 0.4f * pulse));
                    int pulseColor = onCd ? 0xFF6666 : (maxed ? 0xAAAAAA : 0x4488CC);

                    g.fill((int)drawX, (int)drawY, (int)(drawX + l.cardW()), (int)(drawY + l.cardH()), HudAnimUtil.withAlpha(pulseColor, (int)(pulseAlpha * 0.12f)));
                    g.fill((int)drawX - 1, (int)drawY - 1, (int)(drawX + l.cardW()) + 1, (int)drawY, HudAnimUtil.withAlpha(pulseColor, pulseAlpha));
                    g.fill((int)drawX - 1, (int)(drawY + l.cardH()), (int)(drawX + l.cardW()) + 1, (int)(drawY + l.cardH()) + 1, HudAnimUtil.withAlpha(pulseColor, pulseAlpha));
                    g.fill((int)drawX - 1, (int)drawY, (int)drawX, (int)(drawY + l.cardH()), HudAnimUtil.withAlpha(pulseColor, pulseAlpha));
                    g.fill((int)(drawX + l.cardW()), (int)drawY, (int)(drawX + l.cardW()) + 1, (int)(drawY + l.cardH()), HudAnimUtil.withAlpha(pulseColor, pulseAlpha));
                    g.fill((int)drawX, (int)drawY, (int)(drawX + l.cardW()), (int)(drawY + l.cardH()), HudAnimUtil.withAlpha(0x000000, (int)(160 * clampedEase * effectiveAlpha)));
                }

                int textY = (int)drawY + (l.cardH() - font.lineHeight * 2 - 4) / 2;

                String statusStr = "";
                int scColor = 0xFF5555;
                if (onCd) {
                    statusStr = ClientTradeCache.INSTANCE.getCooldownText(shopId, gi);
                    if (statusStr.isEmpty()) statusStr = "...";
                } else if (maxed) {
                    statusStr = Component.translatable("arc_quest.gui.trade.status.maxed").getString();
                    scColor = 0xAAAAAA;
                } else if (conditionNotMet) {
                    statusStr = Component.translatable("arc_quest.gui.trade.status.locked").getString();
                    scColor = 0x4488CC;
                }

                int statusW = statusStr.isEmpty() ? 0 : font.width(statusStr);
                String nameStr = entry.getDisplayName().getString();
                int maxNameW = l.cardW() - 34;
                if (!statusStr.isEmpty()) maxNameW -= (statusW + 4);
                if (font.width(nameStr) > maxNameW) nameStr = font.plainSubstrByWidth(nameStr, maxNameW - 8) + "...";
                int nameDrawW = font.width(nameStr);

                g.drawString(font, nameStr, (int)drawX + 26, textY, HudAnimUtil.withAlpha(canBuy ? 0xFFFFFF : 0x999999, (int)(255 * clampedEase * effectiveAlpha)), true);

                if (!statusStr.isEmpty()) {
                    g.drawString(font, statusStr, (int)drawX + 26 + nameDrawW + 4, textY, HudAnimUtil.withAlpha(scColor, (int)(255 * clampedEase * effectiveAlpha)), true);
                }

                int costColor = canBuy ? HudAnimUtil.withAlpha(getThemeColorForEntry(entry), (int)(255 * clampedEase * effectiveAlpha)) : HudAnimUtil.withAlpha(0x777777, (int)(255 * clampedEase * effectiveAlpha));
                int currentCostX = (int)drawX + 26;
                int costY = textY + font.lineHeight + 4;
                int startCostX = currentCostX;
                int maxCostWConstraint = l.cardW() - 34;

                for (int j = 0; j < entry.getCosts().size(); j++) {
                    ITradeOffer cost = entry.getCosts().get(j);

                    if (currentCostX - startCostX > maxCostWConstraint - 15) {
                        g.drawString(font, "...", currentCostX, costY, costColor, true);
                        break;
                    }

                    if (j > 0) {
                        g.drawString(font, "+", currentCostX, costY, HudAnimUtil.withAlpha(0x777777, (int)(255 * clampedEase * effectiveAlpha)), true);
                        currentCostX += font.width("+") + 2;
                    }

                    ResourceLocation costIconLoc = cost.getIcon();
                    g.pose().pushPose();
                    float iconScale = 0.6f;
                    g.pose().translate(currentCostX, costY - 1, 0);
                    g.pose().scale(iconScale, iconScale, 1f);

                    if (costIconLoc != null) {
                        drawAdaptiveIcon(g, costIconLoc, 0, 0, 16, 16, clampedEase * effectiveAlpha);
                    } else {
                        ItemStack costStack = getIconStackForOffer(cost);
                        if (!costStack.isEmpty()) g.renderItem(costStack, 0, 0);
                    }
                    g.pose().popPose();

                    currentCostX += 12;

                    String costDesc = cost.describe().getString();
                    int remainW = maxCostWConstraint - (currentCostX - startCostX);
                    if (font.width(costDesc) > remainW) {
                        costDesc = font.plainSubstrByWidth(costDesc, Math.max(1, remainW - 6)) + "..";
                    }

                    g.drawString(font, costDesc, currentCostX, costY, costColor, true);
                    currentCostX += font.width(costDesc) + 4;
                }
                g.pose().popPose();
            }
        }
        g.pose().popPose();
    }

    @Override
    protected int getHoveredEntryIndex(int mx, int my) {
        Layout l = computeLayout();
        for (int i = 0; i < entries.size(); i++) {
            int tx = l.startX() + (i % l.cols()) * (l.cardW() + l.gap());
            int ty = l.startY() + (i / l.cols()) * (l.cardH() + l.gap());
            if (mx >= tx && mx < tx + l.cardW() && my >= ty && my < ty + l.cardH()) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0 || shop == null || isClosing || transitionAnim < 0.9f) return super.mouseClicked(mx, my, btn);
        int idx = getHoveredEntryIndex((int)mx, (int)my);
        if (idx != -1) {
            TradeEntry entry = entries.get(idx);
            int gi = ClientTradeCache.INSTANCE.getGlobalIndex(shopId, entry.getEntryId());
            lastClickedGi = gi;

            if (ClientTradeCache.INSTANCE.canPurchase(shopId, gi)) {
                ArcQuestNetwork.sendTradeRequest(C2SRequestTradePacket.purchaseWithScreenType(shopId, entry.getEntryId(), C2SRequestTradePacket.ScreenType.SIMPLE));
                playClick();
            } else {
                onTradeFail(S2COpenTradePacket.FailReason.GENERIC, "blocked");
            }
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public void refreshData() {
        super.refreshData();
        TradeEntry hoveredEntry = (hoveredTooltipIndex != -1 && hoveredTooltipIndex < entries.size())
                ? entries.get(hoveredTooltipIndex) : null;

        List<TradeEntry> oldEntries = new ArrayList<>(this.entries);
        this.entries.clear();
        if (shop != null) {
            List<TradeEntry> allEntries = new ArrayList<>(shop.getAllEntries());
            for (int i = 0; i < allEntries.size(); i++) {
                if (ClientTradeCache.INSTANCE.isVisible(shopId, i)) entries.add(allEntries.get(i));
            }
        }

        float[] newHoverAnims = new float[this.entries.size()];
        if (hoveredEntry != null) {
            int newIndex = entries.indexOf(hoveredEntry);
            if (newIndex != -1) newHoverAnims[newIndex] = 1f;
        }
        this.hoverAnims = newHoverAnims;
    }
}