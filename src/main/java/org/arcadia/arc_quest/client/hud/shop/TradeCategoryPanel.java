package org.arcadia.arc_quest.client.hud.shop;

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

    public void render(GuiGraphics g, int lx, int ly, int lw, int lh, int mx, int my, float dt, float alpha, boolean isClosing, float fastClose) {
        ensureState();
        clampScroll(lh);
        scrollOffset = HudAnimUtil.smoothHalfLife((float) scrollOffset, (float) targetScroll, 0.06f, dt);
        selectedCatSlide = HudAnimUtil.lerp(selectedCatSlide, selectedCategoryIndex, 0.2f, dt);
        int hlY = ly + TradeCategoryListLayout.TOP_PADDING
                + (int) (selectedCatSlide * TradeCategoryListLayout.ROW_STRIDE - scrollOffset);

        g.enableScissor(lx, ly, lx + lw, ly + lh);
        g.fill(lx + 4, hlY, lx + 7, hlY + 28, HudAnimUtil.withAlpha(selectedCategoryIndex == 0 ? screen.getShop().getThemeColor() : (screen.getShop().getCategories().isEmpty() ? screen.getShop().getThemeColor() : screen.getShop().getCategories().get(Math.max(0, selectedCategoryIndex - 1)).getThemeColor()), (int) (255 * alpha)));

        int cy = ly + TradeCategoryListLayout.TOP_PADDING - (int) scrollOffset;
        drawCatRow(g, lx, cy, lw, ly, lh, Component.translatable("arc_quest.trade.category.all").getString(), selectedCategoryIndex == 0, 0, mx, my, dt, alpha, isClosing, fastClose);
        cy += TradeCategoryListLayout.ROW_STRIDE;
        for (int i = 0; i < screen.getShop().getCategories().size(); i++) {
            if (cy + TradeCategoryListLayout.ROW_HEIGHT >= ly && cy < ly + lh) {
                drawCatRow(g, lx, cy, lw, ly, lh, screen.getShop().getCategories().get(i).getDisplayName().getString(), selectedCategoryIndex == i + 1, i + 1, mx, my, dt, alpha, isClosing, fastClose);
            }
            cy += TradeCategoryListLayout.ROW_STRIDE;
        }
        g.disableScissor();

        int maxScroll = TradeCategoryListLayout.maxScroll(rowCount(), lh);
        if (maxScroll > 0) {
            int contentHeight = TradeCategoryListLayout.contentHeight(rowCount());
            int thumbHeight = Math.max(16, (int) ((float) lh / contentHeight * lh));
            int thumbY = ly + (int) (scrollOffset / maxScroll * (lh - thumbHeight));
            g.fill(lx + lw - 6, thumbY, lx + lw - 4, thumbY + thumbHeight,
                    HudAnimUtil.withAlpha(0xFFFFFF, (int) (180 * alpha)));
        }
    }

    private void drawCatRow(GuiGraphics g, int x, int y, int w, int viewportY, int viewportHeight,
                            String text, boolean sel, int idx, int mx, int my, float dt,
                            float alpha, boolean isClosing, float fastClose) {
        boolean hov = !isClosing && dt > 0 && mx >= x && mx < x + w
                && my >= viewportY && my < viewportY + viewportHeight
                && my >= y && my < y + TradeCategoryListLayout.ROW_HEIGHT;
        HudCursorManager.requestPointer(hov && alpha > 0.05f);
        catHoverAnims[idx] = HudAnimUtil.step(catHoverAnims[idx], hov ? 1f : 0f, 8f, dt);
        float hEase = HudAnimUtil.easeOutCubic(catHoverAnims[idx]), contentScale = isClosing ? HudAnimUtil.easeInCubic(fastClose) : 1.0f;
        int textX = x + 16 + (sel ? 6 : (int) (4 * hEase)), c = sel ? 0xFFFFFF : Math.round(150 + 105 * hEase);
        int maxTextWidth = Math.max(1, (int) ((w - (textX - x) - 8) / screen.getTextScale()));
        if (font.width(text) > maxTextWidth) {
            text = font.plainSubstrByWidth(text, Math.max(0, maxTextWidth - font.width("..."))) + "...";
        }

        if (contentScale > 0.01f) {
            g.pose().pushPose();
            g.pose().translate(x + w / 2f, y + 14, 0);
            g.pose().scale(contentScale, contentScale, 1f);
            g.pose().translate(-(x + w / 2f), -(y + 14), 0);
            drawScaledString(g, text, textX, y + 10,
                    HudAnimUtil.withAlpha((c << 16) | (c << 8) | c, (int) (255 * alpha)), true);
            g.pose().popPose();
        }
    }

    public boolean mouseClicked(double mx, double my, int lx, int ly, int lw, int lh) {
        ensureState();
        if (mx < lx || mx >= lx + lw) return false;
        int row = TradeCategoryListLayout.rowAt(my, ly, lh, scrollOffset, rowCount());
        if (row < 0) return false;
        selectedCategoryIndex = row;
        screen.filterEntries();
        screen.playClick();
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double delta,
                                 int lx, int ly, int lw, int lh) {
        if (mx < lx || mx >= lx + lw || my < ly || my >= ly + lh) return false;
        targetScroll -= delta * TradeCategoryListLayout.ROW_STRIDE;
        clampScroll(lh);
        return true;
    }

    private void clampScroll(int viewportHeight) {
        int maxScroll = TradeCategoryListLayout.maxScroll(rowCount(), viewportHeight);
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

    private int rowCount() {
        return screen.getShop() == null ? 1 : screen.getShop().getCategories().size() + 1;
    }

    private void drawScaledString(GuiGraphics g, String text, int x, int y, int color, boolean shadow) {
        float scale = screen.getTextScale();
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, color, shadow);
        g.pose().popPose();
    }
}
