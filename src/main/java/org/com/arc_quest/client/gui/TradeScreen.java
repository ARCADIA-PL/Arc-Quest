package org.com.arc_quest.client.gui;

import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.com.arc_quest.client.util.ClientCooldownHelper;
import org.com.arc_quest.dialogue.network.C2SDialogueChoicePacket;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.trade.api.ITradeOffer;
import org.com.arc_quest.trade.api.TradeCategory;
import org.com.arc_quest.trade.api.TradeEntry;
import org.com.arc_quest.trade.network.C2SRequestTradePacket;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class TradeScreen extends AbstractTradeScreen {

    private static final Logger LOGGER = LogUtils.getLogger();

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
    private boolean[] canBuyConditions;

    public TradeScreen(String shopId, int[] purchaseCounts, int[] maxPurchases,
                       long[] lastPurchaseTimes, long[] purchaseGameTimes, long[] purchaseDayTimes,
                       int[] cooldownTypes, long[] cooldownValues, int[] resetTimeTicks, boolean[] visibility,
                       boolean[] canBuyConditions) {
        super("Trade Matrix", shopId, purchaseCounts, maxPurchases, lastPurchaseTimes, purchaseGameTimes, purchaseDayTimes, cooldownTypes, cooldownValues, resetTimeTicks, visibility);
        this.allEntries = shop != null ? new ArrayList<>(shop.getAllEntries()) : List.of();
        this.filteredEntries = new ArrayList<>(allEntries);
        this.canBuyConditions = canBuyConditions != null ? canBuyConditions : new boolean[0];
    }

    public static void setParentScreen(Screen screen) {
        parentScreen = screen;
    }

    @Override
    public void onClose() {
        if (parentScreen != null) {
            Minecraft mc = Minecraft.getInstance();
            if (parentScreen instanceof DialogueScreen ds) {
                LOGGER.info("[TradeScreen] Sending RESTORE_DIALOGUE request");
                ArcQuestNetwork.sendDialogueChoice(C2SDialogueChoicePacket.restore());
                ds.resetSelectionState();
                ds.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
                LOGGER.info("[TradeScreen] Reset dialogue selection state and re-initialized");
            }
            mc.setScreen(parentScreen);
            parentScreen = null;
            LOGGER.info("[TradeScreen] Restored parent screen");
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
            if (i < visibility.length && !visibility[i]) continue;
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
        float easeProgress = (isClosing ? QuestAnimUtil.easeInCubic(transitionAnim) : QuestAnimUtil.easeOutCubic(transitionAnim)) * QuestAnimUtil.easeOutCubic(suspendAlpha);
        int pw = panelW(), ph = panelH();
        int px = (width - pw) / 2, py = (height - ph) / 2;
        float slideOffset = (1f - easeProgress) * 200f;

        g.pose().pushPose();

        if (shop == null) { g.drawCenteredString(font, "Shop Closed", width/2, height/2, QuestAnimUtil.withAlpha(0xFF5555, (int)(255*effectiveAlpha))); g.pose().popPose(); return; }

        float titleScale = isClosing ? QuestAnimUtil.easeInCubic(transitionAnim) : (0.95f + 0.05f * easeProgress);
        if (titleScale > 0.01f) {
            g.pose().pushPose();
            g.pose().translate(width / 2f, py + 16, 0); g.pose().scale(titleScale, titleScale, 1f); g.pose().translate(-width / 2f, -(py + 16), 0);
            g.drawCenteredString(font, shop.getDisplayName(), width / 2, py, QuestAnimUtil.withAlpha(shop.getThemeColor(), (int)(255*effectiveAlpha)));
            g.pose().popPose();
        }

        clampScroll();
        scrollOffset += (targetScroll - scrollOffset) * Math.min(1.0, dt * 12.0);

        int lx = px - (int) slideOffset, ly = py + 36, lw = CAT_WIDTH, lh = ph - 36;
        int rx = px + CAT_WIDTH + 16 + (int) slideOffset, ry = py + 36, rw = pw - CAT_WIDTH - 16, rh = ph - 36;

        g.fill(lx, ly, lx + lw, ly + lh, QuestAnimUtil.withAlpha(0x05050A, (int)(120 * effectiveAlpha)));
        QuestAnimUtil.drawFrame(g, lx, ly, lw, lh, 1, QuestAnimUtil.withAlpha(0xFFFFFF, (int)(100 * effectiveAlpha)));

        g.fill(rx, ry, rx + rw, ry + rh, QuestAnimUtil.withAlpha(0x05050A, (int)(120 * effectiveAlpha)));
        QuestAnimUtil.drawFrame(g, rx, ry, rw, rh, 1, QuestAnimUtil.withAlpha(0xFFFFFF, (int)(100 * effectiveAlpha)));

        renderCategories(g, lx, ly, lw, lh, mx, my);
        renderEntries(g, rx, ry, rw, rh, mx, my);
        g.pose().popPose();
    }

    private void renderCategories(GuiGraphics g, int lx, int ly, int lw, int lh, int mx, int my) {
        selectedCatSlide = QuestAnimUtil.lerp(selectedCatSlide, selectedCategoryIndex, 0.2f, dt);
        int hlY = ly + 10 + (int)(selectedCatSlide * 36);
        g.fill(lx + 4, hlY, lx + 7, hlY + 28, QuestAnimUtil.withAlpha(selectedCategoryIndex == 0 ? shop.getThemeColor() : (shop.getCategories().isEmpty() ? shop.getThemeColor() : shop.getCategories().get(Math.max(0, selectedCategoryIndex-1)).getThemeColor()), (int)(255 * effectiveAlpha)));

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
        catHoverAnims[idx] = QuestAnimUtil.step(catHoverAnims[idx], hov ? 1f : 0f, 8f, dt);
        float hEase = QuestAnimUtil.easeOutCubic(catHoverAnims[idx]);

        float fastClose = isClosing ? Math.max(0f, (transitionAnim - 0.4f) / 0.6f) : 1.0f;
        float contentScale = isClosing ? QuestAnimUtil.easeInCubic(fastClose) : 1.0f;

        int textX = x + 16 + (sel ? 6 : (int)(4 * hEase));
        int c = sel ? 0xFFFFFF : Math.round(150 + 105 * hEase);

        if (contentScale > 0.01f) {
            g.pose().pushPose();
            g.pose().translate(x + w/2f, y + 14, 0);
            g.pose().scale(contentScale, contentScale, 1f);
            g.pose().translate(-(x + w/2f), -(y + 14), 0);
            g.drawString(font, text, textX, y + 10, QuestAnimUtil.withAlpha((c<<16)|(c<<8)|c, (int)(255*effectiveAlpha)), true);
            g.pose().popPose();
        }
    }

    private void renderEntries(GuiGraphics g, int rx, int ry, int rw, int rh, int mx, int my) {
        g.enableScissor(rx, ry, rx + rw, ry + rh);

        float fastClose = isClosing ? Math.max(0f, (transitionAnim - 0.4f) / 0.6f) : effectiveAlpha;
        float contentScale = isClosing ? QuestAnimUtil.easeInCubic(fastClose) : 1.0f;

        for (int i = 0; i < filteredEntries.size(); i++) {
            TradeEntry entry = filteredEntries.get(i);
            int gi = allEntries.indexOf(entry);
            int drawY = ry + (int) (i * (CARD_HEIGHT + 8) - scrollOffset) + 8;
            if (drawY + CARD_HEIGHT < ry || drawY > ry + rh) continue;

            boolean hov = !isClosing && dt > 0 && mx >= rx && mx < rx + rw && my >= drawY && my < drawY + CARD_HEIGHT && my >= ry && my <= ry + rh;

            boolean onCd = gi >= 0 && gi < lastPurchaseTimes.length && ClientCooldownHelper.isOnCooldown(lastPurchaseTimes[gi], purchaseGameTimes[gi], purchaseDayTimes[gi], cooldownTypes[gi], cooldownValues[gi], resetTimeTicks[gi]);
            boolean maxed = !onCd && entry.hasLimit() && gi >= 0 && gi < purchaseCounts.length && purchaseCounts[gi] >= entry.getMaxPurchases();
            boolean conditionNotMet = !onCd && !maxed && gi >= 0 && gi < canBuyConditions.length && !canBuyConditions[gi];
            boolean canBuy = !onCd && !maxed && !conditionNotMet;

            entryHoverAnims[i] = QuestAnimUtil.step(entryHoverAnims[i], hov && canBuy ? 1f : 0f, 6f, dt);
            float hEase = QuestAnimUtil.easeOutCubic(entryHoverAnims[i]);

            int bgA = (int) ((0x22 + 0x33 * hEase) * effectiveAlpha);
            int bdA = (int) ((0x44 + 0x66 * hEase) * effectiveAlpha);
            int bRgb = hov && canBuy ? getThemeColorForEntry(entry) : 0xFFFFFF;

            if (gi == lastClickedGi && feedbackAnim > 0) {
                bdA = Math.min(255, bdA + (int)(180 * feedbackAnim * effectiveAlpha));
                bRgb = feedbackSuccess ? 0x55FF55 : 0xFF5555;
            }

            int cx = rx + 8, cy = drawY, cw = rw - 16, ch = CARD_HEIGHT;

            
            g.fill(cx, cy, cx + cw, cy + ch, (bgA << 24) | 0x05050A);
            QuestAnimUtil.drawFrame(g, cx, cy, cw, ch, 1, (bdA << 24) | (bRgb & 0xFFFFFF));

            if (contentScale > 0.01f) {
                g.pose().pushPose();
                g.pose().translate(cx + cw/2f, cy + ch/2f, 0);
                float animScale = contentScale + hEase * 0.02f;
                g.pose().scale(animScale, animScale, 1f);
                g.pose().translate(-(cx + cw/2f), -(cy + ch/2f), 0);

                if (entry.getIconOverride() != null) g.blit(entry.getIconOverride(), cx + 7, cy + 16, 0, 0, 16, 16, 16, 16);
                else { ItemStack is = getIconStackForEntry(entry); if (!is.isEmpty()) { g.pose().pushPose(); g.pose().translate(cx + 12, cy + 16, 0); g.pose().scale(1.2f, 1.2f, 1f); g.renderItem(is, 0, 0); g.pose().popPose(); } }


                if (onCd || maxed || conditionNotMet) {
                    float pulse = (float) (Math.sin(Util.getMillis() / 200.0) * 0.5 + 0.5);
                    int pulseAlpha = (int) (255 * (0.6f + 0.4f * pulse) * effectiveAlpha);
                    int pulseColor = onCd ? 0xFF6666 : (maxed ? 0xAAAAAA : 0x4488CC);

                    g.fill(cx, cy, cx + cw, cy + ch, QuestAnimUtil.withAlpha(pulseColor, (int)(pulseAlpha * 0.1f)));
                    
                    g.fill(cx - 1, cy - 1, cx + cw + 1, cy, QuestAnimUtil.withAlpha(pulseColor, pulseAlpha));
                    g.fill(cx - 1, cy + ch, cx + cw + 1, cy + ch + 1, QuestAnimUtil.withAlpha(pulseColor, pulseAlpha));
                    g.fill(cx - 1, cy, cx, cy + ch, QuestAnimUtil.withAlpha(pulseColor, pulseAlpha));
                    g.fill(cx + cw, cy, cx + cw + 1, cy + ch, QuestAnimUtil.withAlpha(pulseColor, pulseAlpha));
                    
                    g.fill(cx, cy, cx + cw, cy + ch, QuestAnimUtil.withAlpha(0x000000, (int)(160 * effectiveAlpha)));
                }

                int textX = cx + 52 + (int)(4 * hEase);

                String statusStr = "";
                int scColor = 0xFF5555;
                if (onCd) {
                    statusStr = ClientCooldownHelper.getCooldownText(lastPurchaseTimes[gi], purchaseGameTimes[gi], purchaseDayTimes[gi], cooldownTypes[gi], cooldownValues[gi], resetTimeTicks[gi]);
                    if (statusStr.isEmpty()) statusStr = "...";
                } else if (maxed) {
                    statusStr = "Maxed";
                    scColor = 0xAAAAAA;
                } else if (conditionNotMet) {
                    statusStr = "Locked";
                    scColor = 0x4488CC;  // 深蓝色
                }

                String nameStr = entry.getDisplayName().getString();
                int maxNameW = cw - 140;
                if (!statusStr.isEmpty()) maxNameW -= (font.width(statusStr) + 6);
                if (font.width(nameStr) > maxNameW) nameStr = font.plainSubstrByWidth(nameStr, maxNameW - 8) + "...";
                int nameDrawW = font.width(nameStr);

                g.drawString(font, nameStr, textX, cy + 10, QuestAnimUtil.withAlpha(canBuy ? 0xFFFFFF : 0x999999, (int)(255*effectiveAlpha)), true);

                if (!statusStr.isEmpty()) {
                    g.drawString(font, statusStr, textX + nameDrawW + 6, cy + 10, QuestAnimUtil.withAlpha(scColor, (int)(255*effectiveAlpha)), true);
                }

                StringBuilder cs = new StringBuilder();
                for (ITradeOffer c : entry.getCosts()) {
                    if (!cs.isEmpty()) cs.append(" + ");
                    cs.append(c.describe().getString());
                }
                String costStr = cs.toString();
                if (font.width(costStr) > cw - 140) costStr = font.plainSubstrByWidth(costStr, cw - 148) + "...";
                g.drawString(font, costStr, textX, cy + 26, QuestAnimUtil.withAlpha(canBuy ? 0xDDDDDD : 0x777777, (int)(255*effectiveAlpha)), true);

                
                int btnW = 80, btnH = 24;
                int btnX = cx + cw - btnW - 12, btnY = cy + (ch - btnH) / 2;
                String btnText;
                int btnC;
                if (canBuy) {
                    btnText = "Purchase";
                    btnC = (hov && mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH) ? 0xFFFFFF : getThemeColorForEntry(entry);
                } else if (onCd) {
                    btnText = "Wait";
                    btnC = 0x888888;
                } else if (conditionNotMet) {
                    btnText = "Locked";
                    btnC = 0x4488CC;
                } else {
                    btnText = "Empty";
                    btnC = 0x888888;
                }

                g.fill(btnX, btnY, btnX + btnW, btnY + btnH, QuestAnimUtil.withAlpha(btnC, (int)(40 * effectiveAlpha)));
                QuestAnimUtil.drawFrame(g, btnX, btnY, btnW, btnH, 1, QuestAnimUtil.withAlpha(btnC, (int)(200 * effectiveAlpha)));
                g.drawCenteredString(font, btnText, btnX + btnW / 2, btnY + 8, QuestAnimUtil.withAlpha(btnC, (int)(255*effectiveAlpha)));

                g.pose().popPose();
            }
        }
        g.disableScissor();

        int maxScroll = Math.max(0, filteredEntries.size() * (CARD_HEIGHT + 8) - rh);
        if (maxScroll > 0) {
            int th = Math.max(16, (int) (((float) rh / (filteredEntries.size() * (CARD_HEIGHT + 8))) * rh));
            int ty = ry + (int) ((scrollOffset / maxScroll) * (rh - th));
            g.fill(rx + rw - 6, ty, rx + rw - 4, ty + th, QuestAnimUtil.withAlpha(0xFFFFFF, (int) (180 * effectiveAlpha)));
        }
    }

    @Override
    protected int getHoveredEntryIndex(int mx, int my) {
        int pw = panelW(), ph = panelH(), px = (width - pw) / 2, py = (height - ph) / 2;
        int rx = px + CAT_WIDTH + 16 + (int)((1f - (QuestAnimUtil.easeOutCubic(transitionAnim)*QuestAnimUtil.easeOutCubic(suspendAlpha))) * 200f);
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
        float slide = (1f - (QuestAnimUtil.easeOutCubic(transitionAnim)*QuestAnimUtil.easeOutCubic(suspendAlpha))) * 200f;

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
                int gi = allEntries.indexOf(e);
                lastClickedGi = gi;
                if (!ClientCooldownHelper.isOnCooldown(lastPurchaseTimes[gi], purchaseGameTimes[gi], purchaseDayTimes[gi], cooldownTypes[gi], cooldownValues[gi], resetTimeTicks[gi]) && !(e.hasLimit() && purchaseCounts[gi] >= e.getMaxPurchases())) {
                    ArcQuestNetwork.sendTradeRequest(C2SRequestTradePacket.purchaseWithScreenType(shopId, e.getEntryId(), C2SRequestTradePacket.ScreenType.FULL));
                    playClick();
                } else onTradeFail("blocked");
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
    public void updateData(int[] pc, int[] mp, long[] lpt, long[] pgt, long[] pdt, int[] ct, long[] cv, int[] rt, boolean[] vis, boolean[] canBuy) {
        super.updateData(pc, mp, lpt, pgt, pdt, ct, cv, rt, vis);
        this.canBuyConditions = canBuy != null ? canBuy : new boolean[0];
        filterEntries();
    }
}