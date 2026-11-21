package org.arcadia.arc_quest.client.hud.quest.journal.component;

import org.arcadia.arc_quest.client.hud.style.HudStyleRenderer;
import org.arcadia.arc_quest.client.hud.style.HudDraw;
import org.arcadia.arc_quest.client.hud.style.OreUiRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.client.hud.component.HudRect;

import java.util.ArrayList;
import java.util.List;

public final class JournalTabStrip {
    private float indicatorX;
    private float indicatorWidth;

    public void render(GuiGraphics graphics, Font font, List<TabItem> items,
                       int x, int y, int height, int gap, int mouseX, int mouseY,
                       int themeColor, int alpha, float deltaTime) {
        TabLayout layout = layout(font, items, x, y, height, gap);
        HudRect activeBounds = layout.activeBounds();
        if (activeBounds != null) {
            if (indicatorWidth <= 0.1f) {
                indicatorX = activeBounds.x();
                indicatorWidth = activeBounds.width();
            }
            float factor = Math.min(1f, deltaTime * 15f);
            indicatorX += (activeBounds.x() - indicatorX) * factor;
            indicatorWidth += (activeBounds.width() - indicatorWidth) * factor;
        }

        for (TabBounds tabBounds : layout.tabs()) {
            HudRect bounds = tabBounds.bounds();
            boolean hovered = bounds.contains(mouseX, mouseY);
            HudCursorManager.requestPointer(hovered && alpha > 8);
            TabItem item = tabBounds.item();
            if (HudDraw.isStyled()) drawTabFace(graphics, bounds, item.active(), hovered, alpha, themeColor);
            int textColor = item.active()
                    ? HudAnimUtil.withAlpha(0xFFFFFF, alpha)
                    : hovered ? HudAnimUtil.withAlpha(0xDDDDDD, alpha)
                    : HudAnimUtil.withAlpha(0x888888, alpha);
            if (alpha > 8) {
                HudDraw.drawString(graphics, font, item.label(), bounds.x() + 8,
                        contentTop(bounds, font.lineHeight, true), textColor, true);
                if (item.unread()) {
                    JournalUnreadBadgeRenderer.draw(graphics, bounds.right() - 4, bounds.y() + 4, alpha);
                }
            }
        }
        if (!HudDraw.isStyled() && alpha > 8 && indicatorWidth > 0f) {
            HudDraw.fill(graphics, (int) indicatorX, y + height - 2,
                    (int) (indicatorX + indicatorWidth), y + height,
                    HudAnimUtil.withAlpha(themeColor, alpha));
        }
    }

    /** Shared by tabs and the journal actions attached to the active tab. */
    public static void drawTabFace(GuiGraphics g, HudRect bounds, boolean active, boolean hovered, int alpha, int theme) {
        if (HudDraw.isOre()) {
            OreUiRenderer.journalControl(g, bounds.x(), bounds.y(), bounds.width(), bounds.height(), active, hovered, alpha / 255f, theme);
        } else if (HudDraw.isStyled()) {
            HudStyleRenderer.card(g, bounds.x(), bounds.y(), bounds.width(), bounds.height(), active, hovered, alpha / 255f, theme);
        } else if (active) {
            HudDraw.fill(g, bounds.x(), bounds.bottom() - 2, bounds.right(), bounds.bottom(), HudAnimUtil.withAlpha(theme, alpha));
        }
    }

    public static int contentTop(HudRect bounds, int height, boolean shadow) {
        if (!HudDraw.isOre()) return bounds.y() + (bounds.height() - height) / 2;
        int top = OreUiRenderer.cardTextTop(bounds.height());
        int bottom = OreUiRenderer.cardTextBottom(bounds.height());
        return bounds.y() + top + Math.max(0, (bottom - top - height - (shadow ? 1 : 0)) / 2);
    }

    public String hitTest(Font font, List<TabItem> items, int x, int y,
                          int height, int gap, double mouseX, double mouseY) {
        for (TabBounds tabBounds : layout(font, items, x, y, height, gap).tabs()) {
            if (tabBounds.bounds().contains(mouseX, mouseY)) return tabBounds.item().id();
        }
        return null;
    }

    public int totalWidth(Font font, List<TabItem> items, int gap) {
        int width = 0;
        for (int index = 0; index < items.size(); index++) {
            if (index > 0) width += gap;
            width += font.width(items.get(index).label()) + 16;
        }
        return width;
    }

    private TabLayout layout(Font font, List<TabItem> items, int x, int y, int height, int gap) {
        ArrayList<TabBounds> tabs = new ArrayList<>(items.size());
        HudRect activeBounds = null;
        int currentX = x;
        for (TabItem item : items) {
            int width = font.width(item.label()) + 16;
            HudRect bounds = new HudRect(currentX, y, width, height);
            tabs.add(new TabBounds(item, bounds));
            if (item.active()) activeBounds = bounds;
            currentX += width + gap;
        }
        return new TabLayout(List.copyOf(tabs), activeBounds);
    }

    public record TabItem(String id, String label, boolean active, boolean unread) {
    }

    private record TabBounds(TabItem item, HudRect bounds) {
    }

    private record TabLayout(List<TabBounds> tabs, HudRect activeBounds) {
    }
}
