package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.guide.GuideListScreen;
import org.arcadia.arc_quest.client.hud.quest.history.QuestHistoryPanel;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalButtonRenderer;
import org.arcadia.arc_quest.guide.network.ClientGuideCache;

final class JournalGuideMenu {

    private static final int HORIZONTAL_PADDING = 8;
    private final QuestJournalScreen screen;
    private float hoverAnimation;

    JournalGuideMenu(QuestJournalScreen screen) {
        this.screen = screen;
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY, int safeAlpha,
                int rightEdgeX, int buttonY, int themeColor, float deltaTime) {
        Font font = screen.getFont();
        String label = Component.translatable("arc_quest.gui.journal.tab.guide").getString();
        HudRect button = buttonLayout(font, label, rightEdgeX, buttonY);
        boolean blocked = QuestHistoryPanel.isActive();
        boolean buttonHovered = !blocked && button.contains(mouseX, mouseY);
        hoverAnimation = HudAnimUtil.smoothExp(hoverAnimation, buttonHovered ? 1f : 0f, 15f, deltaTime);
        if (buttonHovered) screen.requestPointerCursor();
        JournalButtonRenderer.drawUnderlineButton(graphics, font, button, label,
                themeColor, safeAlpha, hoverAnimation, ClientGuideCache.INSTANCE.hasUnreadGuides());
    }

    boolean mouseClicked(double mouseX, double mouseY, int rightEdgeX, int buttonY) {
        if (QuestHistoryPanel.isActive()) return true;
        Font font = screen.getFont();
        String label = Component.translatable("arc_quest.gui.journal.tab.guide").getString();
        HudRect button = buttonLayout(font, label, rightEdgeX, buttonY);
        if (button.contains(mouseX, mouseY)) {
            openGuide();
            return true;
        }
        return false;
    }

    private void openGuide() {
        screen.playClick();
        GuideListScreen.openFromJournal(screen, null);
    }

    private HudRect buttonLayout(Font font, String label, int rightEdgeX, int buttonY) {
        int width = font.width(label) + HORIZONTAL_PADDING * 2;
        return new HudRect(rightEdgeX - width, buttonY, width, JournalConstants.TAB_HEIGHT);
    }

}
