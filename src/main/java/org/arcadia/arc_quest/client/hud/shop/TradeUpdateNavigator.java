package org.arcadia.arc_quest.client.hud.shop;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.trade.api.TradeEntry;

import java.util.List;
import java.util.stream.IntStream;

/** 提示区位于列表裁剪范围之外，点击只导航，不参与商品悬停和购买判定。 */
final class TradeUpdateNavigator {
    static final int INSET = 18;
    private final String shopId;
    private final Font font;
    private List<TradeEntry> indexedEntries;
    private long indexedRevision = -1;
    private int[] indices = new int[0];
    private int lastAbove = -1, lastBelow = -1;
    private String aboveText = "", belowText = "";

    TradeUpdateNavigator(String shopId, Font font) {
        this.shopId = shopId;
        this.font = font;
    }

    private void update(List<TradeEntry> entries) {
        long revision = TradeUpdateHighlights.revision();
        if (indexedEntries == entries && indexedRevision == revision) return;
        indexedEntries = entries;
        indexedRevision = revision;
        indices = IntStream.range(0, entries.size())
                .filter(index -> TradeUpdateHighlights.hasUpdate(shopId, entries.get(index).getEntryId())).toArray();
    }

    private TradeUpdateScrollIndex.Outside outside(List<TradeEntry> entries, double scroll, int height) {
        update(entries);
        return TradeUpdateScrollIndex.outside(indices, scroll, height,
                TradeListPanel.CARD_HEIGHT + 8, TradeListPanel.CARD_HEIGHT, 8);
    }

    void render(GuiGraphics graphics, List<TradeEntry> entries, double scroll,
                int x, int y, int width, int height, int mx, int my, float alpha, boolean interactive) {
        var outside = outside(entries, scroll, height);
        if (lastAbove != outside.aboveCount()) {
            lastAbove = outside.aboveCount();
            aboveText = Component.translatable("arc_quest.trade.update.above", lastAbove).getString();
        }
        if (lastBelow != outside.belowCount()) {
            lastBelow = outside.belowCount();
            belowText = Component.translatable("arc_quest.trade.update.below", lastBelow).getString();
        }
        if (lastAbove > 0) draw(graphics, aboveText, x, y - INSET, width, mx, my, alpha, interactive);
        if (lastBelow > 0) draw(graphics, belowText, x, y + height, width, mx, my, alpha, interactive);
    }

    int clicked(List<TradeEntry> entries, double scroll, int x, int y, int width, int height, double mx, double my) {
        var outside = outside(entries, scroll, height);
        if (outside.aboveCount() > 0 && hit(x, y - INSET, width, mx, my)) return outside.nearestAbove();
        if (outside.belowCount() > 0 && hit(x, y + height, width, mx, my)) return outside.nearestBelow();
        return -1;
    }

    private void draw(GuiGraphics graphics, String text, int x, int y, int width,
                      int mx, int my, float alpha, boolean interactive) {
        boolean hovered = interactive && hit(x, y, width, mx, my);
        HudCursorManager.requestPointer(hovered);
        int left = x + 8, right = x + width - 10;
        if (hovered) graphics.fill(left, y + 1, right, y + INSET - 1,
                HudAnimUtil.withAlpha(0xB7CCD4, (int) (16 * alpha)));
        float scale = Math.min(1f, Math.max(0.1f, (right - left - 8f) / Math.max(1, font.width(text))));
        graphics.pose().pushPose();
        graphics.pose().translate((left + right) / 2f, y + (INSET - font.lineHeight * scale) / 2f, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, text, -font.width(text) / 2, 0,
                HudAnimUtil.withAlpha(hovered ? 0xFFFFFF : 0xB7CCD4, (int) (230 * alpha)), false);
        graphics.pose().popPose();
    }

    private static boolean hit(int x, int y, int width, double mx, double my) {
        return mx >= x + 8 && mx < x + width - 10 && my >= y + 1 && my < y + INSET - 1;
    }
}
