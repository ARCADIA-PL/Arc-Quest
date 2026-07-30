package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.guide.GuideListScreen;
import org.arcadia.arc_quest.client.hud.quest.history.QuestHistoryPanel;
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
        ButtonLayout button = buttonLayout(font, label, rightEdgeX, buttonY);
        boolean blocked = QuestHistoryPanel.isActive();
        boolean buttonHovered = !blocked && button.contains(mouseX, mouseY);
        hoverAnimation = HudAnimUtil.smoothExp(hoverAnimation, buttonHovered ? 1f : 0f, 15f, deltaTime);
        if (buttonHovered) screen.requestPointerCursor();
        renderButton(graphics, font, label, button, themeColor, safeAlpha);
    }

    boolean mouseClicked(double mouseX, double mouseY, int rightEdgeX, int buttonY) {
        if (QuestHistoryPanel.isActive()) return true;
        Font font = screen.getFont();
        String label = Component.translatable("arc_quest.gui.journal.tab.guide").getString();
        ButtonLayout button = buttonLayout(font, label, rightEdgeX, buttonY);
        if (button.contains(mouseX, mouseY)) {
            openGuide();
            return true;
        }
        return false;
    }

    private void renderButton(GuiGraphics graphics, Font font, String label, ButtonLayout layout,
                              int themeColor, int safeAlpha) {
        if (safeAlpha <= 8) return;
        float hoverEase = HudAnimUtil.easeOutCubic(hoverAnimation);
        int textX = layout.x() + HORIZONTAL_PADDING + Math.round(2f * hoverEase);
        int textY = layout.y() + (layout.height() - font.lineHeight) / 2;
        graphics.fill(layout.x(), layout.y() + 2, layout.x() + layout.width(), layout.y() + layout.height() - 2,
                HudAnimUtil.withAlpha(themeColor, Math.round(20 * hoverEase * safeAlpha / 255f)));
        graphics.drawString(font, label, textX, textY,
                HudAnimUtil.withAlpha(HudAnimUtil.lerpColor(0xEEEEEE, 0xFFFFFF, hoverEase), safeAlpha), true);
        graphics.fill(layout.x(), layout.y() + layout.height() - 2,
                layout.x() + layout.width(), layout.y() + layout.height(),
                HudAnimUtil.withAlpha(themeColor, Math.round((205 + 50 * hoverEase) * safeAlpha / 255f)));
        if (ClientGuideCache.INSTANCE.hasUnreadGuides()) {
            HudRenderUtil.drawBreathingRedDot(graphics, layout.x() + layout.width() - 4,
                    layout.y() + 4, safeAlpha / 255f);
        }
    }

    private void openGuide() {
        screen.playClick();
        GuideListScreen.openFromJournal(screen, null);
    }

    private ButtonLayout buttonLayout(Font font, String label, int rightEdgeX, int buttonY) {
        int width = font.width(label) + HORIZONTAL_PADDING * 2;
        return new ButtonLayout(rightEdgeX - width, buttonY, width, JournalConstants.TAB_HEIGHT);
    }

    private record ButtonLayout(int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

}
