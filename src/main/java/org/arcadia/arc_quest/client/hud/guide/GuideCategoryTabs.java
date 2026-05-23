package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.guide.api.GuideCategory;
import java.util.List;

final class GuideCategoryTabs {
    private final GuideListScreen screen;

    GuideCategoryTabs(GuideListScreen screen) {
        this.screen = screen;
    }

    void render(GuiGraphics g, int mouseX, int mouseY, int alpha) {
        int[] r = screen.tabsRect();
        int ty = r[1] + 4;
        int th = GuideConstants.TAB_HEIGHT;
        float effectiveAlpha = alpha / 255f;

        g.fill(r[0] + 4, ty, r[0] + r[2] - 4, ty + th, HudAnimUtil.withAlpha(0x000000, (int) (alpha * 0.4F)));
        g.fill(r[0] + 4, ty + th - 1, r[0] + r[2] - 4, ty + th, HudAnimUtil.withAlpha(screen.getThemeColor(), (int) (alpha * 0.3F)));

        List<GuideCategory> cats = screen.visibleCategories();
        if (cats.isEmpty()) return;

        int tabAreaW = r[2] - 56;
        int totalTabsW = 0;
        for (GuideCategory cat : cats) totalTabsW += Math.max(54, Minecraft.getInstance().font.width(cat.getDisplayName()) + 16) + 4;
        int maxScroll = Math.max(0, totalTabsW - tabAreaW);

        float scroll = screen.getTabScrollOffset();
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        int startX = r[0] + 28 - (int) scroll;
        int x = startX;
        int clipRight = r[0] + r[2] - 28;

        for (GuideCategory cat : cats) {
            int tw = Math.max(54, Minecraft.getInstance().font.width(cat.getDisplayName()) + 16);
            boolean selected = cat.getId().equals(screen.getSelectedCategoryId());
            boolean hovered = hit(mouseX, mouseY, x, ty + 3, tw, 16);
            boolean inClip = x + tw > r[0] + 26 && x < clipRight;

            if (hovered && !selected && inClip) {
                g.fill(x, ty + 3, x + tw, ty + 17, HudAnimUtil.withAlpha(screen.getThemeColor(), (int) (alpha * 0.1F)));
            }

            if (inClip) {
                int color = selected ? HudAnimUtil.withAlpha(0xFFFFFF, alpha) : HudAnimUtil.withAlpha(0x888888, alpha);
                g.drawString(Minecraft.getInstance().font, cat.getDisplayName(), x + 8, ty + 7, color, false);
            }
            x += tw + 4;
        }

        int selectedX = 0;
        int selectedW = 54;
        x = startX;
        for (GuideCategory cat : cats) {
            int tw = Math.max(54, Minecraft.getInstance().font.width(cat.getDisplayName()) + 16);
            if (cat.getId().equals(screen.getSelectedCategoryId())) {
                selectedX = x;
                selectedW = tw;
                break;
            }
            x += tw + 4;
        }

        if (selectedX != 0) {
            screen.updateTabIndicator(selectedX, selectedW);
            int animX = (int) screen.getTabAnimX();
            int animW = (int) screen.getTabAnimW();
            g.fill(animX, ty + 18, animX + animW, ty + 20, HudAnimUtil.withAlpha(screen.getThemeColor(), alpha));
        }
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        int[] r = screen.tabsRect();
        int ty = r[1] + 4;
        List<GuideCategory> cats = screen.visibleCategories();
        if (cats.isEmpty()) return false;

        float scroll = screen.getTabScrollOffset();
        int startX = r[0] + 28 - (int) scroll;

        int x = startX;
        for (GuideCategory cat : cats) {
            int tw = Math.max(54, Minecraft.getInstance().font.width(cat.getDisplayName()) + 16);
            if (hit(mouseX, mouseY, x, ty + 3, tw, 16)) {
                screen.selectCategory(cat.getId());
                return true;
            }
            x += tw + 4;
        }
        return false;
    }

    private boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
