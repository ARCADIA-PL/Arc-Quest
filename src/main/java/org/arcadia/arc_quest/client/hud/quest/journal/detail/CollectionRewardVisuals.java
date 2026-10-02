package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.StyledTextUtil;
import org.arcadia.arc_quest.client.hud.component.HudRect;

/** Shared, stationary reward attention marks and translucent primary actions. */
final class CollectionRewardVisuals {
    private CollectionRewardVisuals() {}

    static void dot(GuiGraphics graphics, int centerX, int centerY, int alpha) {
        if (alpha < 4) return;
        int ink = HudAnimUtil.withAlpha(0xED6D73, alpha);
        graphics.fill(centerX - 1, centerY - 2, centerX + 2, centerY + 3, ink);
        graphics.fill(centerX - 2, centerY - 1, centerX + 3, centerY + 2, ink);
    }

    static void claimButton(GuiGraphics graphics, Font font, HudRect bounds, Component label,
                            boolean hovered, int theme, int alpha) {
        if (alpha < 4) return;
        int tint = HudAnimUtil.lerpColor(theme, 0xFFFFFF, hovered ? .26f : .12f);
        CollectionJournalVisuals.softRect(graphics, bounds,
                HudAnimUtil.withAlpha(tint, Math.round(alpha * (hovered ? .62f : .46f))));
        int edge = HudAnimUtil.withAlpha(tint, Math.round(alpha * .9f));
        graphics.fill(bounds.x() + 2, bounds.y(), bounds.right() - 2, bounds.y() + 1, edge);
        graphics.fill(bounds.x() + 2, bounds.bottom() - 1, bounds.right() - 2, bounds.bottom(), edge);
        graphics.fill(bounds.x(), bounds.y() + 2, bounds.x() + 1, bounds.bottom() - 2, edge);
        graphics.fill(bounds.right() - 1, bounds.y() + 2, bounds.right(), bounds.bottom() - 2, edge);
        var fitted = StyledTextUtil.fitSingleLine(font, label, Math.max(1, bounds.width() - 12));
        graphics.drawString(font, fitted, bounds.x() + (bounds.width() - font.width(fitted)) / 2,
                bounds.y() + (bounds.height() - font.lineHeight) / 2,
                HudAnimUtil.withAlpha(0xFFFFFF, alpha), false);
    }
}
