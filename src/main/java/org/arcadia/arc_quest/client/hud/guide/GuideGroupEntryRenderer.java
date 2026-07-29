package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.client.gui.Font;
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

        graphics.fill(x + 2, y, x + width - 8, y + height - 2,
                HudAnimUtil.withAlpha(0xFFFFFF, (int) ((0x08 + hover * 0x18) * effectiveAlpha)));
        graphics.fill(x + 3, y + 1, x + width - 9, y + height - 3,
                HudAnimUtil.withAlpha(theme, (int) ((0x06 + hover * 0x08) * effectiveAlpha)));
        graphics.fill(x + 2, y + height - 3, x + width - 8, y + height - 2,
                HudAnimUtil.withAlpha(theme, (int) (0x25 * effectiveAlpha)));
        HudRenderUtil.drawCyberneticEdge(graphics, x + 2, y, height - 2,
                theme, (int) (0xC0 * effectiveAlpha));

        drawChevron(graphics, x + 13, y + (height - 2) / 2, expansion, textColor);
        Font font = screen.getFont();
        String count = Integer.toString(row.guides().size());
        boolean hasUnread = row.guides().stream()
                .anyMatch(guide -> !ClientGuideCache.INSTANCE.isSeen(guide.getId()));
        int textX = x + 26;
        int countX = x + width - (hasUnread ? 28 : 16) - font.width(count);
        var title = StyledTextUtil.fitSingleLine(
                font, row.group().getDisplayName(), Math.max(1, countX - textX - 8));
        int textY = (int) (y + (height - font.lineHeight) / 2f - 0.5f);
        graphics.drawString(font, title, textX, textY, textColor, false);
        graphics.drawString(font, count, countX, textY, textColor, false);
        if (hasUnread) {
            HudRenderUtil.drawBreathingRedDot(graphics, x + width - 14,
                    y + height / 2, effectiveAlpha);
        }
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
        drawLine(graphics, leftX, leftY, middleX, middleY, color);
        drawLine(graphics, middleX, middleY, rightX, rightY, color);
    }

    private void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int step = 0; step <= steps; step++) {
            float progress = steps == 0 ? 0f : (float) step / steps;
            int x = Math.round(x1 + (x2 - x1) * progress);
            int y = Math.round(y1 + (y2 - y1) * progress);
            graphics.fill(x, y, x + 2, y + 2, color);
        }
    }

    private float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }
}
