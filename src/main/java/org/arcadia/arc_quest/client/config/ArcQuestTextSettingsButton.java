package org.arcadia.arc_quest.client.config;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;

public final class ArcQuestTextSettingsButton {
    private final ArcQuestTextTarget target;

    public ArcQuestTextSettingsButton(ArcQuestTextTarget target) {
        this.target = target;
    }

    public void render(GuiGraphics graphics, Font font, int screenWidth, int mouseX, int mouseY, int accentColor) {
        renderAt(graphics, font, defaultX(font, screenWidth), mouseX, mouseY, accentColor);
    }

    public void renderAt(GuiGraphics graphics, Font font, int x,
                         int mouseX, int mouseY, int accentColor) {
        renderAt(graphics, font, x, mouseX, mouseY, accentColor, 1f);
    }

    public void renderAt(GuiGraphics graphics, Font font, int x,
                         int mouseX, int mouseY, int accentColor, float opacity) {
        if (ArcQuestTopBarButtonRenderer.drawIcon(graphics, ArcQuestTopBarButtonRenderer.Icon.TEXT_SIZE,
                x, mouseX, mouseY, accentColor, opacity, true)) {
            HudCursorManager.requestPointer(true);
            graphics.renderTooltip(font, buttonText(), mouseX, mouseY);
        }
    }

    public static boolean drawIcon(GuiGraphics graphics, HudRect bounds, boolean hovered, int theme, float opacity) {
        return ArcQuestTopBarButtonRenderer.drawIcon(graphics, ArcQuestTopBarButtonRenderer.Icon.TEXT_SIZE,
                bounds, hovered, theme, opacity, true);
    }

    public void renderJournal(QuestJournalScreen screen, GuiGraphics graphics, int x,
                              int mouseX, int mouseY, int accentColor, float opacity) {
        ArcQuestTopBarButtonRenderer.renderJournalIcon(screen, graphics, ArcQuestTopBarButtonRenderer.Icon.TEXT_SIZE,
                buttonText(), x, mouseX, mouseY, accentColor, opacity, true);
    }

    public boolean mouseClicked(Screen parent, double mouseX, double mouseY, int button) {
        if (button != 0 || parent.getMinecraft() == null) return false;
        Font font = parent.getMinecraft().font;
        int screenWidth = parent.getMinecraft().getWindow().getGuiScaledWidth();
        return mouseClickedAt(parent, mouseX, mouseY, button,
                defaultX(font, screenWidth));
    }

    public boolean mouseClickedAt(Screen parent, double mouseX, double mouseY,
                                  int button, int x) {
        if (button != 0 || parent.getMinecraft() == null) return false;
        if (!ArcQuestTopBarButtonRenderer.iconBounds(x).contains(mouseX, mouseY)) return false;
        parent.getMinecraft().setScreen(new ArcQuestTextConfigScreen(parent, target));
        return true;
    }

    public int width(Font font) {
        return ArcQuestTopBarButtonRenderer.ICON_WIDTH;
    }

    private Component buttonText() {
        return Component.translatable("gui.arc_quest.text_config.button");
    }

    public int defaultX(Font font, int screenWidth) {
        return target == ArcQuestTextTarget.DIALOGUE
                ? screenWidth - width(font) - ArcQuestTopBarButtonRenderer.MARGIN
                : ArcQuestTopBarButtonRenderer.MARGIN;
    }

}
