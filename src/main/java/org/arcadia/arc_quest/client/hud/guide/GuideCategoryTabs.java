package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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

        g.fill(r[0] + 4, ty, r[0] + r[2] - 4, ty + th, withAlpha(0x0A0F1D, (int) (alpha * 0.85F)));
        g.fill(r[0] + 4, ty + th - 1, r[0] + r[2] - 4, ty + th, withAlpha(0x1E293B, (int) (alpha * 0.5F)));

        List<GuideCategory> cats = screen.visibleCategories();
        if (cats.isEmpty()) return;

        int tabAreaW = r[2] - 56;
        int totalTabsW = 0;
        for (GuideCategory cat : cats) totalTabsW += Math.max(54, Minecraft.getInstance().font.width(cat.getDisplayName()) + 16) + 4;
        int maxScroll = Math.max(0, totalTabsW - tabAreaW);

        float scroll = screen.getTabScrollOffset();
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        boolean canScrollLeft = scroll > 0;
        boolean canScrollRight = scroll < maxScroll;
        boolean leftHover = hit(mouseX, mouseY, r[0] + 8, ty + 3, 16, 16);
        GuideNavigationControls.drawArrowButton(g, r[0] + 8, ty + 3, 16, true, canScrollLeft, leftHover && canScrollLeft, screen.getThemeColor(), alpha);

        int startX = r[0] + 28 - (int) scroll;
        int x = startX;
        int clipRight = r[0] + r[2] - 28;
        int targetIndicatorX = -1;
        int targetIndicatorW = 0;

        for (GuideCategory cat : cats) {
            int tw = Math.max(54, Minecraft.getInstance().font.width(cat.getDisplayName()) + 16);
            boolean selected = cat.getId().equals(screen.getSelectedCategoryId());
            boolean hovered = hit(mouseX, mouseY, x, ty + 3, tw, 16);
            boolean inClip = x + tw > r[0] + 26 && x < clipRight;

            if (selected) {
                targetIndicatorX = x;
                targetIndicatorW = tw;
            }

            if (hovered && !selected && inClip) {
                g.fill(x, ty + 3, x + tw, ty + 17, withAlpha(screen.getThemeColor(), (int) (alpha * 0.1F)));
            }

            if (inClip) {
                GuideNavigationControls.drawScaledText(screen, g, x + 8, ty + 7, GuideConstants.CAPTION_SCALE,
                        cat.getDisplayName().getString(),
                        withAlpha(selected ? GuideConstants.TEXT : GuideConstants.SUB, alpha));
            }
            x += tw + 4;
        }

        if (targetIndicatorX != -1) {
            screen.updateTabIndicator(targetIndicatorX, targetIndicatorW);
            int animX = (int) screen.getTabAnimX();
            int animW = (int) screen.getTabAnimW();
            g.fill(animX, ty + 18, animX + animW, ty + 20, withAlpha(screen.getThemeColor(), alpha));
            g.fill(animX, ty + 17, animX + animW, ty + 18, withAlpha(screen.getThemeColor(), (int) (alpha * 0.4F)));
        }

        boolean rightHover = hit(mouseX, mouseY, r[0] + r[2] - 24, ty + 3, 16, 16);
        GuideNavigationControls.drawArrowButton(g, r[0] + r[2] - 24, ty + 3, 16, false, canScrollRight, rightHover && canScrollRight, screen.getThemeColor(), alpha);
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        int[] r = screen.tabsRect();
        int ty = r[1] + 4;
        List<GuideCategory> cats = screen.visibleCategories();
        if (cats.isEmpty()) return false;

        float scroll = screen.getTabScrollOffset();
        int startX = r[0] + 28 - (int) scroll;

        if (hit(mouseX, mouseY, r[0] + 8, ty + 3, 16, 16)) {
            screen.selectPrevCategory();
            return true;
        }
        if (hit(mouseX, mouseY, r[0] + r[2] - 24, ty + 3, 16, 16)) {
            screen.selectNextCategory();
            return true;
        }

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

    private int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }
}
