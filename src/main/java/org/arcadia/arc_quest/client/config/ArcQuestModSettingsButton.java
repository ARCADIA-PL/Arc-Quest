package org.arcadia.arc_quest.client.config;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ArcQuestModSettingsButton {
    private static final Component TEXT =
            Component.translatable("gui.arc_quest.mod_config.button");

    public void renderAt(GuiGraphics graphics, Font font, int x,
                         int mouseX, int mouseY, int accentColor) {
        ArcQuestTopBarButtonRenderer.render(graphics, font, TEXT, x,
                mouseX, mouseY, accentColor, withAlpha(accentColor, 225), false);
    }

    public boolean mouseClickedAt(Screen parent, double mouseX, double mouseY,
                                  int button, int x) {
        if (button != 0 || parent.getMinecraft() == null) return false;
        if (!ArcQuestTopBarButtonRenderer.bounds(parent.getMinecraft().font, TEXT, x)
                .contains(mouseX, mouseY)) return false;
        parent.getMinecraft().setScreen(new ArcQuestModConfigScreen(parent));
        return true;
    }

    public int width(Font font) {
        return ArcQuestTopBarButtonRenderer.width(font, TEXT);
    }

    public int defaultX() {
        return ArcQuestTopBarButtonRenderer.MARGIN;
    }

    public int gap() {
        return ArcQuestTopBarButtonRenderer.GAP;
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }
}
