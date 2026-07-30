package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.guide.GuideListScreen;
import org.arcadia.arc_quest.guide.api.GuideCategory;
import org.arcadia.arc_quest.guide.network.ClientGuideCache;

import java.util.List;

final class JournalGuideMenu {

    private static final int HORIZONTAL_PADDING = 10;
    private static final int LETTER_SPACING = 1;
    private static final int ACCENT_HEIGHT = 3;
    private static final int POPUP_GAP = 7;
    private static final int POPUP_EXTRA_HEIGHT = 10;
    private final QuestJournalScreen screen;
    private float buttonHoverAnimation;
    private float popupAnimation;

    JournalGuideMenu(QuestJournalScreen screen) {
        this.screen = screen;
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY, int safeAlpha,
                int rightEdgeX, int buttonY, int themeColor, float deltaTime) {
        Font font = screen.getFont();
        String label = Component.translatable("arc_quest.gui.journal.tab.guide").getString();
        ButtonLayout button = buttonLayout(font, label, rightEdgeX, buttonY);
        List<GuideCategory> categories = GuideListScreen.visibleCategoriesSnapshot();
        PopupLayout popup = popupLayout(font, categories, button);
        boolean buttonHovered = button.contains(mouseX, mouseY);
        boolean popupHovered = !categories.isEmpty() && popup.hoverRegionContains(mouseX, mouseY, button.x());
        float hoverTarget = buttonHovered ? 1f : 0f;
        float popupTarget = !categories.isEmpty() && (buttonHovered || popupHovered) ? 1f : 0f;
        buttonHoverAnimation = HudAnimUtil.smoothExp(buttonHoverAnimation, hoverTarget, 15f, deltaTime);
        popupAnimation = HudAnimUtil.smoothExp(popupAnimation, popupTarget, 13f, deltaTime);
        if (buttonHovered || popupHovered) screen.requestPointerCursor();

        if (popupAnimation > 0.01f && !categories.isEmpty()) {
            renderPopup(graphics, font, categories, popup, mouseX, mouseY, themeColor, safeAlpha);
        }
        renderButton(graphics, font, label, button, themeColor, safeAlpha);
    }

    boolean mouseClicked(double mouseX, double mouseY, int rightEdgeX, int buttonY) {
        Font font = screen.getFont();
        String label = Component.translatable("arc_quest.gui.journal.tab.guide").getString();
        ButtonLayout button = buttonLayout(font, label, rightEdgeX, buttonY);
        if (button.contains(mouseX, mouseY)) {
            openGuide(null);
            return true;
        }

        List<GuideCategory> categories = GuideListScreen.visibleCategoriesSnapshot();
        if (categories.isEmpty() || popupAnimation < 0.15f) return false;
        PopupLayout popup = popupLayout(font, categories, button);
        int tabX = popup.x() + 5;
        for (GuideCategory category : categories) {
            int tabWidth = categoryWidth(font, category);
            if (mouseX >= tabX && mouseX <= tabX + tabWidth
                    && mouseY >= popup.y() && mouseY <= popup.y() + popup.height()) {
                openGuide(category.getId());
                return true;
            }
            tabX += tabWidth + 3;
        }
        return false;
    }

    private void renderButton(GuiGraphics graphics, Font font, String label, ButtonLayout layout,
                              int themeColor, int safeAlpha) {
        int backgroundAlpha = Math.round((190 + 30 * buttonHoverAnimation) * safeAlpha / 255f);
        int borderAlpha = Math.round((125 + 55 * buttonHoverAnimation) * safeAlpha / 255f);
        HudAnimUtil.drawFrame(graphics, layout.x(), layout.y(), layout.width(), layout.height(),
                HudAnimUtil.withAlpha(0x070A0F, backgroundAlpha),
                HudAnimUtil.withAlpha(themeColor, borderAlpha));
        int textX = layout.x() + (layout.width() - spacedWidth(font, label)) / 2;
        int textY = layout.y() + (layout.height() - font.lineHeight) / 2;
        drawSpacedString(graphics, font, label, textX, textY,
                HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha));
        graphics.fill(layout.x(), layout.y() + layout.height() - ACCENT_HEIGHT,
                layout.x() + layout.width(), layout.y() + layout.height(),
                HudAnimUtil.withAlpha(themeColor, safeAlpha));
        if (ClientGuideCache.INSTANCE.hasUnreadGuides()) {
            HudRenderUtil.drawBreathingRedDot(graphics, layout.x() + layout.width() - 4,
                    layout.y() + 4, safeAlpha / 255f);
        }
    }

    private void renderPopup(GuiGraphics graphics, Font font, List<GuideCategory> categories,
                             PopupLayout popup, int mouseX, int mouseY, int themeColor, int safeAlpha) {
        float eased = HudAnimUtil.easeOutCubic(popupAnimation);
        int alpha = Math.round(safeAlpha * eased);
        int drawX = popup.x() + Math.round((1f - eased) * 18f);
        HudAnimUtil.drawFrame(graphics, drawX, popup.y(), popup.width(), popup.height(),
                HudAnimUtil.withAlpha(0x070A0F, Math.round(230 * alpha / 255f)),
                HudAnimUtil.withAlpha(themeColor, Math.round(145 * alpha / 255f)));
        graphics.fill(drawX, popup.y() + popup.height() - ACCENT_HEIGHT,
                drawX + popup.width(), popup.y() + popup.height(),
                HudAnimUtil.withAlpha(themeColor, alpha));

        int tabX = drawX + 5;
        for (GuideCategory category : categories) {
            int tabWidth = categoryWidth(font, category);
            boolean hovered = mouseX >= tabX && mouseX <= tabX + tabWidth
                    && mouseY >= popup.y() && mouseY <= popup.y() + popup.height();
            if (hovered) {
                graphics.fill(tabX, popup.y() + 3, tabX + tabWidth, popup.y() + popup.height() - ACCENT_HEIGHT,
                        HudAnimUtil.withAlpha(0xFFFFFF, Math.round(24 * alpha / 255f)));
            }
            int categoryColor = category.getThemeColor() == 0xFFFFFFFF ? themeColor : category.getThemeColor();
            graphics.fill(tabX + 2, popup.y() + 7, tabX + 4, popup.y() + popup.height() - 8,
                    HudAnimUtil.withAlpha(categoryColor, alpha));
            String categoryLabel = font.plainSubstrByWidth(category.getDisplayName().getString(), tabWidth - 13);
            graphics.drawString(font, categoryLabel, tabX + 8,
                    popup.y() + (popup.height() - font.lineHeight) / 2 - 1,
                    HudAnimUtil.withAlpha(hovered ? 0xFFFFFF : 0xD4DAE1, alpha), true);
            tabX += tabWidth + 3;
        }

        int connectorY = popup.y() + popup.height() / 2;
        int connectorX = drawX + popup.width();
        if (connectorX < popup.buttonX() - 2) {
            graphics.fill(connectorX, connectorY - 1, popup.buttonX() - 2, connectorY + 1,
                    HudAnimUtil.withAlpha(themeColor, alpha));
        }
        graphics.fill(popup.buttonX() - 4, connectorY - 3, popup.buttonX() - 2, connectorY + 4,
                HudAnimUtil.withAlpha(themeColor, alpha));
    }

    private void openGuide(net.minecraft.resources.ResourceLocation categoryId) {
        screen.playClick();
        GuideListScreen.openFromJournal(screen, categoryId);
    }

    private ButtonLayout buttonLayout(Font font, String label, int rightEdgeX, int buttonY) {
        int width = spacedWidth(font, label) + HORIZONTAL_PADDING * 2;
        return new ButtonLayout(rightEdgeX - width, buttonY, width, JournalConstants.TAB_HEIGHT);
    }

    private PopupLayout popupLayout(Font font, List<GuideCategory> categories, ButtonLayout button) {
        int width = 10;
        for (GuideCategory category : categories) width += categoryWidth(font, category) + 3;
        width = Math.max(58, width - 3);
        int height = button.height() + POPUP_EXTRA_HEIGHT;
        return new PopupLayout(button.x() - POPUP_GAP - width,
                button.y() - POPUP_EXTRA_HEIGHT / 2, width, height, button.x());
    }

    private int categoryWidth(Font font, GuideCategory category) {
        return Math.max(58, Math.min(104, font.width(category.getDisplayName()) + 18));
    }

    private int spacedWidth(Font font, String text) {
        if (text.isEmpty()) return 0;
        int width = 0;
        for (int index = 0; index < text.length(); index++) width += font.width(text.substring(index, index + 1));
        return width + (text.length() - 1) * LETTER_SPACING;
    }

    private void drawSpacedString(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        int drawX = x;
        for (int index = 0; index < text.length(); index++) {
            String character = text.substring(index, index + 1);
            graphics.drawString(font, character, drawX, y, color, true);
            drawX += font.width(character) + LETTER_SPACING;
        }
    }

    private record ButtonLayout(int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private record PopupLayout(int x, int y, int width, int height, int buttonX) {
        private boolean hoverRegionContains(double mouseX, double mouseY, int buttonLeft) {
            return mouseX >= x && mouseX <= buttonLeft && mouseY >= y && mouseY <= y + height;
        }
    }
}
