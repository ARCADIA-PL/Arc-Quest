package org.arcadia.arc_quest.client.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;

public final class ArcQuestModSettingsButton {
    private static final Component TEXT =
            Component.translatable("gui.arc_quest.mod_config.button");

    public void renderAt(QuestJournalScreen screen, GuiGraphics graphics, int x,
                         int mouseX, int mouseY, int accentColor, float opacity) {
        ArcQuestTopBarButtonRenderer.renderJournalIcon(screen, graphics, ArcQuestTopBarButtonRenderer.Icon.SETTINGS,
                TEXT, x, mouseX, mouseY, accentColor, opacity, true);
    }

    public boolean mouseClickedAt(Screen parent, double mouseX, double mouseY,
                                  int button, int x) {
        if (button != 0 || parent.getMinecraft() == null) return false;
        if (!ArcQuestTopBarButtonRenderer.iconBounds(x)
                .contains(mouseX, mouseY)) return false;
        parent.getMinecraft().setScreen(new ArcQuestModConfigScreen(parent));
        return true;
    }

    public int width() {
        return ArcQuestTopBarButtonRenderer.ICON_WIDTH;
    }

    public int defaultX() {
        return ArcQuestTopBarButtonRenderer.MARGIN;
    }

    public int gap() {
        return ArcQuestTopBarButtonRenderer.GAP;
    }

}
