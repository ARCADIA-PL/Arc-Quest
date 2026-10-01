package org.arcadia.arc_quest.client.hud.shop;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.network.ClientTradeCache;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TradeGridPanel {

    private static final long CACHE_VALID_MS = 100;
    private final SimpleTradePanel screen;
    private final Font font;
    private final Map<String, EntryRenderState> stateCache = new HashMap<>();
    private final Map<String, EntryVisualCache> visualCache = new HashMap<>();
    private final String statusMaxedText;
    private final String statusLockedText;
    private float openAnimTime = 0f;
    private float[] hoverAnims;
    private FrameAnimData[] animData = new FrameAnimData[0];

    public TradeGridPanel(SimpleTradePanel screen, Font font) {
        this.screen = screen;
        this.font = font;
        hoverAnims = new float[screen.getEntries().size()];
        statusMaxedText = Component.translatable("arc_quest.gui.trade.status.maxed").getString();
        statusLockedText = Component.translatable("arc_quest.gui.trade.status.locked").getString();
    }

    public void updateHoverAnimsSize(int size) {
        hoverAnims = new float[size];
    }

    public void setHoverAnim(int index, float val) {
        if (index >= 0 && index < hoverAnims.length) hoverAnims[index] = val;
    }

    private void ensureAnimDataSize(int size) {
        if (animData.length < size) {
            FrameAnimData[] newData = new FrameAnimData[size];
            System.arraycopy(animData, 0, newData, 0, animData.length);
            for (int i = animData.length; i < size; i++) newData[i] = new FrameAnimData();
            animData = newData;
        }
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

    // 【终极优化：内联矩形拼接边框，彻底并入 GuiGraphics 原生 Batching，无额外状态打断】
    private void drawFastFrame(GuiGraphics g, int x, int y, int w, int h, int thickness, int color) {
        g.fill(x, y, x + w, y + thickness, color); // Top
        g.fill(x, y + h - thickness, x + w, y + h, color); // Bottom
        g.fill(x, y + thickness, x + thickness, y + h - thickness, color); // Left
        g.fill(x + w - thickness, y + thickness, x + w, y + h - thickness, color); // Right
    }

    public void render(GuiGraphics g, int mx, int my, float pt, float dt, float easeProgress, boolean isClosing) {
        if (!isClosing && dt > 0) openAnimTime = HudAnimUtil.advanceByDuration(openAnimTime, TradeGridAnimation.duration(screen.getEntries().size()), dt);
        Layout l = computeLayout();
        List<TradeEntry> entries = screen.getEntries();
        float alpha = screen.getEffectiveAlpha();

        ensureAnimDataSize(entries.size());

        long now = System.currentTimeMillis();
        ClientTradeCache cache = ClientTradeCache.INSTANCE;
        ClientTradeCache.FeedbackSnapshot fbs = screen.getLastClickedGi() >= 0 ? cache.feedbackSnapshot(screen.getShopId()) : null;

        float pulse = (float) (Math.sin(Util.getMillis() / 200.0) * 0.5 + 0.5);
        float syncBreath = (float) (Math.sin(Util.getMillis() / 150.0) * 0.5 + 0.5);

        // 【预计算通道】消除每帧循环内对数学和字符串判断的冗余GC，一次性算好布局和矩阵参数
        for (int i = 0; i < entries.size(); i++) {
            FrameAnimData fd = animData[i];
            TradeEntry entry = entries.get(i);
            int targetX = l.startX() + (i % l.cols()) * (l.cardW() + l.gap());
            int targetY = l.startY() + (i / l.cols()) * (l.cardH() + l.gap());

            float flyEase;
            if (isClosing) {
                flyEase = easeProgress;
                float dir = (targetX + l.cardW() / 2f) < screen.width / 2f ? -1f : 1f;
                fd.drawX = targetX + dir * ((1f - easeProgress) * (screen.width / 2f + 100f));
                fd.drawY = targetY + (1f - easeProgress) * 30f;
            } else {
                float flyProgress = TradeGridAnimation.openingProgress(openAnimTime, i, entries.size());
                flyEase = HudAnimUtil.easeOutBack(flyProgress);
                fd.drawX = targetX - ((targetX + l.cardW() / 2f) - screen.width / 2f) * (1f - flyEase);
                fd.drawY = targetY - ((targetY + l.cardH() / 2f) - screen.height / 2f) * (1f - flyEase);
            }

            fd.visible = (flyEase >= 0.01f || isClosing);
            fd.clampedEase = Math.max(0f, Math.min(1f, flyEase));
            fd.contentScale = isClosing ? HudAnimUtil.easeInCubic(Math.max(0f, (screen.getTransitionAnim() - 0.4f) / 0.6f)) : flyEase;
            // Include the maximum hover expansion and halo at the CURRENT animated position.
            float maximumScale = Math.max((isClosing ? 1f : flyEase) + .06f, fd.contentScale);
            fd.visible &= TradeGridAnimation.intersectsViewport(fd.drawX, fd.drawY, l.cardW(), l.cardH(),
                    maximumScale, screen.width, screen.height);
            if (!fd.visible) {
                hoverAnims[i] = HudAnimUtil.smoothHalfLife(hoverAnims[i], 0, .05f, dt);
                continue;
            }

            boolean hov = (!isClosing && screen.getTransitionAnim() >= 0.9f) && mx >= targetX && mx < targetX + l.cardW() && my >= targetY && my < targetY + l.cardH();

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

            hoverAnims[i] = HudAnimUtil.smoothHalfLife(hoverAnims[i], hov && state.canBuy ? 1f : 0f, 0.05f, dt);
            HudCursorManager.requestPointer(hov && state.canBuy);
            fd.hEase = HudAnimUtil.easeOutCubic(hoverAnims[i]);
            fd.bgScale = (isClosing ? 1.0f : flyEase) + fd.hEase * 0.06f;
        }

        g.pose().pushPose();
        g.pose().translate(0, (1f - easeProgress) * -30f, 0);
        g.drawCenteredString(font, screen.getShop().getDisplayName(), screen.width / 2, l.startY() - 24, HudAnimUtil.withAlpha(screen.getShop().getThemeColor(), (int) (255 * alpha)));
        g.pose().popPose();

        // =========================================================================
        // PASS 1: 纯 2D 渲染通道 (背景、边框、遮罩、文字、2D图标)
        // 彻底剔除 g.renderFakeItem()！
        // =========================================================================
        for (int i = 0; i < entries.size(); i++) {
            FrameAnimData fd = animData[i];
            if (!fd.visible) continue;

            TradeEntry entry = entries.get(i);
            EntryRenderState state = stateCache.get(entry.getEntryId());
            if (state == null || state.globalIndex == -1) continue;

            int gi = state.globalIndex;
            EntryVisualCache visual = getVisualCache(entry);

            int bRgb = (gi == screen.getLastClickedGi() && screen.getFeedbackAnim() > 0) ? (screen.isFeedbackSuccess() ? 0x55FF55 : 0xFF5555) : screen.getThemeColorForEntry(entry);
            int bgA = (int) ((0x44 + 0x33 * fd.hEase) * fd.clampedEase * alpha);
            int bdA = (int) ((0x66 + 0x66 * fd.hEase) * fd.clampedEase * alpha);

            boolean hasShortfall = fbs != null && entry.getEntryId().equals(fbs.lastFailedEntryId()) && !fbs.shortfallLines().isEmpty();

            if (gi == screen.getLastClickedGi()) {
                if (screen.isFeedbackSuccess() && screen.getFeedbackAnim() > 0) {
                    bdA = Math.min(255, bdA + (int) (170 * screen.getFeedbackAnim() * alpha));
                    bRgb = HudAnimUtil.lerpColor(bRgb, 0x55FF55, screen.getFeedbackAnim());
                } else if (!screen.isFeedbackSuccess() && (screen.getFeedbackAnim() > 0 || hasShortfall)) {
                    float intensity = Math.max(screen.getFeedbackAnim(), hasShortfall ? (syncBreath * 0.6f + 0.4f) : 0f);
                    bdA = Math.min(255, bdA + (int) (170 * intensity * alpha));
                    bRgb = HudAnimUtil.lerpColor(bRgb, 0xFF3333, intensity);
                }
            }

            float cX = fd.drawX + l.cardW() / 2f;
            float cY = fd.drawY + l.cardH() / 2f;

            g.pose().pushPose();
            g.pose().translate(cX, cY, 0);
            g.pose().scale(fd.bgScale, fd.bgScale, 1f);
            g.pose().translate(-cX, -cY, 0);
            g.fill((int) fd.drawX, (int) fd.drawY, (int) (fd.drawX + l.cardW()), (int) (fd.drawY + l.cardH()), (bgA << 24) | 0x05050A);
            drawFastFrame(g, (int) fd.drawX, (int) fd.drawY, l.cardW(), l.cardH(), 1, (bdA << 24) | (bRgb & 0xFFFFFF));
            TradeUpdateHighlights.draw(g, screen.getShopId(), entry.getEntryId(), (int) fd.drawX, (int) fd.drawY, l.cardW(), l.cardH(), alpha * fd.clampedEase);
            g.pose().popPose();

            if (fd.contentScale > 0.01f) {
                g.pose().pushPose();
                g.pose().translate(cX, cY, 0);
                g.pose().scale(fd.contentScale, fd.contentScale, 1f);
                g.pose().translate(-cX, -cY, 0);

                if (state.onCd || state.maxed || state.locked) {
                    int pA = (int) (255 * fd.clampedEase * alpha * (0.6f + 0.4f * pulse));
                    int pC = state.onCd ? 0xFF6666 : (state.maxed ? 0xAAAAAA : 0x4488CC);
                    int dx = (int) fd.drawX, dy = (int) fd.drawY, dw = l.cardW(), dh = l.cardH();

                    g.fill(dx, dy, dx + dw, dy + dh, HudAnimUtil.withAlpha(pC, (int) (pA * 0.12f)));
                    g.fill(dx - 1, dy - 1, dx + dw + 1, dy, HudAnimUtil.withAlpha(pC, pA));
                    g.fill(dx - 1, dy + dh, dx + dw + 1, dy + dh + 1, HudAnimUtil.withAlpha(pC, pA));
                    g.fill(dx - 1, dy, dx, dy + dh, HudAnimUtil.withAlpha(pC, pA));
                    g.fill(dx + dw, dy, dx + dw + 1, dy + dh, HudAnimUtil.withAlpha(pC, pA));
                    g.fill(dx, dy, dx + dw, dy + dh, HudAnimUtil.withAlpha(0x000000, (int) (160 * fd.clampedEase * alpha)));
                }

                int textY = (int) fd.drawY + (l.cardH() - font.lineHeight * 2 - 4) / 2;
                String statusStr = state.onCd ? cache.getCooldownText(screen.getShopId(), gi) : (state.maxed ? statusMaxedText : (state.locked ? statusLockedText : ""));
                int scColor = state.onCd ? 0xFF5555 : (state.maxed ? 0xAAAAAA : 0x4488CC);

                int maxNameW = l.cardW() - 34 - (statusStr.isEmpty() ? 0 : font.width(statusStr) + 4);
                String nameStr = getClippedName(visual, maxNameW);

                g.drawString(font, nameStr, (int) fd.drawX + 26, textY, HudAnimUtil.withAlpha(state.canBuy ? 0xFFFFFF : 0x999999, (int) (255 * fd.clampedEase * alpha)), true);
                if (!statusStr.isEmpty()) {
                    g.drawString(font, statusStr, (int) fd.drawX + 26 + visual.clippedNameWidth + 4, textY, HudAnimUtil.withAlpha(scColor, (int) (255 * fd.clampedEase * alpha)), true);
                }

                g.pose().popPose();
            }
        }

        // =========================================================================
        // PASS 2: Item slots share one layout with hover, tooltips and JEI hit regions.
        // =========================================================================
        for (int i = 0; i < entries.size(); i++) {
            FrameAnimData fd = animData[i];
            if (!fd.visible || fd.contentScale <= 0.01f) continue;

            TradeEntry entry = entries.get(i);
            EntryRenderState state = stateCache.get(entry.getEntryId());
            if (state == null || state.globalIndex == -1) continue;

            float cX = fd.drawX + l.cardW() / 2f;
            float cY = fd.drawY + l.cardH() / 2f;

            g.pose().pushPose();
            g.pose().translate(cX, cY, 0);
            g.pose().scale(fd.contentScale, fd.contentScale, 1f);
            g.pose().translate(-cX, -cY, 0);

            int itemY = (int) fd.drawY + (l.cardH() - 16) / 2;
            screen.ingredientSlots().rewards(g, entry, (int) fd.drawX + 5, itemY - 1,
                    18, 18, mx, my, dt, fd.clampedEase * alpha);
            int costY = (int) fd.drawY + (l.cardH() - font.lineHeight * 2 - 4) / 2 + font.lineHeight + 3;
            screen.ingredientSlots().costs(g, entry, (int) fd.drawX + 26, costY, l.cardW() - 34,
                    10, mx, my, dt, fd.clampedEase * alpha, 1, state.canBuy ? screen.getThemeColorForEntry(entry) : 0x777777);
            g.pose().popPose();
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

    private static class FrameAnimData {
        float drawX, drawY, bgScale, contentScale, clampedEase, hEase;
        boolean visible;
    }


    private record Layout(int cols, int rows, int cardW, int cardH, int gap, int startX, int startY, int totalW,
                          int totalH) {
    }
}
