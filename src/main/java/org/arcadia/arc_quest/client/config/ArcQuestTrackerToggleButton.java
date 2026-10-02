package org.arcadia.arc_quest.client.config;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.config.ArcQuestTrackerConfig;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import java.util.List;

/** Toggles only the local overlay visibility, preserving the authoritative tracked quest. */
public final class ArcQuestTrackerToggleButton {
    private static final Component ICON_SPACE = Component.literal(" ");
    private Component text() {
        return Component.translatable(ArcQuestTrackerConfig.enabled()
                ? "arc_quest.gui.journal.tracker_hide" : "arc_quest.gui.journal.tracker_show");
    }

    public void renderAt(QuestJournalScreen screen, GuiGraphics graphics, Font font, int x, int mouseX, int mouseY, int theme) {
        renderAt(screen, graphics, font, x, mouseX, mouseY, theme, 1f);
    }

    public void renderAt(QuestJournalScreen screen, GuiGraphics graphics, Font font, int x, int mouseX, int mouseY, int theme, float opacity) {
        if (!Float.isFinite(opacity) || opacity <= 3f / 255f) return;
        boolean hovered = ArcQuestTopBarButtonRenderer.bounds(font, ICON_SPACE, x).contains(mouseX, mouseY);
        ArcQuestTopBarButtonRenderer.render(graphics, font, ICON_SPACE, x, mouseX, mouseY, theme,
                0xE1000000 | (theme & 0xFFFFFF), ArcQuestTrackerConfig.enabled(), opacity);
        int color = ArcQuestTopBarButtonRenderer.fade((hovered ? 0xFF000000 : 0xDD000000)
                | (ArcQuestTrackerConfig.enabled() ? theme & 0xFFFFFF : 0x999999), opacity);
        int iconX = x + 6, iconY = ArcQuestTopBarButtonRenderer.Y + 5;
        graphics.fill(iconX, iconY, iconX + 2, iconY + 9, color);
        graphics.fill(iconX + 5, iconY, iconX + 15, iconY + 1, color);
        graphics.fill(iconX + 5, iconY + 4, iconX + 13, iconY + 5, color);
        graphics.fill(iconX + 5, iconY + 8, iconX + 10, iconY + 9, color);
        HudCursorManager.requestPointer(hovered);
        if (hovered) screen.setHoveredCustomTooltip(List.of(text()));
    }

    public boolean mouseClickedAt(Font font, double mouseX, double mouseY, int button, int x) {
        if (button != 0 || !ArcQuestTopBarButtonRenderer.bounds(font, ICON_SPACE, x).contains(mouseX, mouseY)) return false;
        ArcQuestTrackerConfig.setEnabled(!ArcQuestTrackerConfig.enabled());
        return true;
    }
}
