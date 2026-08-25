package org.arcadia.arc_quest.client.hud.shop;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.network.ClientTradeCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TradeListPanel {
    public static final int CARD_HEIGHT = 48;
    private static final long CACHE_VALID_MS = 100;
    private static final int STRIDE = CARD_HEIGHT + 8;
    private final TradeScreen screen;
    private final Font font;
    private final Map<String, EntryRenderState> stateCache = new HashMap<>();
    private final Map<String, EntryVisualCache> visualCache = new HashMap<>();
    private final String plusText = "+";
    private final int plusWidth;
    private final String statusMaxedText;
    private final String statusLockedText;
    private final String purchaseText;
    private final String waitText;
    private final String lockedText;
    private final String emptyText;
    private double scrollOffset = 0, targetScroll = 0;
    private boolean draggingScrollbar;
    private double scrollbarDragOffset;
    private float[] entryHoverAnims;

    public TradeListPanel(TradeScreen screen, Font font) {
        this.screen = screen;
        this.font = font;
        plusWidth = font.width(plusText);
        statusMaxedText = Component.translatable("arc_quest.gui.trade.status.maxed").getString();
        statusLockedText = Component.translatable("arc_quest.gui.trade.status.locked").getString();
        purchaseText = Component.translatable("arc_quest.gui.trade.btn.purchase").getString();
        waitText = Component.translatable("arc_quest.gui.trade.btn.wait").getString();
        lockedText = Component.translatable("arc_quest.gui.trade.btn.locked").getString();
        emptyText = Component.translatable("arc_quest.gui.trade.btn.empty").getString();
        resetAnims();
    }

    public void resetAnims() {
        targetScroll = 0;
        scrollOffset = 0;
        draggingScrollbar = false;
        scrollbarDragOffset = 0;
        entryHoverAnims = new float[screen.getFilteredEntries().size()];
        stateCache.clear();
        visualCache.clear();
    }

    public void setHoverAnim(int index, float val) {
        if (index >= 0 && index < entryHoverAnims.length) entryHoverAnims[index] = val;
    }

    public void clampScroll(int listHeight) {
        int maxScroll = Math.max(0, screen.getFilteredEntries().size() * STRIDE + 4 - listHeight);
        targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));
    }

    public void mouseScrolled(double d, int listHeight) {
        targetScroll -= d * STRIDE;
        clampScroll(listHeight);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int listX, int listY, int listWidth, int listHeight, int button) {
        if (button != 0 || !hasScrollbar(listHeight)) return false;

        int thumbTop = getScrollbarThumbTop(listY, listHeight);
        int thumbHeight = getScrollbarThumbHeight(listHeight);
        int scrollbarX = listX + listWidth - 8;
        if (mouseX < scrollbarX - 3 || mouseX >= listX + listWidth || mouseY < listY || mouseY >= listY + listHeight) {
            return false;
        }

        if (mouseY >= thumbTop && mouseY < thumbTop + thumbHeight) {
            draggingScrollbar = true;
            scrollbarDragOffset = mouseY - thumbTop;
        } else {
            setScrollFromScrollbarPosition(mouseY - thumbHeight / 2.0, listY, listHeight);
        }
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int listX, int listY, int listWidth, int listHeight, int button) {
        if (!draggingScrollbar || button != 0) return false;
        setScrollFromScrollbarPosition(mouseY - scrollbarDragOffset, listY, listHeight);
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || !draggingScrollbar) return false;
        draggingScrollbar = false;
        scrollbarDragOffset = 0;
        return true;
    }

    private void setScrollFromScrollbarPosition(double thumbTop, int listY, int listHeight) {
        int maxScroll = getMaxScroll(listHeight);
        int thumbHeight = getScrollbarThumbHeight(listHeight);
        int travel = Math.max(0, listHeight - thumbHeight);
        double normalized = travel == 0 ? 0 : (thumbTop - listY) / travel;
        targetScroll = clamp(normalized * maxScroll, 0, maxScroll);
        scrollOffset = targetScroll;
    }

    int getMaxScroll(int listHeight) {
        return Math.max(0, screen.getFilteredEntries().size() * STRIDE + 4 - listHeight);
    }

    int getScrollbarThumbHeight(int listHeight) {
        if (listHeight <= 0) return 0;
        int contentHeight = screen.getFilteredEntries().size() * STRIDE + 4;
        return Math.min(listHeight, Math.max(16, (int) ((float) listHeight / contentHeight * listHeight)));
    }

    int getScrollbarThumbTop(int listY, int listHeight) {
        int maxScroll = getMaxScroll(listHeight);
        int thumbHeight = getScrollbarThumbHeight(listHeight);
        int travel = Math.max(0, listHeight - thumbHeight);
        return listY + (maxScroll == 0 ? 0 : (int) (scrollOffset / maxScroll * travel));
    }

    private boolean hasScrollbar(int listHeight) {
        return getMaxScroll(listHeight) > 0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    public double getScrollOffset() {
        return scrollOffset;
    }

    // 【终极优化：内联矩形拼接边框】
    private void drawFastFrame(GuiGraphics g, int x, int y, int w, int h, int thickness, int color) {
        g.fill(x, y, x + w, y + thickness, color);
        g.fill(x, y + h - thickness, x + w, y + h, color);
        g.fill(x, y + thickness, x + thickness, y + h - thickness, color);
        g.fill(x + w - thickness, y + thickness, x + w, y + h - thickness, color);
    }

    public void render(GuiGraphics g, int rx, int ry, int rw, int rh, int mx, int my, float dt, float alpha, boolean isClosing, float fastClose) {
        clampScroll(rh);
        scrollOffset = HudAnimUtil.smoothHalfLife((float) scrollOffset, (float) targetScroll, 0.06f, dt);
        g.enableScissor(rx, ry, rx + rw, ry + rh);

        float contentScale = isClosing ? HudAnimUtil.easeInCubic(fastClose) : 1.0f;
        ClientTradeCache cache = ClientTradeCache.INSTANCE;
        List<TradeEntry> entries = screen.getFilteredEntries();

        long now = System.currentTimeMillis();
        // 提炼脉冲运算，全场共享一个时间戳！
        float pulse = (float) (Math.sin(Util.getMillis() / 200.0) * 0.5 + 0.5);
        float syncBreath = (float) (Math.sin(Util.getMillis() / 150.0) * 0.5 + 0.5);

        int firstVisible = Math.max(0, (int) Math.floor((scrollOffset - 8 - CARD_HEIGHT) / STRIDE));
        int lastVisible = Math.min(entries.size() - 1, (int) Math.ceil((scrollOffset + rh - 8) / STRIDE));
        ClientTradeCache.FeedbackSnapshot fbs = screen.getLastClickedGi() >= 0 ? cache.feedbackSnapshot(screen.getShopId()) : null;

        // =========================================================================
        // PASS 1: 纯 2D 渲染通道
        // =========================================================================
        for (int i = firstVisible; i <= lastVisible; i++) {
            TradeEntry entry = entries.get(i);
            EntryVisualCache visual = getVisualCache(entry);
            int drawY = ry + (int) (i * STRIDE - scrollOffset) + 8;

            EntryRenderState state = stateCache.get(entry.getEntryId());
            if (state == null || now - state.lastUpdateTime > CACHE_VALID_MS) {
                state = stateCache.computeIfAbsent(entry.getEntryId(), id -> new EntryRenderState());
                state.globalIndex = cache.getGlobalIndex(screen.getShopId(), entry.getEntryId());
                if (state.globalIndex != -1) {
                    state.onCd = cache.isEntryCoolingDown(screen.getShopId(), state.globalIndex, entry);
                    state.maxed = cache.isPurchaseLimitReached(screen.getShopId(), state.globalIndex, entry);
                    state.locked = !state.onCd && !state.maxed && cache.isConditionBlocked(screen.getShopId(), state.globalIndex, entry);
                    state.canBuy = !state.onCd && !state.maxed && !state.locked;
                }
                state.lastUpdateTime = now;
            }

            int gi = state.globalIndex;
            if (gi == -1) continue;

            boolean hov = !isClosing && dt > 0 && mx >= rx && mx < rx + rw && my >= drawY && my < drawY + CARD_HEIGHT && my >= ry && my <= ry + rh;
            HudCursorManager.requestPointer(hov && state.canBuy && alpha > 0.05f);
            entryHoverAnims[i] = HudAnimUtil.smoothHalfLife(entryHoverAnims[i], hov && state.canBuy ? 1f : 0f, 0.08f, dt);
            float hEase = HudAnimUtil.easeOutCubic(entryHoverAnims[i]);

            int bgA = (int) ((0x22 + 0x33 * hEase) * alpha);
            int bdA = (int) ((0x44 + 0x66 * hEase) * alpha);
            int bRgb = hov && state.canBuy ? screen.getThemeColorForEntry(entry) : 0xFFFFFF;

            boolean hasShortfall = false;
            if (gi == screen.getLastClickedGi()) {
                hasShortfall = fbs != null && entry.getEntryId().equals(fbs.lastFailedEntryId()) && !fbs.shortfallLines().isEmpty();

                if (screen.isFeedbackSuccess() && screen.getFeedbackAnim() > 0) {
                    bdA = Math.min(255, bdA + (int) (180 * screen.getFeedbackAnim() * alpha));
                    bRgb = HudAnimUtil.lerpColor(bRgb, 0x55FF55, screen.getFeedbackAnim());
                } else if (!screen.isFeedbackSuccess() && screen.getFeedbackAnim() > 0) {
                    float intensity = screen.getFeedbackAnim();
                    bdA = Math.min(255, bdA + (int) (180 * intensity * alpha));
                    bRgb = HudAnimUtil.lerpColor(bRgb, 0xFF3333, intensity);
                } else if (hasShortfall) {
                    bdA = Math.min(255, bdA + (int) (40 * alpha));
                    bRgb = HudAnimUtil.lerpColor(bRgb, 0xAA4444, 0.35f);
                }
            }

            int cx = rx + 8, cy = drawY, cw = rw - 16, ch = CARD_HEIGHT;

            g.fill(cx, cy, cx + cw, cy + ch, (bgA << 24) | 0x05050A);
            drawFastFrame(g, cx, cy, cw, ch, 1, (bdA << 24) | (bRgb & 0xFFFFFF));

            if (contentScale > 0.01f) {
                g.pose().pushPose();
                g.pose().translate(cx + cw / 2f, cy + ch / 2f, 0);
                float aScale = contentScale + hEase * 0.02f;
                g.pose().scale(aScale, aScale, 1f);
                g.pose().translate(-(cx + cw / 2f), -(cy + ch / 2f), 0);

                if (state.onCd || state.maxed || state.locked) {
                    int pA = (int) (255 * alpha * (0.6f + 0.4f * pulse));
                    int pC = state.onCd ? 0xFF6666 : (state.maxed ? 0xAAAAAA : 0x4488CC);
                    int maskA1 = HudAnimUtil.withAlpha(pC, (int) (pA * 0.1f));
                    int maskA2 = HudAnimUtil.withAlpha(pC, pA);

                    g.fill(cx, cy, cx + cw, cy + ch, maskA1);
                    g.fill(cx - 1, cy - 1, cx + cw + 1, cy, maskA2);
                    g.fill(cx - 1, cy + ch, cx + cw + 1, cy + ch + 1, maskA2);
                    g.fill(cx - 1, cy, cx, cy + ch, maskA2);
                    g.fill(cx + cw, cy, cx + cw + 1, cy + ch, maskA2);
                    g.fill(cx, cy, cx + cw, cy + ch, HudAnimUtil.withAlpha(0x000000, (int) (160 * alpha)));
                }

                if (entry.getRewardIcon() != null) {
                    screen.drawAdaptiveIcon(g, entry.getRewardIcon(), cx + 7, cy + 16, 16, 16, alpha);
                }

                int textX = cx + 52 + (int) (4 * hEase);
                String statStr = state.onCd ? cache.getCooldownText(screen.getShopId(), gi)
                        : (state.maxed ? statusMaxedText : (state.locked ? statusLockedText : ""));
                int scColor = state.onCd ? 0xFF5555 : (state.maxed ? 0xAAAAAA : 0x4488CC);

                int mNW = cw - 140 - (statStr.isEmpty() ? 0 : font.width(statStr) + 6);
                String nStr = getClippedName(visual, mNW);

                g.drawString(font, nStr, textX, cy + 10, HudAnimUtil.withAlpha(state.canBuy ? 0xFFFFFF : 0x999999, (int) (255 * alpha)), false);
                if (!statStr.isEmpty()) {
                    g.drawString(font, statStr, textX + visual.clippedNameWidth + 6, cy + 10, HudAnimUtil.withAlpha(scColor, (int) (255 * alpha)), false);
                }

                int cX = textX, costY = cy + 26;
                for (int j = 0; j < visual.costs.size(); j++) {
                    CostVisual cost = visual.costs.get(j);
                    if (j > 0) {
                        g.drawString(font, plusText, cX, costY, HudAnimUtil.withAlpha(0x777777, (int) (255 * alpha)), false);
                        cX += plusWidth + 2;
                    }

                    if (cost.icon() != null) {
                        g.pose().pushPose();
                        g.pose().translate(cX, costY - 1, 0);
                        g.pose().scale(0.6f, 0.6f, 1f);
                        screen.drawAdaptiveIcon(g, cost.icon(), 0, 0, 16, 16, alpha);
                        g.pose().popPose();
                    }
                    cX += 12;

                    g.drawString(font, cost.text(), cX, costY, HudAnimUtil.withAlpha(state.canBuy ? 0xDDDDDD : 0x777777, (int) (255 * alpha)), false);
                    cX += cost.textWidth() + 4;
                    if (cX > cx + cw - 100) {
                        g.drawString(font, "...", cX, costY, HudAnimUtil.withAlpha(0x777777, (int) (255 * alpha)), false);
                        break;
                    }
                }

                int btnW = 80, btnH = 24, btnX = cx + cw - btnW - 12, btnY = cy + (ch - btnH) / 2;
                String btnText = state.canBuy ? purchaseText : (state.onCd ? waitText : (state.locked ? lockedText : emptyText));
                int btnC = state.canBuy
                        ? ((hov && mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH) ? 0xFFFFFF : screen.getThemeColorForEntry(entry))
                        : (state.locked ? 0x4488CC : 0x888888);

                g.fill(btnX, btnY, btnX + btnW, btnY + btnH, HudAnimUtil.withAlpha(btnC, (int) (40 * alpha)));
                drawFastFrame(g, btnX, btnY, btnW, btnH, 1, HudAnimUtil.withAlpha(btnC, (int) (200 * alpha)));
                g.drawCenteredString(font, btnText, btnX + btnW / 2, btnY + 8, HudAnimUtil.withAlpha(btnC, (int) (255 * alpha)));

                g.pose().popPose();
            }
        }

        // =========================================================================
        // PASS 2: 纯 3D 渲染通道
        // =========================================================================
        if (contentScale > 0.01f) {
            for (int i = firstVisible; i <= lastVisible; i++) {
                TradeEntry entry = entries.get(i);
                EntryRenderState state = stateCache.get(entry.getEntryId());
                if (state == null || state.globalIndex == -1) continue;

                EntryVisualCache visual = getVisualCache(entry);
                int drawY = ry + (int) (i * STRIDE - scrollOffset) + 8;
                int cx = rx + 8, cy = drawY, cw = rw - 16, ch = CARD_HEIGHT;

                float hEase = HudAnimUtil.easeOutCubic(entryHoverAnims[i]);

                g.pose().pushPose();
                g.pose().translate(cx + cw / 2f, cy + ch / 2f, 0);
                float aScale = contentScale + hEase * 0.02f;
                g.pose().scale(aScale, aScale, 1f);
                g.pose().translate(-(cx + cw / 2f), -(cy + ch / 2f), 0);

                if (entry.getRewardIcon() == null && !visual.mainStack.isEmpty()) {
                    g.pose().pushPose();
                    g.pose().translate(cx + 12, cy + 16, 0);
                    g.pose().scale(1.2f, 1.2f, 1f);
                    g.renderFakeItem(visual.mainStack, 0, 0);
                    g.pose().popPose();
                }

                int textX = cx + 52 + (int) (4 * hEase);
                int cX = textX, costY = cy + 26;
                for (int j = 0; j < visual.costs.size(); j++) {
                    CostVisual cost = visual.costs.get(j);
                    if (j > 0) cX += plusWidth + 2;

                    if (cost.icon() == null && !cost.stack().isEmpty()) {
                        g.pose().pushPose();
                        g.pose().translate(cX, costY - 1, 0);
                        g.pose().scale(0.6f, 0.6f, 1f);
                        g.renderFakeItem(cost.stack(), 0, 0);
                        g.pose().popPose();
                    }

                    cX += 12 + cost.textWidth() + 4;
                    if (cX > cx + cw - 100) break;
                }

                g.pose().popPose();
            }
        }

        g.disableScissor();

        int maxScroll = getMaxScroll(rh);
        if (maxScroll > 0) {
            int th = getScrollbarThumbHeight(rh);
            int ty = getScrollbarThumbTop(ry, rh);
            g.fill(rx + rw - 6, ty, rx + rw - 4, ty + th, HudAnimUtil.withAlpha(0xFFFFFF, (int) (180 * alpha)));
        }
    }

    private EntryVisualCache getVisualCache(TradeEntry entry) {
        return visualCache.computeIfAbsent(entry.getEntryId(), id -> {
            EntryVisualCache visual = new EntryVisualCache();
            visual.name = entry.getDisplayName().getString();
            visual.nameWidth = font.width(visual.name);
            visual.mainStack = screen.getIconStackForEntry(entry);
            visual.costs = new ArrayList<>();
            for (ITradeOffer cost : entry.getCosts()) {
                ItemStack stack = cost.getIcon() == null ? screen.getIconStackForOffer(cost) : ItemStack.EMPTY;
                String text = cost.describe().getString();
                visual.costs.add(new CostVisual(cost.getIcon(), stack, text, font.width(text)));
            }
            return visual;
        });
    }

    private String getClippedName(EntryVisualCache visual, int maxWidth) {
        if (visual.lastMaxNameWidth == maxWidth && visual.clippedName != null) return visual.clippedName;
        if (visual.nameWidth > maxWidth) {
            visual.clippedName = font.plainSubstrByWidth(visual.name, Math.max(0, maxWidth - 8)) + "...";
            visual.clippedNameWidth = font.width(visual.clippedName);
        } else {
            visual.clippedName = visual.name;
            visual.clippedNameWidth = visual.nameWidth;
        }
        visual.lastMaxNameWidth = maxWidth;
        return visual.clippedName;
    }

    public TradeEntry getHoveredEntry(int mx, int my, int rx, int ry, int rw, int rh) {
        if (mx >= rx && mx < rx + rw && my >= ry && my <= ry + rh) {
            int vi = (int) ((my - ry + scrollOffset - 8) / STRIDE);
            if (vi >= 0 && vi < screen.getFilteredEntries().size() && my >= ry + (int) (vi * STRIDE - scrollOffset) + 8 && my < ry + (int) (vi * STRIDE - scrollOffset) + 8 + CARD_HEIGHT) {
                return screen.getFilteredEntries().get(vi);
            }
        }
        return null;
    }

    private static class EntryRenderState {
        int globalIndex;
        boolean onCd;
        boolean maxed;
        boolean locked;
        boolean canBuy;
        long lastUpdateTime;
    }

    private static class EntryVisualCache {
        String name;
        int nameWidth;
        ItemStack mainStack;
        List<CostVisual> costs;
        int lastMaxNameWidth = Integer.MIN_VALUE;
        String clippedName;
        int clippedNameWidth;
    }

    private record CostVisual(ResourceLocation icon, ItemStack stack, String text, int textWidth) {
    }
}
