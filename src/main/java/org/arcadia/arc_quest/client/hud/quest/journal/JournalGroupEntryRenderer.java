package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.StyledTextUtil;

final class JournalGroupEntryRenderer {

    private final QuestJournalScreen screen;

    JournalGroupEntryRenderer(QuestJournalScreen screen) {
        this.screen = screen;
    }

    void render(GuiGraphics graphics, JournalListLayout.GroupRow row,
                int x, int y, int width, int fallbackTheme,
                float expansion, float hover, float effectiveAlpha) {
        JournalListLayout.GroupDefinition group = row.group();
        int groupTheme = group.themeColor() == 0xFFFFFFFF ? fallbackTheme : group.themeColor();
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

        int chevronCenterX = x + 13;
        int chevronCenterY = y + (height - 2) / 2 - 1;
        drawChevron(graphics, chevronCenterX, chevronCenterY, expansion, primaryColor);

        Font font = screen.getFont();

        float textScale = 1f;
        int textStartX = x + 26;
        String count = Integer.toString(row.quests().size());
        int counterX = x + width - 16 - font.width(count);
        int nameMaxPixels = counterX - textStartX - 8;
        Component name = group.displayName();
        FormattedCharSequence fittedName = StyledTextUtil.fitSingleLine(font, name, nameMaxPixels);

        float textY = y + (height - font.lineHeight * textScale) / 2f - 0.5f;
        graphics.pose().pushPose();
        graphics.pose().translate(textStartX, textY, 0);
        graphics.pose().scale(textScale, textScale, 1f);
        graphics.drawString(font, fittedName, 0, 0, primaryColor, false);
        graphics.pose().popPose();

        graphics.pose().pushPose();
        graphics.pose().translate(counterX, textY, 0);
        graphics.pose().scale(textScale, textScale, 1f);
        graphics.drawString(font, count, 0, 0, primaryColor, false);
        graphics.pose().popPose();

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
