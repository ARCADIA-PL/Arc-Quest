package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.mutil.animation.ArcAnimClock;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;

public class JournalTabPanel {
    private final QuestJournalScreen screen;
    private float tabSlideAnim = 0f;
    private float tabWidthAnim = 0f;

    public JournalTabPanel(QuestJournalScreen screen) {
        this.screen = screen;
    }

    private String getTabLabel(JournalTypes.Tab tab) {
        return Component.translatable(switch (tab) {
            case ACTIVE -> "arc_quest.gui.journal.tab.active";
            case COMPLETED -> "arc_quest.gui.journal.tab.completed";
            case FAILED -> "arc_quest.gui.journal.tab.failed";
        }).getString();
    }

    public void render(GuiGraphics g, int mx, int my, int safeAlpha, float slide, int theme, float dt) {
        int tabY = 38, tabBaseX = JournalConstants.LIST_MARGIN - (int) slide;
        float targetTabX = 0, currentTabX = tabBaseX, targetTabW = 0;

        for (JournalTypes.Tab tab : JournalTypes.Tab.values()) {
            int tw = screen.getFont().width(getTabLabel(tab)) + 16;
            if (tab == screen.getCurrentTab()) {
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

        currentTabX = tabBaseX;
        for (JournalTypes.Tab tab : JournalTypes.Tab.values()) {
            String label = getTabLabel(tab);
            int tw = screen.getFont().width(label) + 16;
            boolean hovered = mx >= currentTabX && mx <= currentTabX + tw && my >= tabY && my <= tabY + JournalConstants.TAB_HEIGHT;
            int textColor = (tab == screen.getCurrentTab())
                    ? ArcDrawUtil.withAlpha(0xFFFFFF, safeAlpha)
                    : hovered ? ArcDrawUtil.withAlpha(0xDDDDDD, safeAlpha) : ArcDrawUtil.withAlpha(0x888888, safeAlpha);

            if (safeAlpha > 8) {
                g.drawString(screen.getFont(), label, (int) currentTabX + 8, tabY + (JournalConstants.TAB_HEIGHT - screen.getFont().lineHeight) / 2, textColor, true);
            }
            currentTabX += tw + 4;
        }

        if ((int) (255 * screen.getEffectiveAlpha()) > 8) {
            g.fill((int) tabSlideAnim, tabY + JournalConstants.TAB_HEIGHT - 2, (int) (tabSlideAnim + tabWidthAnim), tabY + JournalConstants.TAB_HEIGHT, ArcDrawUtil.withAlpha(theme, (int) (255 * screen.getEffectiveAlpha())));
        }
    }

    public boolean mouseClicked(double mx, double my, int tabBaseX) {
        int tabY = 38;
        for (JournalTypes.Tab tab : JournalTypes.Tab.values()) {
            int tw = screen.getFont().width(getTabLabel(tab)) + 16;
            if (mx >= tabBaseX && mx <= tabBaseX + tw && my >= tabY && my <= tabY + JournalConstants.TAB_HEIGHT) {
                if (screen.getCurrentTab() != tab) {
                    screen.setCurrentTab(tab);
                    screen.playClick();
                }
                return true;
            }
            tabBaseX += tw + 4;
        }
        return false;
    }
}