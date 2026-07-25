// file_name: JournalTabPanel.java
package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.QuestHudOverlay;
import org.arcadia.arc_quest.client.hud.guide.GuideListScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.history.QuestChangeNotificationManager;
import org.arcadia.arc_quest.config.ArcQuestConfig;

public class JournalTabPanel {

    private final QuestJournalScreen screen;
    private float tabSlideAnim = 0f;
    private float tabWidthAnim = 0f;

    public JournalTabPanel(QuestJournalScreen screen) {
        this.screen = screen;
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
        float targetTabX = 0, currentTabX = tabBaseX, targetTabW = 0;

        // 1. 计算基础 Tabs 宽度和目标位置
        for (JournalTypes.Tab tab : JournalTypes.Tab.values()) {
            int tw = screen.getFont().width(getTabLabel(tab.name())) + 16;
            if (tab == screen.getCurrentTab() && !screen.isShowingChangeLog()) {
                targetTabX = currentTabX;
                targetTabW = tw;
            }
            currentTabX += tw + 4;
        }

        // 2. 计算 HISTORY Tab 宽度和目标位置
        String labelHistory = getTabLabel("HISTORY");
        int logTw = screen.getFont().width(labelHistory) + 16;
        if (screen.isShowingChangeLog()) {
            targetTabX = currentTabX;
            targetTabW = logTw;
        }

        // 滑块动画插值
        if (tabWidthAnim <= 0.1f) {
            tabSlideAnim = targetTabX;
            tabWidthAnim = targetTabW;
        }
        float lerpFactor = Math.min(1.0f, dt * 15f);
        tabSlideAnim += (targetTabX - tabSlideAnim) * lerpFactor;
        tabWidthAnim += (targetTabW - tabWidthAnim) * lerpFactor;

        // 3. 渲染基础 Tabs
        currentTabX = tabBaseX;
        for (JournalTypes.Tab tab : JournalTypes.Tab.values()) {
            String label = getTabLabel(tab.name());
            int tw = screen.getFont().width(label) + 16;
            boolean hovered = mx >= currentTabX && mx <= currentTabX + tw && my >= tabY && my <= tabY + JournalConstants.TAB_HEIGHT;
            boolean active = tab == screen.getCurrentTab() && !screen.isShowingChangeLog();
            int textColor = active ? HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha) : hovered ? HudAnimUtil.withAlpha(0xDDDDDD, safeAlpha) : HudAnimUtil.withAlpha(0x888888, safeAlpha);

            if (safeAlpha > 8) {
                g.drawString(screen.getFont(), label, (int) currentTabX + 8, tabY + (JournalConstants.TAB_HEIGHT - screen.getFont().lineHeight) / 2, textColor, true);
            }
            currentTabX += tw + 4;
        }

        // 4. 渲染 HISTORY Tab
        if (ArcQuestConfig.isQuestHistoryTabEnabled()) {
            boolean hoveredLog = mx >= currentTabX && mx <= currentTabX + logTw && my >= tabY && my <= tabY + JournalConstants.TAB_HEIGHT;
            boolean activeLog = screen.isShowingChangeLog();
            int logColor = activeLog ? HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha) : hoveredLog ? HudAnimUtil.withAlpha(0xDDDDDD, safeAlpha) : HudAnimUtil.withAlpha(0x888888, safeAlpha);

            if (safeAlpha > 8) {
                g.drawString(screen.getFont(), labelHistory, (int) currentTabX + 8, tabY + (JournalConstants.TAB_HEIGHT - screen.getFont().lineHeight) / 2, logColor, true);
            }

            if (QuestChangeNotificationManager.INSTANCE.hasUnreadOtherThan(QuestHudOverlay.INSTANCE.getTrackedQuestId())
                    && safeAlpha > 8) {
                int dotX = (int) currentTabX + logTw - 4;
                int dotY = tabY + 4;
                HudRenderUtil.drawBreathingRedDot(g, dotX, dotY, safeAlpha / 255f);
            }
        }

        // 5. 渲染底部主滑动指示条
        if (safeAlpha > 8 && tabWidthAnim > 0) {
            g.fill((int) tabSlideAnim, tabY + JournalConstants.TAB_HEIGHT - 2, (int) (tabSlideAnim + tabWidthAnim), tabY + JournalConstants.TAB_HEIGHT, HudAnimUtil.withAlpha(theme, safeAlpha));
        }

        // 6. 渲染右侧 GUIDE 按钮
        String labelGuide = getTabLabel("GUIDE");
        int guideTw = screen.getFont().width(labelGuide) + 16;
        int guideX = rightEdgeX - guideTw;

        boolean guideHovered = mx >= guideX && mx <= guideX + guideTw && my >= tabY && my <= tabY + JournalConstants.TAB_HEIGHT;
        int guideColor = guideHovered ? HudAnimUtil.withAlpha(0xDDDDDD, safeAlpha) : HudAnimUtil.withAlpha(0x888888, safeAlpha);

        if (safeAlpha > 8) {
            g.drawString(screen.getFont(), labelGuide, guideX + 8, tabY + (JournalConstants.TAB_HEIGHT - screen.getFont().lineHeight) / 2, guideColor, true);

            if (guideHovered) {
                // GUIDE 按钮在悬停时展示主题色底部高亮，保持机能风的一致性
                g.fill(guideX, tabY + JournalConstants.TAB_HEIGHT - 2, guideX + guideTw, tabY + JournalConstants.TAB_HEIGHT, HudAnimUtil.withAlpha(theme, safeAlpha));
            }
        }
    }

    public boolean mouseClicked(double mx, double my, int tabBaseX, int rightEdgeX) {
        int tabY = 38;
        int currentTabX = tabBaseX;

        for (JournalTypes.Tab tab : JournalTypes.Tab.values()) {
            int tw = screen.getFont().width(getTabLabel(tab.name())) + 16;
            if (mx >= currentTabX && mx <= currentTabX + tw && my >= tabY && my <= tabY + JournalConstants.TAB_HEIGHT) {
                if (screen.isShowingChangeLog() || screen.getCurrentTab() != tab) {
                    screen.setShowingChangeLog(false);
                    screen.setCurrentTab(tab);
                    screen.playClick();
                }
                return true;
            }
            currentTabX += tw + 4;
        }

        if (ArcQuestConfig.isQuestHistoryTabEnabled()) {
            String labelHistory = getTabLabel("HISTORY");
            int logTw = screen.getFont().width(labelHistory) + 16;
            if (mx >= currentTabX && mx <= currentTabX + logTw && my >= tabY && my <= tabY + JournalConstants.TAB_HEIGHT) {
                if (!screen.isShowingChangeLog()) {
                    screen.setShowingChangeLog(true);
                    screen.playClick();
                }
                return true;
            }
        }

        String labelGuide = getTabLabel("GUIDE");
        int guideTw = screen.getFont().width(labelGuide) + 16;
        int guideX = rightEdgeX - guideTw;
        if (mx >= guideX && mx <= guideX + guideTw && my >= tabY && my <= tabY + JournalConstants.TAB_HEIGHT) {
            screen.playClick();
            if (screen.getMinecraft() != null) {
                screen.getMinecraft().setScreen(new GuideListScreen());
            }
            return true;
        }

        return false;
    }
}
