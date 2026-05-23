package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.guide.GuideListScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.history.QuestChangeNotificationManager;

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

    public void render(GuiGraphics g, int mx, int my, int safeAlpha, int tabBaseX, int rightEdgeX, int theme, float dt) {
        int tabY = 38;
        float targetTabX = 0, currentTabX = tabBaseX, targetTabW = 0;

        for (JournalTypes.Tab tab : JournalTypes.Tab.values()) {
            int tw = screen.getFont().width(getTabLabel(tab)) + 16;
            if (tab == screen.getCurrentTab() && !screen.isShowingChangeLog()) {
                targetTabX = currentTabX;
                targetTabW = tw;
            }
            currentTabX += tw + 4;
        }

        // 伪装的第四个原生 Tab: HISTORY
        String logLabel = "HISTORY";
        int logTw = screen.getFont().width(logLabel) + 16;
        if (screen.isShowingChangeLog()) {
            targetTabX = currentTabX;
            targetTabW = logTw;
        }

        // 滑块动画
        if (tabWidthAnim <= 0.1f) {
            tabSlideAnim = targetTabX;
            tabWidthAnim = targetTabW;
        }
        float lerpFactor = Math.min(1.0f, dt * 15f);
        tabSlideAnim += (targetTabX - tabSlideAnim) * lerpFactor;
        tabWidthAnim += (targetTabW - tabWidthAnim) * lerpFactor;

        // 渲染基础 Tabs
        currentTabX = tabBaseX;
        for (JournalTypes.Tab tab : JournalTypes.Tab.values()) {
            String label = getTabLabel(tab);
            int tw = screen.getFont().width(label) + 16;
            boolean hovered = mx >= currentTabX && mx <= currentTabX + tw && my >= tabY && my <= tabY + JournalConstants.TAB_HEIGHT;
            boolean active = tab == screen.getCurrentTab() && !screen.isShowingChangeLog();
            int textColor = active ? HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha) : hovered ? HudAnimUtil.withAlpha(0xDDDDDD, safeAlpha) : HudAnimUtil.withAlpha(0x888888, safeAlpha);

            if (safeAlpha > 8) {
                g.drawString(screen.getFont(), label, (int) currentTabX + 8, tabY + (JournalConstants.TAB_HEIGHT - screen.getFont().lineHeight) / 2, textColor, true);
            }
            currentTabX += tw + 4;
        }

        // 渲染 HISTORY Tab
        boolean hoveredLog = mx >= currentTabX && mx <= currentTabX + logTw && my >= tabY && my <= tabY + JournalConstants.TAB_HEIGHT;
        boolean activeLog = screen.isShowingChangeLog();
        int logColor = activeLog ? HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha) : hoveredLog ? HudAnimUtil.withAlpha(0xDDDDDD, safeAlpha) : HudAnimUtil.withAlpha(0x888888, safeAlpha);

        if (safeAlpha > 8) {
            g.drawString(screen.getFont(), logLabel, (int) currentTabX + 8, tabY + (JournalConstants.TAB_HEIGHT - screen.getFont().lineHeight) / 2, logColor, true);
        }

        if (QuestChangeNotificationManager.INSTANCE.hasAnyUnread() && safeAlpha > 8) {
            int rhombusX = (int) currentTabX + logTw - 4;
            int rhombusY = tabY + 4;
            HudRenderUtil.drawBreathingRhombus(g, rhombusX, rhombusY, 0xFFFFD166, (System.currentTimeMillis() / 1000f), safeAlpha / 255f);
        }

        // 渲染底部滑动指示条
        if (safeAlpha > 8 && tabWidthAnim > 0) {
            g.fill((int) tabSlideAnim, tabY + JournalConstants.TAB_HEIGHT - 2, (int) (tabSlideAnim + tabWidthAnim), tabY + JournalConstants.TAB_HEIGHT, HudAnimUtil.withAlpha(theme, safeAlpha));
        }

        // --- 右侧悬浮功能级选项卡: GUIDE [G] ---
        String guideLabel = "GUIDE [G]";
        int guideTw = screen.getFont().width(guideLabel) + 16;
        int guideX = rightEdgeX - guideTw;

        boolean guideHovered = mx >= guideX && mx <= guideX + guideTw && my >= tabY && my <= tabY + JournalConstants.TAB_HEIGHT;
        int guideColor = guideHovered ? HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha) : HudAnimUtil.withAlpha(0xAAAAAA, safeAlpha);

        if (safeAlpha > 8) {
            if (guideHovered) {
                g.fill(guideX, tabY, guideX + guideTw, tabY + JournalConstants.TAB_HEIGHT, HudAnimUtil.withAlpha(theme, (int)(safeAlpha * 0.25f)));
                HudRenderUtil.drawCyberneticEdge(g, guideX, tabY, JournalConstants.TAB_HEIGHT, theme, safeAlpha);
            }
            g.drawString(screen.getFont(), guideLabel, guideX + 8, tabY + (JournalConstants.TAB_HEIGHT - screen.getFont().lineHeight) / 2, guideColor, true);

            // 底部点缀高亮边缘，对标左侧 Tabs 的样式
            g.fill(guideX, tabY + JournalConstants.TAB_HEIGHT - 2, guideX + guideTw, tabY + JournalConstants.TAB_HEIGHT, HudAnimUtil.withAlpha(theme, (int)(safeAlpha * 0.5f)));
        }
    }

    public boolean mouseClicked(double mx, double my, int tabBaseX, int rightEdgeX) {
        int tabY = 38;
        int currentTabX = tabBaseX;

        // 基础 Tabs 点击检测
        for (JournalTypes.Tab tab : JournalTypes.Tab.values()) {
            int tw = screen.getFont().width(getTabLabel(tab)) + 16;
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

        // HISTORY Tab 点击检测
        String logLabel = "HISTORY";
        int logTw = screen.getFont().width(logLabel) + 16;
        if (mx >= currentTabX && mx <= currentTabX + logTw && my >= tabY && my <= tabY + JournalConstants.TAB_HEIGHT) {
            if (!screen.isShowingChangeLog()) {
                screen.setShowingChangeLog(true);
                screen.playClick();
            }
            return true;
        }

        // GUIDE [G] 按钮点击检测
        String guideLabel = "GUIDE [G]";
        int guideTw = screen.getFont().width(guideLabel) + 16;
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