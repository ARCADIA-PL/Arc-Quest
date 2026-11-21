package org.arcadia.arc_quest.client.hud.quest.journal;

import org.arcadia.arc_quest.client.hud.style.HudDraw;
import org.arcadia.arc_quest.client.hud.style.HudStyleRenderer;
import org.arcadia.arc_quest.client.hud.style.OreUiRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudText;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.StyledTextUtil;
import org.arcadia.arc_quest.client.hud.style.HudArrowRenderer;

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
        int height = JournalConstants.ENTRY_HEIGHT;
        int cardHeight = height - JournalConstants.ENTRY_GAP;
        int alpha = (int) (255 * effectiveAlpha);
        int baseGray = (int) (0xD0 + 0x2F * hover);
        int primaryColor = HudAnimUtil.withAlpha(
                (baseGray << 16) | (baseGray << 8) | baseGray, alpha);

        if (HudDraw.isOre()) {
            OreUiRenderer.journalControl(graphics, x + 2, y, width - 10, cardHeight, false, hover > .5f, effectiveAlpha, groupTheme);
        } else if (HudDraw.isStyled()) {
            HudStyleRenderer.card(graphics, x + 2, y, width - 10, cardHeight, false, hover > .5f, effectiveAlpha, groupTheme);
        } else {
            HudDraw.fill(graphics, x + 2, y, x + width - 8, y + cardHeight,
                    HudAnimUtil.withAlpha(0xFFFFFF,
                            (int) ((0x08 + hover * 0x18) * effectiveAlpha)));
            HudDraw.fill(graphics, x + 3, y + 1, x + width - 9, y + height - 3,
                    HudAnimUtil.withAlpha(groupTheme,
                            (int) ((0x06 + hover * 0x08) * effectiveAlpha)));
            HudDraw.fill(graphics, x + 2, y, x + width - 8, y + 1,
                    HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x20 * effectiveAlpha)));
            HudDraw.fill(graphics, x + 2, y + height - 3, x + width - 8, y + cardHeight,
                    HudAnimUtil.withAlpha(groupTheme, (int) (0x25 * effectiveAlpha)));
        }
        if (!HudDraw.isOre()) HudRenderUtil.drawCyberneticEdge(graphics, x + 2, y, cardHeight,
                groupTheme, (int) (0xC0 * effectiveAlpha));

        float centerY = y + (HudDraw.isOre()
                ? (OreUiRenderer.cardTextTop(cardHeight) + OreUiRenderer.cardTextBottom(cardHeight)) / 2f
                : height / 2f);
        HudArrowRenderer.expandable(graphics, x + 14, Math.round(centerY), expansion, primaryColor);

        Font font = screen.getFont();

        float nameScale = QuestListRowTextLayout.titleScale(font.lineHeight);
        float subtitleScale = QuestListRowTextLayout.groupSubtitleScale(font.lineHeight);
        int textStartX = x + 26;
        String count = Integer.toString(row.quests().size());
        int counterX = x + width - 17 - (int) Math.ceil(font.width(count) * nameScale);
        int subtitleColor = HudDraw.isOre() ? HudAnimUtil.withAlpha(0xBBBBBB, alpha)
                : HudAnimUtil.withAlpha(0x8FA7B5, (int) (190 * effectiveAlpha));

        Component subtitle = HudText.of("journal.quest_group");
        int labelWidth = Math.max(0, counterX - textStartX - 6);
        var fittedSubtitle = StyledTextUtil.fitSingleLine(font, subtitle, (int) (labelWidth * .4f / subtitleScale));
        int subtitlePixels = (int) Math.ceil(font.width(fittedSubtitle) * subtitleScale);
        int nameMaxPixels = labelWidth - (subtitlePixels > 0 ? subtitlePixels + 10 : 0);
        Component name = group.displayName();
        int unscaledNameMaxPixels = Math.max(0, (int) (nameMaxPixels / nameScale));
        FormattedCharSequence fittedName = StyledTextUtil.fitSingleLine(font, name, unscaledNameMaxPixels);

        float nameY = centerY - font.lineHeight * nameScale / 2f;
        graphics.pose().pushPose();
        graphics.pose().translate(textStartX, nameY, 0);
        graphics.pose().scale(nameScale, nameScale, 1f);
        HudDraw.drawString(graphics, font, fittedName, 0, 0, primaryColor, false);
        graphics.pose().popPose();

        if (subtitlePixels > 0) {
            int nameEndX = textStartX + (int) Math.ceil(font.width(fittedName) * nameScale);
            int separatorX = nameEndX + 5;
            HudDraw.fill(graphics, separatorX, (int) centerY - 1, separatorX + 2, (int) centerY + 1,
                    HudAnimUtil.withAlpha(groupTheme, (int) (160 * effectiveAlpha)));

            int subtitleX = separatorX + 5;
            float subtitleY = centerY - font.lineHeight * subtitleScale / 2f;
            graphics.pose().pushPose();
            graphics.pose().translate(subtitleX, subtitleY, 0);
            graphics.pose().scale(subtitleScale, subtitleScale, 1f);
            HudDraw.drawString(graphics, font, fittedSubtitle, 0, 0, subtitleColor, false);
            graphics.pose().popPose();
        }

        graphics.pose().pushPose();
        graphics.pose().translate(counterX, centerY - font.lineHeight * nameScale / 2f, 0);
        graphics.pose().scale(nameScale, nameScale, 1f);
        HudDraw.drawString(graphics, font, count, 0, 0, primaryColor, false);
        graphics.pose().popPose();

    }

}
