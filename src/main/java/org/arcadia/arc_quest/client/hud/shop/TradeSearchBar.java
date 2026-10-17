package org.arcadia.arc_quest.client.hud.shop;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.FadingSearchBox;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.client.hud.component.HudRect;

final class TradeSearchBar {
    static final int HEIGHT = 20;
    private final AbstractTradeScreen screen;
    private FadingSearchBox input;
    private String query = "";

    TradeSearchBar(AbstractTradeScreen screen) { this.screen = screen; }

    FadingSearchBox init(Font font) {
        boolean focused = focused();
        input = new FadingSearchBox(font, 0, 0, 80, 14, text("hint"));
        input.setMaxLength(80);
        input.setHint(text("hint"));
        input.setVisible(screen.searchBounds() != null);
        input.setValue(query);
        input.setResponder(value -> {
            query = value;
            screen.search.setQuery(value);
            screen.onSearchChanged();
        });
        input.setFocused(focused);
        return input;
    }

    String query() { return query; }
    void query(String value) { input.setValue(value); }
    boolean focused() { return input != null && input.isFocused(); }
    void focus(boolean focused) {
        input.setFocused(focused);
        screen.setFocused(focused ? input : null);
    }
    boolean keyPressed(int key, int scan, int modifiers) { return input.keyPressed(key, scan, modifiers); }
    boolean charTyped(char character, int modifiers) { return input.charTyped(character, modifiers); }
    void tick() { if (input != null) input.tick(); }

    void render(GuiGraphics g, int mx, int my, float partialTick) {
        HudRect box = screen.searchBounds();
        if (input == null) return;
        input.setVisible(box != null);
        if (box == null) return;
        Font font = screen.getFont();
        int theme = screen.getShop().getThemeColor();
        float alpha = screen.getEffectiveAlpha();
        int a = (int) (255 * alpha);
        if (a <= 3) return;
        g.fill(box.x(), box.y(), box.right(), box.bottom(), HudAnimUtil.withAlpha(0x05050A, (int) (150 * alpha)));
        HudAnimUtil.drawFrame(g, box.x(), box.y(), box.width(), box.height(), 1,
                HudAnimUtil.withAlpha(focused() ? theme : 0x888899, (int) (180 * alpha)));
        input.setX(box.x() + 6);
        input.setY(box.y() + (box.height() - font.lineHeight) / 2);
        input.setWidth(Math.max(1, box.width() - 28));
        input.setAlpha(alpha);
        input.setTextColor(0xE0E0E0);
        input.setCursorColor(theme);
        input.render(g, mx, my, partialTick);
        if (!query.isEmpty()) {
            boolean hover = mx >= box.right() - 20 && box.contains(mx, my);
            HudCursorManager.requestPointer(hover && screen.canSearch());
            g.drawCenteredString(font, "×", box.right() - 10, box.y() + (box.height() - font.lineHeight) / 2,
                    HudAnimUtil.withAlpha(hover ? theme : 0xBBBBBB, a));
            if (hover) g.renderTooltip(font, text("clear"), mx, my);
        }
    }

    boolean mouseClicked(double mx, double my, int button) {
        if (input == null || !screen.canSearch()) return false;
        HudRect box = screen.searchBounds();
        if (box == null || !box.contains(mx, my)) {
            focus(false);
            return false;
        }
        if (button != 0 && button != 1) return false;
        focus(true);
        if (button == 1 || mx >= box.right() - 20) input.setValue("");
        else input.mouseClicked(mx, my, button);
        return true;
    }

    private static Component text(String key) { return Component.translatable("arc_quest.gui.trade.search." + key); }
}
