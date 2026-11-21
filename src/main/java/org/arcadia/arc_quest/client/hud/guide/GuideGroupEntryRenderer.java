package org.arcadia.arc_quest.client.hud.guide;

import org.arcadia.arc_quest.client.hud.style.HudDraw;
import org.arcadia.arc_quest.client.hud.style.HudStyleRenderer;
import net.minecraft.client.gui.Font;
import org.arcadia.arc_quest.client.hud.style.HudArrowRenderer;
import org.arcadia.arc_quest.client.hud.style.OreUiRenderer;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.StyledTextUtil;
import org.arcadia.arc_quest.guide.network.ClientGuideCache;

final class GuideGroupEntryRenderer {

    private final GuideListScreen screen;

    GuideGroupEntryRenderer(GuideListScreen screen) {
        this.screen = screen;
    }

    void render(GuiGraphics graphics, GuideListLayout.GroupRow row,
                int x, int y, int width, int fallbackTheme, float expansion,
                float hover, float effectiveAlpha) {
        int height = GuideConstants.ENTRY_HEIGHT;
        int theme = row.group().getThemeColor() == 0xFFFFFFFF
                ? fallbackTheme : row.group().getThemeColor();
        int gray = (int) (0xD0 + 0x2F * hover);
        int textColor = HudAnimUtil.withAlpha(
                (gray << 16) | (gray << 8) | gray, (int) (255 * effectiveAlpha));

        if (HudDraw.isOre()) {
            OreUiRenderer.journalControl(graphics, x + 2, y, width - 10, height - 2, false, hover > .5f, effectiveAlpha, theme);
        } else if (HudDraw.isStyled()) {
            HudStyleRenderer.card(graphics, x + 2, y, width - 10, height - 2, false, hover > .5f, effectiveAlpha, theme);
        } else {
            HudDraw.fill(graphics, x + 2, y, x + width - 8, y + height - 2,
                    HudAnimUtil.withAlpha(0xFFFFFF, (int) ((0x08 + hover * 0x18) * effectiveAlpha)));
            HudDraw.fill(graphics, x + 3, y + 1, x + width - 9, y + height - 3,
                    HudAnimUtil.withAlpha(theme, (int) ((0x06 + hover * 0x08) * effectiveAlpha)));
            HudDraw.fill(graphics, x + 2, y + height - 3, x + width - 8, y + height - 2,
                    HudAnimUtil.withAlpha(theme, (int) (0x25 * effectiveAlpha)));
        }
        if (!HudDraw.isOre()) HudRenderUtil.drawCyberneticEdge(graphics, x + 2, y, height - 2,
                theme, (int) (0xC0 * effectiveAlpha));

        int cardHeight = height - 2;
        float centerY = y + (HudDraw.isOre()
                ? (OreUiRenderer.cardTextTop(cardHeight) + OreUiRenderer.cardTextBottom(cardHeight)) / 2f
                : cardHeight / 2f);
        HudArrowRenderer.expandable(graphics, x + 13, Math.round(centerY), expansion, textColor);
        Font font = screen.getFont();
        String count = Integer.toString(row.guides().size());
        boolean hasUnread = row.guides().stream()
                .anyMatch(guide -> !ClientGuideCache.INSTANCE.isSeen(guide.getId()));
        int textX = x + 26;
        int countX = x + width - (hasUnread ? 28 : 16) - font.width(count);
        var title = StyledTextUtil.fitSingleLine(
                font, row.group().getDisplayName(), Math.max(1, countX - textX - 8));
        int textY = Math.round(centerY - font.lineHeight / 2f);
        HudDraw.drawString(graphics, font, title, textX, textY, textColor, false);
        HudDraw.drawString(graphics, font, count, countX, textY, textColor, false);
        if (hasUnread) {
            HudRenderUtil.drawBreathingRedDot(graphics, x + width - 14,
                    Math.round(centerY), effectiveAlpha);
        }
    }

}
