package org.arcadia.arc_quest.client.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.config.ArcQuestTrackerConfig;

/** Toggles only the local overlay visibility, preserving the authoritative tracked quest. */
public final class ArcQuestTrackerToggleButton {
    public void renderAt(QuestJournalScreen screen, GuiGraphics graphics, int x,
                         int mouseX, int mouseY, int theme, float opacity) {
        boolean enabled = ArcQuestTrackerConfig.enabled();
        Component tooltip = Component.translatable(enabled
                ? "arc_quest.gui.journal.tracker_hide" : "arc_quest.gui.journal.tracker_show");
        ArcQuestTopBarButtonRenderer.renderJournalIcon(screen, graphics, ArcQuestTopBarButtonRenderer.Icon.TRACKER,
                tooltip, x, mouseX, mouseY, theme, opacity, enabled);
    }

    public boolean mouseClickedAt(double mouseX, double mouseY, int button, int x) {
        if (button != 0 || !ArcQuestTopBarButtonRenderer.iconBounds(x).contains(mouseX, mouseY)) return false;
        ArcQuestTrackerConfig.setEnabled(!ArcQuestTrackerConfig.enabled());
        return true;
    }
}
