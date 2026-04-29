package org.arcadia.arc_quest.client.hud.shop;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;

public class TradeCategoryPanel {
    private final TradeScreen screen;
    private final Font font;
    private int selectedCategoryIndex = 0;
    private float selectedCatSlide = 0f;
    private float[] catHoverAnims;

    public TradeCategoryPanel(TradeScreen screen, Font font) {
        this.screen = screen; this.font = font;
        resetAnims();
    }

    public void resetAnims() { this.catHoverAnims = new float[screen.getShop() != null ? screen.getShop().getCategories().size() + 1 : 1]; }
    public int getSelectedIndex() { return selectedCategoryIndex; }

    public void render(GuiGraphics g, int lx, int ly, int lw, int lh, int mx, int my, float dt, float alpha, boolean isClosing, float fastClose) {
        selectedCatSlide = HudAnimUtil.lerp(selectedCatSlide, selectedCategoryIndex, 0.2f, dt);
        int hlY = ly + 10 + (int)(selectedCatSlide * 36);
        g.fill(lx + 4, hlY, lx + 7, hlY + 28, HudAnimUtil.withAlpha(selectedCategoryIndex == 0 ? screen.getShop().getThemeColor() : (screen.getShop().getCategories().isEmpty() ? screen.getShop().getThemeColor() : screen.getShop().getCategories().get(Math.max(0, selectedCategoryIndex-1)).getThemeColor()), (int)(255 * alpha)));

        int cy = ly + 10;
        drawCatRow(g, lx, cy, lw, Component.translatable("arc_quest.trade.category.all").getString(), selectedCategoryIndex == 0, 0, mx, my, dt, alpha, isClosing, fastClose);
        cy += 36;
        for (int i = 0; i < screen.getShop().getCategories().size(); i++) {
            drawCatRow(g, lx, cy, lw, screen.getShop().getCategories().get(i).getDisplayName().getString(), selectedCategoryIndex == i + 1, i + 1, mx, my, dt, alpha, isClosing, fastClose);
            cy += 36;
        }
    }

    private void drawCatRow(GuiGraphics g, int x, int y, int w, String text, boolean sel, int idx, int mx, int my, float dt, float alpha, boolean isClosing, float fastClose) {
        boolean hov = !isClosing && dt > 0 && mx >= x && mx < x + w && my >= y && my < y + 28;
        catHoverAnims[idx] = HudAnimUtil.step(catHoverAnims[idx], hov ? 1f : 0f, 8f, dt);
        float hEase = HudAnimUtil.easeOutCubic(catHoverAnims[idx]), contentScale = isClosing ? HudAnimUtil.easeInCubic(fastClose) : 1.0f;
        int textX = x + 16 + (sel ? 6 : (int)(4 * hEase)), c = sel ? 0xFFFFFF : Math.round(150 + 105 * hEase);

        if (contentScale > 0.01f) {
            g.pose().pushPose(); g.pose().translate(x + w/2f, y + 14, 0); g.pose().scale(contentScale, contentScale, 1f); g.pose().translate(-(x + w/2f), -(y + 14), 0);
            g.drawString(font, text, textX, y + 10, HudAnimUtil.withAlpha((c<<16)|(c<<8)|c, (int)(255*alpha)), true); g.pose().popPose();
        }
    }

    public boolean mouseClicked(double mx, double my, int lx, int ly, int lw, int lh) {
        if (mx >= lx && mx < lx + lw) {
            int cy = ly + 10;
            if (my >= cy && my < cy + 28) { selectedCategoryIndex = 0; screen.filterEntries(); screen.playClick(); return true; }
            cy += 36;
            for (int i = 0; i < screen.getShop().getCategories().size(); i++) {
                if (my >= cy && my < cy + 28) { selectedCategoryIndex = i + 1; screen.filterEntries(); screen.playClick(); return true; }
                cy += 36;
            }
        }
        return false;
    }
}