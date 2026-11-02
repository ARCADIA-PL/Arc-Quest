package org.arcadia.arc_quest.client.hud.shop;

import org.arcadia.arc_quest.client.hud.style.HudDraw;
import org.arcadia.arc_quest.client.hud.style.HudStyleRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;

public class TradeCategoryPanel {
    private final TradeScreen screen;
    private final Font font;
    private int selectedCategoryIndex = 0;
    private float selectedCatSlide = 0f;
    private float[] catHoverAnims;
    private double scrollOffset = 0;
    private double targetScroll = 0;

    public TradeCategoryPanel(TradeScreen screen, Font font) {
        this.screen = screen;
        this.font = font;
        resetAnims();
    }

    public void resetAnims() {
        int rowCount = rowCount();
        selectedCategoryIndex = Math.max(0, Math.min(selectedCategoryIndex, rowCount - 1));
        catHoverAnims = new float[rowCount];
    }

    public int getSelectedIndex() {
        ensureState();
        return selectedCategoryIndex;
    }

    void selectCategory(String id) {
        selectedCategoryIndex = 0;
        for (int i = 0; i < screen.getShop().getCategories().size(); i++) {
            if (screen.getShop().getCategories().get(i).getId().equals(id)) selectedCategoryIndex = i + 1;
        }
        selectedCatSlide = selectedCategoryIndex;
        screen.filterEntries();
    }

    public void render(GuiGraphics g, int lx, int ly, int lw, int lh, int mx, int my, float dt, float alpha, boolean isClosing, float fastClose) {
        ensureState();
        clampScroll(lh);
        scrollOffset = HudAnimUtil.smoothHalfLife((float) scrollOffset, (float) targetScroll, 0.06f, dt);
        selectedCatSlide = HudAnimUtil.lerp(selectedCatSlide, selectedCategoryIndex, 0.2f, dt);
        int hlY = ly + screen.unit(10)
                + (int) (selectedCatSlide * screen.rows().categoryStride() - scrollOffset);

        g.enableScissor(lx, ly, lx + lw, ly + lh);
        HudDraw.fill(g, lx + screen.unit(4), hlY, lx + screen.unit(7), hlY + screen.rows().categoryHeight(), HudAnimUtil.withAlpha(selectedCategoryIndex == 0 ? screen.getShop().getThemeColor() : (screen.getShop().getCategories().isEmpty() ? screen.getShop().getThemeColor() : screen.getShop().getCategories().get(Math.max(0, selectedCategoryIndex - 1)).getThemeColor()), (int) (255 * alpha)));

        int cy = ly + screen.unit(10) - (int) scrollOffset;
        drawCatRow(g, lx, cy, lw, ly, lh, Component.translatable("arc_quest.trade.category.all").getString(), selectedCategoryIndex == 0, 0, mx, my, dt, alpha, isClosing, fastClose);
        cy += screen.rows().categoryStride();
        for (int i = 0; i < screen.getShop().getCategories().size(); i++) {
            if (cy + screen.rows().categoryHeight() >= ly && cy < ly + lh) {
                drawCatRow(g, lx, cy, lw, ly, lh, screen.getShop().getCategories().get(i).getDisplayName().getString(), selectedCategoryIndex == i + 1, i + 1, mx, my, dt, alpha, isClosing, fastClose);
            }
            cy += screen.rows().categoryStride();
        }
        g.disableScissor();

        int maxScroll = Math.max(0, contentHeight() - lh);
        if (maxScroll > 0) {
            int contentHeight = contentHeight();
            int thumbHeight = Math.max(screen.unit(16), (int) ((float) lh / contentHeight * lh));
            int thumbY = ly + (int) (scrollOffset / maxScroll * (lh - thumbHeight));
            HudDraw.fill(g, lx + lw - screen.unit(6), thumbY, lx + lw - screen.unit(4), thumbY + thumbHeight,
                    HudAnimUtil.withAlpha(0xFFFFFF, (int) (180 * alpha)));
        }
    }

    private void drawCatRow(GuiGraphics g, int x, int y, int w, int viewportY, int viewportHeight,
                            String text, boolean sel, int idx, int mx, int my, float dt,
                            float alpha, boolean isClosing, float fastClose) {
        boolean hov = !isClosing && dt > 0 && mx >= x && mx < x + w
                && my >= viewportY && my < viewportY + viewportHeight
                && my >= y && my < y + screen.rows().categoryHeight();
        HudCursorManager.requestPointer(hov && alpha > 0.05f);
        catHoverAnims[idx] = HudAnimUtil.step(catHoverAnims[idx], hov ? 1f : 0f, 8f, dt);
        float hEase = HudAnimUtil.easeOutCubic(catHoverAnims[idx]), contentScale = isClosing ? HudAnimUtil.easeInCubic(fastClose) : 1.0f;
        if (HudDraw.isStyled()) {
            int theme = idx == 0 ? screen.getShop().getThemeColor() : screen.getShop().getCategories().get(idx - 1).getThemeColor();
            HudStyleRenderer.card(g, x + screen.unit(8), y, w - 2 * screen.unit(8), screen.rows().categoryHeight(),
                    sel, hEase > .5f, alpha, theme);
        }
        int textX = x + screen.unit(16) + (sel ? screen.unit(6) : (int) (screen.unit(4) * hEase)), c = sel ? 0xFFFFFF : Math.round(150 + 105 * hEase);
        int updates = TradeUpdateHighlights.count(screen.getShopId(), idx == 0 ? null : screen.getShop().getCategories().get(idx - 1).getId());
        String badge = updates > 99 ? "99+" : Integer.toString(updates);
        int badgeWidth = updates == 0 ? 0 : (int) (font.width(badge) * screen.getTextScale()) + screen.unit(16);
        int maxTextWidth = Math.max(1, (int) ((w - (textX - x) - screen.unit(8) - badgeWidth) / screen.getTextScale()));
        if (font.width(text) > maxTextWidth) {
            text = font.plainSubstrByWidth(text, Math.max(0, maxTextWidth - font.width("..."))) + "...";
        }

        if (contentScale > 0.01f) {
            g.pose().pushPose();
            g.pose().translate(x + w / 2f, y + screen.unit(14), 0);
            g.pose().scale(contentScale, contentScale, 1f);
            g.pose().translate(-(x + w / 2f), -(y + screen.unit(14)), 0);
            drawScaledString(g, text, textX, y + (screen.rows().categoryHeight() - screen.rows().lineHeight()) / 2,
                    HudAnimUtil.withAlpha((c << 16) | (c << 8) | c, (int) (255 * alpha)), true);
            if (updates > 0) {
                int badgeX = x + w - badgeWidth;
                HudDraw.fill(g, badgeX - screen.unit(3), y + screen.unit(12), badgeX - 1, y + screen.unit(14), HudAnimUtil.withAlpha(0xB7CCD4, (int) (230 * alpha)));
                drawScaledString(g, badge, badgeX + screen.unit(4), y + (screen.rows().categoryHeight() - screen.rows().lineHeight()) / 2, HudAnimUtil.withAlpha(0xB7CCD4, (int) (230 * alpha)), false);
            }
            g.pose().popPose();
        }
    }

    public boolean mouseClicked(double mx, double my, int lx, int ly, int lw, int lh) {
        ensureState();
        if (mx < lx || mx >= lx + lw) return false;
        int row = TradeCategoryListLayout.rowAt(my, ly, lh, scrollOffset, rowCount(), screen.rows().categoryHeight(), screen.rows().categoryStride(), screen.unit(10));
        if (row < 0) return false;
        selectedCategoryIndex = row;
        screen.filterEntries();
        screen.playClick();
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double delta,
                                 int lx, int ly, int lw, int lh) {
        if (mx < lx || mx >= lx + lw || my < ly || my >= ly + lh) return false;
        targetScroll -= delta * screen.rows().categoryStride();
        clampScroll(lh);
        return true;
    }

    private void clampScroll(int viewportHeight) {
        int maxScroll = Math.max(0, contentHeight() - viewportHeight);
        targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    private void ensureState() {
        int rowCount = rowCount();
        selectedCategoryIndex = Math.max(0, Math.min(selectedCategoryIndex, rowCount - 1));
        if (catHoverAnims.length == rowCount) return;
        float[] resized = new float[rowCount];
        System.arraycopy(catHoverAnims, 0, resized, 0, Math.min(catHoverAnims.length, resized.length));
        catHoverAnims = resized;
    }

    private int contentHeight() {
        return TradeCategoryListLayout.contentHeight(rowCount(), screen.rows().categoryHeight(), screen.rows().categoryStride(), screen.unit(10));
    }

    private int rowCount() {
        return screen.getShop() == null ? 1 : screen.getShop().getCategories().size() + 1;
    }

    private void drawScaledString(GuiGraphics g, String text, int x, int y, int color, boolean shadow) {
        float scale = screen.getTextScale();
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        HudDraw.drawString(g, font, text, 0, 0, color, shadow);
        g.pose().popPose();
    }
}
