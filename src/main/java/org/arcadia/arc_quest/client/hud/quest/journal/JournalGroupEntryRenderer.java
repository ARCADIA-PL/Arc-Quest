package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.StyledTextUtil;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalChevronRenderer;

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
        HudRenderUtil.drawCyberneticEdge(graphics, x + 2, y, height - 2,
                groupTheme, (int) (0xC0 * effectiveAlpha));

        JournalChevronRenderer.drawExpandable(graphics, x + 14, y + 9, expansion, primaryColor);

        Font font = screen.getFont();

        float nameScale = 0.72f;
        float subtitleScale = 0.55f;
        int textStartX = x + 26;
        int counterX = x + width - 31;
        int subtitleColor = HudAnimUtil.withAlpha(0x8FA7B5, (int) (190 * effectiveAlpha));

        String subtitle = "QUEST GROUP";
        int subtitlePixels = (int) (font.width(subtitle) * subtitleScale);
        int nameMaxPixels = counterX - textStartX - subtitlePixels - 16;
        Component name = group.displayName();
        int unscaledNameMaxPixels = Math.max(0, (int) (nameMaxPixels / nameScale));
        FormattedCharSequence fittedName = StyledTextUtil.fitSingleLine(font, name, unscaledNameMaxPixels);

        float nameY = y + (height - font.lineHeight * nameScale) / 2f;
        graphics.pose().pushPose();
        graphics.pose().translate(textStartX, nameY, 0);
        graphics.pose().scale(nameScale, nameScale, 1f);
        graphics.drawString(font, fittedName, 0, 0, primaryColor, false);
        graphics.pose().popPose();

        int nameEndX = textStartX + (int) (font.width(fittedName) * nameScale);
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

    }

}
