package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.quest.api.QuestGroupDefinition;

final class JournalGroupEntryRenderer {

    private final QuestJournalScreen screen;

    JournalGroupEntryRenderer(QuestJournalScreen screen) {
        this.screen = screen;
    }

    void render(GuiGraphics graphics, JournalListLayout.GroupRow row,
                int x, int y, int width, int fallbackTheme,
                float expansion, float hover, float effectiveAlpha,
                boolean hasUnread) {
        QuestGroupDefinition group = row.group();
        int groupTheme = group.getThemeColor() == 0xFFFFFFFF ? fallbackTheme : group.getThemeColor();
        int height = JournalConstants.GROUP_ENTRY_HEIGHT;
        int alpha = (int) (255 * effectiveAlpha);
        int baseGray = (int) (0xD0 + 0x2F * hover);
        int primaryColor = HudAnimUtil.withAlpha(
                (baseGray << 16) | (baseGray << 8) | baseGray, alpha);

        graphics.fill(x + 2, y, x + width - 8, y + height - 2,
                HudAnimUtil.withAlpha(0xFFFFFF,
                        (int) ((0x08 + hover * 0x18) * effectiveAlpha)));
        graphics.fill(x + 3, y + 1, x + width - 9, y + height - 3,
                HudAnimUtil.withAlpha(groupTheme,
                        (int) ((0x06 + hover * 0x08) * effectiveAlpha)));
        graphics.fill(x + 2, y, x + width - 8, y + 1,
                HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x20 * effectiveAlpha)));
        graphics.fill(x + 2, y + height - 3, x + width - 8, y + height - 2,
                HudAnimUtil.withAlpha(groupTheme, (int) (0x25 * effectiveAlpha)));
        drawCyberneticEdge(graphics, x + 2, y, height - 2,
                groupTheme, (int) (0xC0 * effectiveAlpha));

        drawChevron(graphics, x + 14, y + 9, expansion, primaryColor);

        Font font = screen.getFont();

        float nameScale = 0.72f;
        float subtitleScale = 0.55f;
        int textStartX = x + 26;
        int counterX = x + width - 31;
        int subtitleColor = HudAnimUtil.withAlpha(0x8FA7B5, (int) (190 * effectiveAlpha));

        String subtitle = "QUEST GROUP";
        int subtitlePixels = (int) (font.width(subtitle) * subtitleScale);
        int nameMaxPixels = counterX - textStartX - subtitlePixels - 16;
        String name = group.getDisplayName().getString();
        int nameFullPixels = (int) (font.width(name) * nameScale);
        if (nameFullPixels > nameMaxPixels) {
            int allowedWidth = (int) (nameMaxPixels / nameScale) - font.width("...");
            name = font.plainSubstrByWidth(name, Math.max(0, allowedWidth)) + "...";
        }

        float nameY = y + (height - font.lineHeight * nameScale) / 2f;
        graphics.pose().pushPose();
        graphics.pose().translate(textStartX, nameY, 0);
        graphics.pose().scale(nameScale, nameScale, 1f);
        graphics.drawString(font, name, 0, 0, primaryColor, false);
        graphics.pose().popPose();

        int nameEndX = textStartX + (int) (font.width(name) * nameScale);
        int separatorX = nameEndX + 5;
        graphics.fill(separatorX, y + height / 2 - 1, separatorX + 2, y + height / 2 + 1,
                HudAnimUtil.withAlpha(groupTheme, (int) (160 * effectiveAlpha)));

        int subtitleX = separatorX + 5;
        float subtitleY = y + (height - font.lineHeight * subtitleScale) / 2f;
        graphics.pose().pushPose();
        graphics.pose().translate(subtitleX, subtitleY, 0);
        graphics.pose().scale(subtitleScale, subtitleScale, 1f);
        graphics.drawString(font, subtitle, 0, 0, subtitleColor, false);
        graphics.pose().popPose();

        String count = Integer.toString(row.quests().size());
        float countScale = 0.72f;
        graphics.pose().pushPose();
        graphics.pose().translate(counterX, y + (height - font.lineHeight * countScale) / 2f, 0);
        graphics.pose().scale(countScale, countScale, 1f);
        graphics.drawString(font, count, 0, 0, primaryColor, false);
        graphics.pose().popPose();

        if (hasUnread) {
            HudRenderUtil.drawBreathingRedDot(graphics, counterX - 10,
                    y + height / 2, effectiveAlpha);
        }
    }

    private void drawCyberneticEdge(GuiGraphics graphics, int x, int y,
                                    int height, int themeColor, int alpha) {
        if (alpha < 5) return;
        int coreColor = themeColor & 0xFFFFFF;
        int topColor = coreColor | (alpha << 24);
        int bottomColor = coreColor | ((int) (alpha * 0.15f) << 24);
        graphics.fillGradient(x, y, x + 3, y + height, topColor, bottomColor);
        int glowColor = 0xFFFFFF | ((int) (alpha * 0.8f) << 24);
        graphics.fillGradient(x, y, x + 1, y + height / 2, glowColor, topColor);
    }

    private void drawChevron(GuiGraphics graphics, int centerX, int centerY,
                             float expansion, int color) {
        float eased = HudAnimUtil.easeOutCubic(expansion);
        int leftX = Math.round(lerp(centerX - 2, centerX - 4, eased));
        int leftY = Math.round(lerp(centerY - 4, centerY - 2, eased));
        int middleX = Math.round(lerp(centerX + 2, centerX, eased));
        int middleY = Math.round(lerp(centerY, centerY + 2, eased));
        int rightX = Math.round(lerp(centerX - 2, centerX + 4, eased));
        int rightY = Math.round(lerp(centerY + 4, centerY - 2, eased));
        drawPixelLine(graphics, leftX, leftY, middleX, middleY, color);
        drawPixelLine(graphics, middleX, middleY, rightX, rightY, color);
    }

    private void drawPixelLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int step = 0; step <= steps; step++) {
            float progress = steps == 0 ? 0f : (float) step / steps;
            int x = Math.round(lerp(x1, x2, progress));
            int y = Math.round(lerp(y1, y2, progress));
            graphics.fill(x, y, x + 2, y + 2, color);
        }
    }

    private float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }
}
