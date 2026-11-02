package org.arcadia.arc_quest.client.hud.shop;

import org.arcadia.arc_quest.client.hud.style.HudStyleRenderer;
import org.arcadia.arc_quest.client.hud.style.HudDraw;
import org.arcadia.arc_quest.client.hud.style.OrePalette;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiScreenIngredients;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.network.ClientTradeCache;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TradeListPanel {
    public static final int CARD_HEIGHT = 48;
    private static final long CACHE_VALID_MS = 100;
    private int cardHeight() { return screen.rows().cardHeight(); }
    private int stride() { return screen.rows().stride(); }
    private int unit(int size) { return screen.unit(size); }
    private final TradeScreen screen;
    private final Font font;
    private final TradeUpdateNavigator updateNavigator;
    private boolean navigationEnabled;
    private final Map<String, EntryRenderState> stateCache = new HashMap<>();
    private final Map<String, EntryVisualCache> visualCache = new HashMap<>();
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
        updateNavigator = new TradeUpdateNavigator(screen.getShopId(), font, screen);
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
        int maxScroll = Math.max(0, screen.getFilteredEntries().size() * stride() + unit(4) - listHeight);
        targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    int navigationInset(int outerHeight) {
        navigationEnabled = outerHeight > screen.rows().navigationHeight() * 2 + cardHeight() && getMaxScroll(outerHeight) > 0;
        return navigationEnabled ? screen.rows().navigationHeight() : 0;
    }

    void restoreScroll(TradeListPanel previous) {
        scrollOffset = previous.scrollOffset;
        targetScroll = previous.targetScroll;
    }

    public void mouseScrolled(double d, int listHeight) {
        if (screen.ingredientSlots().scroll(d)) return;
        targetScroll -= d * stride();
        clampScroll(listHeight);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int listX, int listY, int listWidth, int listHeight, int button) {
        if (button != 0 || !hasScrollbar(listHeight)) return false;
        if (navigationEnabled) {
            int index = updateNavigator.clicked(screen.getFilteredEntries(), scrollOffset,
                    listX, listY, listWidth, listHeight, mouseX, mouseY);
            if (index >= 0) {
                targetScroll = TradeUpdateScrollIndex.centeredScroll(index, listHeight, getMaxScroll(listHeight), stride(), cardHeight(), unit(8));
                screen.playClick();
                return true;
            }
        }

        int thumbTop = getScrollbarThumbTop(listY, listHeight);
        int thumbHeight = getScrollbarThumbHeight(listHeight);
        int scrollbarX = listX + listWidth - unit(8);
        if (mouseX < scrollbarX - unit(3) || mouseX >= listX + listWidth || mouseY < listY || mouseY >= listY + listHeight) {
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
        return Math.max(0, screen.getFilteredEntries().size() * stride() + unit(4) - listHeight);
    }

    int getScrollbarThumbHeight(int listHeight) {
        if (listHeight <= 0) return 0;
        int contentHeight = screen.getFilteredEntries().size() * stride() + unit(4);
        return Math.min(listHeight, Math.max(unit(16), (int) ((float) listHeight / contentHeight * listHeight)));
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
        HudDraw.fill(g, x, y, x + w, y + thickness, color);
        HudDraw.fill(g, x, y + h - thickness, x + w, y + h, color);
        HudDraw.fill(g, x, y + thickness, x + thickness, y + h - thickness, color);
        HudDraw.fill(g, x + w - thickness, y + thickness, x + w, y + h - thickness, color);
    }

    public void render(GuiGraphics g, int rx, int ry, int rw, int rh, int mx, int my, float dt, float alpha, boolean isClosing, float fastClose) {
        clampScroll(rh);
        scrollOffset = HudAnimUtil.smoothHalfLife((float) scrollOffset, (float) targetScroll, 0.06f, dt);
        screen.ingredientSlots().clip(rx, ry, rw, rh);
        JeiScreenIngredients.enableScissor(screen, g, rx, ry, rx + rw, ry + rh);

        float contentScale = isClosing ? HudAnimUtil.easeInCubic(fastClose) : 1.0f;
        ClientTradeCache cache = ClientTradeCache.INSTANCE;
        List<TradeEntry> entries = screen.getFilteredEntries();
        if (entryHoverAnims.length != entries.size()) entryHoverAnims = Arrays.copyOf(entryHoverAnims, entries.size());

        long now = System.currentTimeMillis();
        // 提炼脉冲运算，全场共享一个时间戳！
        float pulse = (float) (Math.sin(Util.getMillis() / 200.0) * 0.5 + 0.5);
        float syncBreath = (float) (Math.sin(Util.getMillis() / 150.0) * 0.5 + 0.5);

        int firstVisible = Math.max(0, (int) Math.floor((scrollOffset - unit(8) - cardHeight()) / stride()));
        int lastVisible = Math.min(entries.size() - 1, (int) Math.ceil((scrollOffset + rh - unit(8)) / stride()));
        ClientTradeCache.FeedbackSnapshot fbs = screen.getLastClickedGi() >= 0 ? cache.feedbackSnapshot(screen.getShopId()) : null;

        // =========================================================================
        // PASS 1: 纯 2D 渲染通道
        // =========================================================================
        for (int i = firstVisible; i <= lastVisible; i++) {
            TradeEntry entry = entries.get(i);
            EntryVisualCache visual = getVisualCache(entry);
            int drawY = ry + (int) (i * stride() - scrollOffset) + unit(8);

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

            boolean hov = !isClosing && dt > 0 && mx >= rx && mx < rx + rw && my >= drawY && my < drawY + cardHeight() && my >= ry && my <= ry + rh;
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

            int cx = rx + unit(8), cy = drawY, cw = rw - 2 * unit(8), ch = cardHeight();

            HudDraw.surface(g, cx, cy, cx + cw, cy + ch, (bgA << 24) | 0x05050A, alpha, OrePalette.isChromatic(bRgb) ? bRgb : screen.getThemeColorForEntry(entry));
            drawFastFrame(g, cx, cy, cw, ch, 1, (bdA << 24) | (bRgb & 0xFFFFFF));
            TradeUpdateHighlights.draw(g, screen.getShopId(), entry.getEntryId(), cx, cy, cw, ch, alpha);

            if (contentScale > 0.01f) {
                g.pose().pushPose();
                g.pose().translate(cx + cw / 2f, cy + ch / 2f, 0);
                float aScale = contentScale;
                g.pose().scale(aScale, aScale, 1f);
                g.pose().translate(-(cx + cw / 2f), -(cy + ch / 2f), 0);

                if (state.onCd || state.maxed || state.locked) {
                    int pA = (int) (255 * alpha * (0.6f + 0.4f * pulse));
                    int pC = state.onCd ? 0xFF6666 : (state.maxed ? 0xAAAAAA : 0x4488CC);
                    int maskA1 = HudAnimUtil.withAlpha(pC, (int) (pA * 0.1f));
                    int maskA2 = HudAnimUtil.withAlpha(pC, pA);

                    HudDraw.fill(g, cx, cy, cx + cw, cy + ch, maskA1);
                    HudDraw.fill(g, cx - 1, cy - 1, cx + cw + 1, cy, maskA2);
                    HudDraw.fill(g, cx - 1, cy + ch, cx + cw + 1, cy + ch + 1, maskA2);
                    HudDraw.fill(g, cx - 1, cy, cx, cy + ch, maskA2);
                    HudDraw.fill(g, cx + cw, cy, cx + cw + 1, cy + ch, maskA2);
                    HudDraw.fill(g, cx, cy, cx + cw, cy + ch, HudAnimUtil.withAlpha(0x000000, (int) (160 * alpha)));
                }

                TradeUpdateHighlights.badge(g, font, screen.getShopId(), entry.getEntryId(), cx + unit(7), cy + unit(3), alpha);

                int textX = cx + unit(52);
                String statStr = state.onCd ? cache.getCooldownText(screen.getShopId(), gi)
                        : (state.maxed ? statusMaxedText : (state.locked ? statusLockedText : ""));
                int scColor = state.onCd ? 0xFF5555 : (state.maxed ? 0xAAAAAA : 0x4488CC);

                float textScale = screen.getTextScale();
                int mNW = Math.max(1, (int) ((cw - unit(52) - screen.rows().buttonWidth() - unit(24) - (statStr.isEmpty() ? 0 : Math.round(font.width(statStr) * textScale) + unit(6))) / textScale));
                String nStr = getClippedName(visual, mNW);

                drawScaledString(g, nStr, textX, cy + unit(10),
                        HudAnimUtil.withAlpha(state.canBuy ? 0xFFFFFF : 0x999999, (int) (255 * alpha)), false);
                if (!statStr.isEmpty()) {
                    drawScaledString(g, statStr, textX + Math.round(visual.clippedNameWidth * textScale) + unit(6), cy + unit(10),
                            HudAnimUtil.withAlpha(scColor, (int) (255 * alpha)), false);
                }

                int btnW = screen.rows().buttonWidth(), btnH = screen.rows().buttonHeight(), btnX = cx + cw - btnW - unit(12), btnY = cy + (ch - btnH) / 2;
                String btnText = state.canBuy ? purchaseText : (state.onCd ? waitText : (state.locked ? lockedText : emptyText));
                int btnC = state.canBuy
                        ? ((hov && mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH) ? 0xFFFFFF : screen.getThemeColorForEntry(entry))
                        : (state.locked ? 0x4488CC : 0x888888);

                if (HudDraw.isStyled()) {
                    HudStyleRenderer.labeledButton(g, font, Component.empty(), btnX, btnY, btnW, btnH, HudStyleRenderer.Action.PRIMARY,
                            hov && mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH, state.canBuy, alpha, screen.getThemeColorForEntry(entry));
                    drawCenteredScaledString(g, btnText, btnX + btnW / 2, btnY + (btnH - screen.rows().lineHeight()) / 2,
                            HudAnimUtil.withAlpha(btnC, (int) (255 * alpha)));
                } else {
                    HudDraw.fill(g, btnX, btnY, btnX + btnW, btnY + btnH, HudAnimUtil.withAlpha(btnC, (int) (40 * alpha)));
                    drawFastFrame(g, btnX, btnY, btnW, btnH, 1, HudAnimUtil.withAlpha(btnC, (int) (200 * alpha)));
                    drawCenteredScaledString(g, btnText, btnX + btnW / 2, btnY + (btnH - screen.rows().lineHeight()) / 2,
                            HudAnimUtil.withAlpha(btnC, (int) (255 * alpha)));
                }

                g.pose().popPose();
            }
        }

        // =========================================================================
        // PASS 2: Item slots share one layout with hover, tooltips and JEI hit regions.
        // =========================================================================
        if (contentScale > 0.01f) {
            for (int i = firstVisible; i <= lastVisible; i++) {
                TradeEntry entry = entries.get(i);
                EntryRenderState state = stateCache.get(entry.getEntryId());
                if (state == null || state.globalIndex == -1) continue;

                int drawY = ry + (int) (i * stride() - scrollOffset) + unit(8);
                int cx = rx + unit(8), cy = drawY, cw = rw - 2 * unit(8), ch = cardHeight();

                g.pose().pushPose();
                g.pose().translate(cx + cw / 2f, cy + ch / 2f, 0);
                float aScale = contentScale;
                g.pose().scale(aScale, aScale, 1f);
                g.pose().translate(-(cx + cw / 2f), -(cy + ch / 2f), 0);

                screen.ingredientSlots().rewards(g, entry, cx + unit(11), cy + (ch - unit(22)) / 2, unit(22), unit(22), mx, my, dt, alpha);
                screen.ingredientSlots().costs(g, entry, cx + unit(52), cy + screen.rows().costTop(),
                        Math.max(1, cw - unit(52) - screen.rows().buttonWidth() - unit(24)), Math.max(unit(10), (int) Math.ceil(font.lineHeight * screen.getTextScale()) + 1),
                        mx, my, dt, alpha, screen.getTextScale(), state.canBuy ? 0xDDDDDD : 0x777777);

                g.pose().popPose();
            }
        }

        JeiScreenIngredients.disableScissor(screen, g);

        int maxScroll = getMaxScroll(rh);
        if (maxScroll > 0) {
            int th = getScrollbarThumbHeight(rh);
            int ty = getScrollbarThumbTop(ry, rh);
            HudDraw.fill(g, rx + rw - unit(6), ty, rx + rw - unit(4), ty + th, HudAnimUtil.withAlpha(0xFFFFFF, (int) (180 * alpha)));
        }
        if (navigationEnabled) {
            updateNavigator.render(g, entries, scrollOffset, rx, ry, rw, rh, mx, my, alpha,
                    !isClosing && dt > 0 && screen.getTransitionAnim() >= 0.9f);
        }
    }

    private EntryVisualCache getVisualCache(TradeEntry entry) {
        return visualCache.computeIfAbsent(entry.getEntryId(), id -> {
            EntryVisualCache visual = new EntryVisualCache();
            visual.name = entry.getDisplayName().getString();
            visual.nameWidth = font.width(visual.name);
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

    private void drawScaledString(GuiGraphics g, String text, int x, int y, int color, boolean shadow) {
        float scale = screen.getTextScale();
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        HudDraw.drawString(g, font, text, 0, 0, color, shadow);
        g.pose().popPose();
    }

    private void drawCenteredScaledString(GuiGraphics g, String text, int centerX, int y, int color) {
        float scale = screen.getTextScale();
        g.pose().pushPose();
        g.pose().translate(centerX, y, 0);
        g.pose().scale(scale, scale, 1f);
        HudDraw.drawCenteredString(g, font, text, 0, 0, color);
        g.pose().popPose();
    }

    public TradeEntry getHoveredEntry(int mx, int my, int rx, int ry, int rw, int rh) {
        if (mx >= rx && mx < rx + rw && my >= ry && my <= ry + rh) {
            int vi = (int) ((my - ry + scrollOffset - unit(8)) / stride());
            if (vi >= 0 && vi < screen.getFilteredEntries().size() && my >= ry + (int) (vi * stride() - scrollOffset) + unit(8) && my < ry + (int) (vi * stride() - scrollOffset) + unit(8) + cardHeight()) {
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
        int lastMaxNameWidth = Integer.MIN_VALUE;
        String clippedName;
        int clippedNameWidth;
    }

}
