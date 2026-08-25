package org.arcadia.arc_quest.client.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ArcQuestTextSettingsButton {
    private static final int X = 8;
    private static final int Y = 8;
    private static final int WIDTH = 96;
    private static final int HEIGHT = 20;

    private ArcQuestTextSettingsButton() {
    }

    public static void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        boolean hovered = contains(mouseX, mouseY);
        int background = hovered ? 0xCC263443 : 0x99202A35;
        int border = hovered ? 0xFF76C8FF : 0xAA6E8295;
        graphics.fill(X, Y, X + WIDTH, Y + HEIGHT, background);
        graphics.fill(X, Y, X + WIDTH, Y + 1, border);
        graphics.fill(X, Y + HEIGHT - 1, X + WIDTH, Y + HEIGHT, border);
        graphics.fill(X, Y, X + 1, Y + HEIGHT, border);
        graphics.fill(X + WIDTH - 1, Y, X + WIDTH, Y + HEIGHT, border);
        graphics.drawString(font, Component.translatable("gui.arc_quest.text_config.button"), X + 7, Y + 6,
                hovered ? 0xFFFFFFFF : 0xFFD4DCE5, false);
    }

    public static boolean mouseClicked(Screen parent, double mouseX, double mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY)) return false;
        if (parent.getMinecraft() != null) parent.getMinecraft().setScreen(new ArcQuestTextConfigScreen(parent));
        return true;
    }

    private static boolean contains(double mouseX, double mouseY) {
        return mouseX >= X && mouseX < X + WIDTH && mouseY >= Y && mouseY < Y + HEIGHT;
    }
}
