// file_name: JournalTabPanel.java
package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.QuestHudOverlay;
import org.arcadia.arc_quest.client.hud.quest.history.QuestHistoryPanel;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalTabStrip;
import org.arcadia.arc_quest.client.hud.quest.journal.history.QuestChangeNotificationManager;
import org.arcadia.arc_quest.config.ArcQuestConfig;

import java.util.ArrayList;
import java.util.List;

public class JournalTabPanel {

    private final QuestJournalScreen screen;
    private final JournalGuideMenu guideMenu;
    private final JournalTabStrip tabStrip = new JournalTabStrip();

    public JournalTabPanel(QuestJournalScreen screen) {
        this.screen = screen;
        guideMenu = new JournalGuideMenu(screen);
    }

    // 统一管理所有的 Tab 和外置按钮文本，未来加翻译也只需改这一个地方
    private String getTabLabel(String tabId) {
        return switch (tabId) {
            case "ACTIVE" -> Component.translatable("arc_quest.gui.journal.tab.active").getString();
            case "COMPLETED" -> Component.translatable("arc_quest.gui.journal.tab.completed").getString();
            case "FAILED" -> Component.translatable("arc_quest.gui.journal.tab.failed").getString();
            case "HISTORY" -> Component.translatable("arc_quest.gui.journal.tab.history").getString();
            case "GUIDE" -> Component.translatable("arc_quest.gui.journal.tab.guide").getString();
            default -> tabId;
        };
    }

    public void render(GuiGraphics g, int mx, int my, int safeAlpha, int tabBaseX, int rightEdgeX, int theme, float dt) {
        int tabY = 38;
        tabStrip.render(g, screen.getFont(), buildTabs(), tabBaseX, tabY,
                JournalConstants.TAB_HEIGHT, 4, mx, my, theme, safeAlpha, dt);
        guideMenu.render(g, mx, my, safeAlpha, rightEdgeX, tabY, theme, dt);
    }

    public boolean mouseClicked(double mx, double my, int tabBaseX, int rightEdgeX) {
        if (QuestHistoryPanel.isActive()) return true;
        int tabY = 38;
        String tabId = tabStrip.hitTest(screen.getFont(), buildTabs(), tabBaseX, tabY,
                JournalConstants.TAB_HEIGHT, 4, mx, my);
        if (tabId != null) {
            if ("HISTORY".equals(tabId)) {
                if (!screen.isShowingChangeLog()) {
                    screen.setShowingChangeLog(true);
                    screen.playClick();
                }
            } else {
                JournalTypes.Tab tab = JournalTypes.Tab.valueOf(tabId);
                if (screen.isShowingChangeLog() || screen.getCurrentTab() != tab) {
                    screen.setShowingChangeLog(false);
                    screen.setCurrentTab(tab);
                    screen.playClick();
                }
            }
            return true;
        }
        return guideMenu.mouseClicked(mx, my, rightEdgeX, tabY);
    }

    private List<JournalTabStrip.TabItem> buildTabs() {
        List<JournalTabStrip.TabItem> tabs = new ArrayList<>();
        for (JournalTypes.Tab tab : JournalTypes.Tab.values()) {
            tabs.add(new JournalTabStrip.TabItem(tab.name(), getTabLabel(tab.name()),
                    tab == screen.getCurrentTab() && !screen.isShowingChangeLog(), false));
        }
        if (ArcQuestConfig.isQuestHistoryTabEnabled()) {
            boolean unread = ArcQuestConfig.shouldShowQuestHistoryUnreadDots()
                    && QuestChangeNotificationManager.INSTANCE.hasUnreadOtherThan(
                    QuestHudOverlay.INSTANCE.getTrackedQuestId());
            tabs.add(new JournalTabStrip.TabItem("HISTORY", getTabLabel("HISTORY"),
                    screen.isShowingChangeLog(), unread));
        }
        return tabs;
    }
}
