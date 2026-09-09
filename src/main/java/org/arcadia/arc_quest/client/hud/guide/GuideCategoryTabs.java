package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.guide.api.GuideCategory;

import java.util.List;

public class GuideCategoryTabs {
    private final GuideListScreen screen;
    private float tabSlideAnim = 0f;
    private float tabWidthAnim = 0f;
    private double scrollOffset = 0;
    private double targetScroll = 0;

    public GuideCategoryTabs(GuideListScreen screen) {
        this.screen = screen;
    }

    public void render(GuiGraphics g, int mx, int my, int safeAlpha, int startX, int maxWidth, int theme, float dt) {
        int tabY = GuideConstants.TAB_TOP;
        List<GuideCategory> categories = screen.visibleCategories();
        if (categories.isEmpty()) return;

        // 计算总宽度
        int totalTabsWidth = 0;
        for (GuideCategory cat : categories) {
            totalTabsWidth += screen.getFont().width(cat.getDisplayName()) + 16 + 4;
        }
        totalTabsWidth -= 4;

        int maxScroll = Math.max(0, totalTabsWidth - maxWidth);
        targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));
        scrollOffset += (targetScroll - scrollOffset) * Math.min(1.0, dt * 15.0);

        float currentTabX = startX - (float) scrollOffset;
        float targetTabX = 0, targetTabW = 0;

        for (GuideCategory cat : categories) {
            int tw = screen.getFont().width(cat.getDisplayName()) + 16;
            if (cat.getId().equals(screen.getSelectedCategoryId())) {
                targetTabX = currentTabX;
                targetTabW = tw;
            }
            currentTabX += tw + 4;
        }

        if (tabWidthAnim <= 0.1f) {
            tabSlideAnim = targetTabX;
            tabWidthAnim = targetTabW;
        }
        float lerpFactor = Math.min(1.0f, dt * 15f);
        tabSlideAnim += (targetTabX - tabSlideAnim) * lerpFactor;
        tabWidthAnim += (targetTabW - tabWidthAnim) * lerpFactor;

        // 使用裁剪区防止超出边界
        screen.enableScissor(g, startX, tabY, startX + maxWidth, tabY + GuideConstants.TAB_HEIGHT);

        currentTabX = startX - (float) scrollOffset;
        for (GuideCategory cat : categories) {
            String label = cat.getDisplayName().getString();
            int tw = screen.getFont().width(label) + 16;
            boolean hovered = mx >= currentTabX && mx <= currentTabX + tw && my >= tabY && my <= tabY + GuideConstants.TAB_HEIGHT;
            HudCursorManager.requestPointer(hovered && safeAlpha > 8);
            boolean active = cat.getId().equals(screen.getSelectedCategoryId());
            int textColor = active ? HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha) : hovered ? HudAnimUtil.withAlpha(0xDDDDDD, safeAlpha) : HudAnimUtil.withAlpha(0x888888, safeAlpha);

            if (safeAlpha > 8) {
                g.drawString(screen.getFont(), label, (int) currentTabX + 8, tabY + (GuideConstants.TAB_HEIGHT - screen.getFont().lineHeight) / 2, textColor, true);
                if (screen.hasUnreadGuide(cat)) {
                    HudRenderUtil.drawBreathingRedDot(g, (int) currentTabX + tw - 4,
                            tabY + 4, safeAlpha / 255f);
                }
            }
            currentTabX += tw + 4;
        }

        if (safeAlpha > 8 && tabWidthAnim > 0) {
            g.fill((int) tabSlideAnim, tabY + GuideConstants.TAB_HEIGHT - 2, (int) (tabSlideAnim + tabWidthAnim), tabY + GuideConstants.TAB_HEIGHT, HudAnimUtil.withAlpha(theme, safeAlpha));
        }

        g.disableScissor();

        // 渲染滚动提示箭头
        if (maxScroll > 0) {
            if (scrollOffset > 1) {
                g.drawString(screen.getFont(), "<", startX - 10, tabY + 6, HudAnimUtil.withAlpha(0x666666, safeAlpha), false);
            }
            if (scrollOffset < maxScroll - 1) {
                g.drawString(screen.getFont(), ">", startX + maxWidth + 4, tabY + 6, HudAnimUtil.withAlpha(0x666666, safeAlpha), false);
            }
        }
    }

    public boolean mouseClicked(double mx, double my, int startX, int maxWidth) {
        int tabY = GuideConstants.TAB_TOP;
        if (my >= tabY && my <= tabY + GuideConstants.TAB_HEIGHT && mx >= startX && mx <= startX + maxWidth) {
            float currentTabX = startX - (float) scrollOffset;
            for (GuideCategory cat : screen.visibleCategories()) {
                int tw = screen.getFont().width(cat.getDisplayName()) + 16;
                if (mx >= currentTabX && mx <= currentTabX + tw) {
                    screen.selectCategory(cat.getId());
                    return true;
                }
                currentTabX += tw + 4;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double delta, int startX, int maxWidth) {
        int tabY = GuideConstants.TAB_TOP;
        if (my >= tabY && my <= tabY + GuideConstants.TAB_HEIGHT && mx >= startX && mx <= startX + maxWidth) {
            targetScroll -= delta * 40.0;
            return true;
        }
        return false;
    }
}
