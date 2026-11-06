package org.arcadia.arc_quest.client.hud.quest.journal;

import org.arcadia.arc_quest.client.hud.style.HudDraw;
import org.arcadia.arc_quest.client.hud.style.HudStyleRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudText;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.StyledTextUtil;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalChevronRenderer;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalScaledTextRenderer;
import org.arcadia.arc_quest.client.hud.component.HudRect;

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

        if (HudDraw.isStyled()) {
            HudStyleRenderer.card(graphics, x + 2, y, width - 10, height - 2, false, hover > .5f, effectiveAlpha, groupTheme);
        } else {
            HudDraw.fill(graphics, x + 2, y, x + width - 8, y + height - 2,
                    HudAnimUtil.withAlpha(0xFFFFFF,
                            (int) ((0x08 + hover * 0x18) * effectiveAlpha)));
            HudDraw.fill(graphics, x + 3, y + 1, x + width - 9, y + height - 3,
                    HudAnimUtil.withAlpha(groupTheme,
                            (int) ((0x06 + hover * 0x08) * effectiveAlpha)));
            HudDraw.fill(graphics, x + 2, y, x + width - 8, y + 1,
                    HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x20 * effectiveAlpha)));
            HudDraw.fill(graphics, x + 2, y + height - 3, x + width - 8, y + height - 2,
                    HudAnimUtil.withAlpha(groupTheme, (int) (0x25 * effectiveAlpha)));
        }
        HudRenderUtil.drawCyberneticEdge(graphics, x + 2, y, height - 2,
                groupTheme, (int) (0xC0 * effectiveAlpha));

        JournalChevronRenderer.drawExpandable(graphics, x + 14, y + 9, expansion, primaryColor);

        Font font = screen.getFont();

        float nameScale = JournalScaledTextRenderer.readableScale(graphics, .82f);
        float subtitleScale = JournalScaledTextRenderer.readableScale(graphics, .78f);
        int textStartX = x + 26;
        int counterX = x + width - 31;
        int subtitleColor = HudAnimUtil.withAlpha(0x9AAEBC, (int) (225 * effectiveAlpha));

        Component subtitle = HudText.of("journal.quest_group");
        int subtitlePixels = (int) (font.width(subtitle) * subtitleScale);
        int nameMaxPixels = counterX - textStartX - subtitlePixels - 16;
        Component name = group.displayName();
        int unscaledNameMaxPixels = Math.max(0, (int) (nameMaxPixels / nameScale));
        FormattedCharSequence fittedName = StyledTextUtil.fitSingleLine(font, name, unscaledNameMaxPixels);

        float nameWidth = font.width(fittedName) * nameScale;
        JournalScaledTextRenderer.centered(graphics, font, fittedName,
                new HudRect(textStartX, y, Math.round(nameWidth), height), nameScale, primaryColor, false);

        int nameEndX = textStartX + (int) (font.width(fittedName) * nameScale);
        int separatorX = nameEndX + 5;
        HudDraw.fill(graphics, separatorX, y + height / 2 - 1, separatorX + 2, y + height / 2 + 1,
                HudAnimUtil.withAlpha(groupTheme, (int) (160 * effectiveAlpha)));

        int subtitleX = separatorX + 5;
        JournalScaledTextRenderer.centered(graphics, font, subtitle,
                new HudRect(subtitleX, y, Math.max(1, subtitlePixels), height), subtitleScale, subtitleColor, false);

        String count = Integer.toString(row.quests().size());
        float countScale = JournalScaledTextRenderer.readableScale(graphics, .82f);
        JournalScaledTextRenderer.centered(graphics, font, count,
                new HudRect(counterX, y, Math.max(1, Math.round(font.width(count) * countScale)), height), countScale, primaryColor, false);

    }

}
