package org.arcadia.arc_quest.client.config;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.config.ArcQuestTextConfig;

public final class ArcQuestTextSettingsOverlay {
    public enum Target {
        DIALOGUE,
        JOURNAL,
        GUIDE
    }

    private static final int X = 8;
    private static final int BUTTON_Y = 8;
    private static final int BUTTON_WIDTH = 112;
    private static final int BUTTON_HEIGHT = 20;
    private static final int PANEL_X = 8;
    private static final int PANEL_Y = 34;
    private static final int PANEL_WIDTH = 222;
    private static final int PANEL_HEIGHT = 76;
    private static final double STEP = 0.1;

    private final Target target;
    private boolean open;

    public ArcQuestTextSettingsOverlay(Target target) {
        this.target = target;
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, int accentColor) {
        boolean hovered = containsButton(mouseX, mouseY);
        int border = hovered ? 0xFFFFFFFF : withAlpha(accentColor, 210);
        graphics.fill(X, BUTTON_Y, X + BUTTON_WIDTH, BUTTON_Y + 1, border);
        graphics.fill(X, BUTTON_Y + BUTTON_HEIGHT - 1, X + BUTTON_WIDTH, BUTTON_Y + BUTTON_HEIGHT, border);
        graphics.fill(X, BUTTON_Y, X + 1, BUTTON_Y + BUTTON_HEIGHT, border);
        graphics.fill(X + BUTTON_WIDTH - 1, BUTTON_Y, X + BUTTON_WIDTH, BUTTON_Y + BUTTON_HEIGHT, border);
        graphics.fill(X + 4, BUTTON_Y + 5, X + 7, BUTTON_Y + 8, border);
        graphics.drawString(font, Component.translatable("gui.arc_quest.text_config.button"), X + 13, BUTTON_Y + 6,
                hovered ? 0xFFFFFFFF : withAlpha(0xDDE6EF, 240), false);

        if (!open) return;

        graphics.fill(PANEL_X, PANEL_Y, PANEL_X + PANEL_WIDTH, PANEL_Y + PANEL_HEIGHT, 0xE610151B);
        graphics.fill(PANEL_X, PANEL_Y, PANEL_X + PANEL_WIDTH, PANEL_Y + 1, border);
        graphics.fill(PANEL_X, PANEL_Y + PANEL_HEIGHT - 1, PANEL_X + PANEL_WIDTH, PANEL_Y + PANEL_HEIGHT, border);
        graphics.fill(PANEL_X, PANEL_Y, PANEL_X + 1, PANEL_Y + PANEL_HEIGHT, border);
        graphics.fill(PANEL_X + PANEL_WIDTH - 1, PANEL_Y, PANEL_X + PANEL_WIDTH, PANEL_Y + PANEL_HEIGHT, border);

        Component label = Component.translatable(labelKey());
        graphics.drawString(font, label, PANEL_X + 10, PANEL_Y + 10, 0xFFFFFFFF, true);
        String value = percent() + "%";
        graphics.drawCenteredString(font, value, PANEL_X + PANEL_WIDTH / 2, PANEL_Y + 28, accentColor);

        drawAction(graphics, font, PANEL_X + 10, PANEL_Y + 48, 42, "-", contains(mouseX, mouseY, PANEL_X + 10, PANEL_Y + 48, 42, 18));
        drawAction(graphics, font, PANEL_X + 58, PANEL_Y + 48, 94, Component.translatable("gui.arc_quest.text_config.restore_defaults").getString(),
                contains(mouseX, mouseY, PANEL_X + 58, PANEL_Y + 48, 94, 18));
        drawAction(graphics, font, PANEL_X + 158, PANEL_Y + 48, 42, "+", contains(mouseX, mouseY, PANEL_X + 158, PANEL_Y + 48, 42, 18));
    }

    public boolean mouseClicked(Screen screen, double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (containsButton(mouseX, mouseY)) {
            open = !open;
            return true;
        }
        if (!open) return false;
        if (!contains(mouseX, mouseY, PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT)) {
            open = false;
            return true;
        }
        if (contains(mouseX, mouseY, PANEL_X + 10, PANEL_Y + 48, 42, 18)) {
            setScale(Math.max(ArcQuestTextConfig.MIN_SCALE, scale() - STEP));
            return true;
        }
        if (contains(mouseX, mouseY, PANEL_X + 58, PANEL_Y + 48, 94, 18)) {
            setScale(defaultScale());
            return true;
        }
        if (contains(mouseX, mouseY, PANEL_X + 158, PANEL_Y + 48, 42, 18)) {
            setScale(Math.min(ArcQuestTextConfig.MAX_SCALE, scale() + STEP));
            return true;
        }
        return true;
    }

    public boolean isOpen() {
        return open;
    }

    private void drawAction(GuiGraphics graphics, Font font, int x, int y, int width, String text, boolean hovered) {
        int color = hovered ? 0xFF354352 : 0xAA202A33;
        graphics.fill(x, y, x + width, y + 18, color);
        graphics.drawCenteredString(font, text, x + width / 2, y + 5, hovered ? 0xFFFFFFFF : 0xFFD4DCE5);
    }

    private int percent() {
        return (int) Math.round(scale() * 100.0);
    }

    private String labelKey() {
        return switch (target) {
            case DIALOGUE -> "gui.arc_quest.text_config.dialogue";
            case JOURNAL -> "gui.arc_quest.text_config.journal";
            case GUIDE -> "gui.arc_quest.text_config.guide";
        };
    }

    private double scale() {
        return switch (target) {
            case DIALOGUE -> ArcQuestTextConfig.dialogueScale();
            case JOURNAL -> ArcQuestTextConfig.journalScale();
            case GUIDE -> ArcQuestTextConfig.guideScale();
        };
    }

    private double defaultScale() {
        return switch (target) {
            case DIALOGUE -> ArcQuestTextConfig.DIALOGUE_SCALE.getDefault();
            case JOURNAL -> ArcQuestTextConfig.JOURNAL_SCALE.getDefault();
            case GUIDE -> ArcQuestTextConfig.GUIDE_SCALE.getDefault();
        };
    }

    private void setScale(double value) {
        switch (target) {
            case DIALOGUE -> ArcQuestTextConfig.DIALOGUE_SCALE.set(value);
            case JOURNAL -> ArcQuestTextConfig.JOURNAL_SCALE.set(value);
            case GUIDE -> ArcQuestTextConfig.GUIDE_SCALE.set(value);
        }
        ArcQuestTextConfig.save();
    }

    private boolean containsButton(double mouseX, double mouseY) {
        return contains(mouseX, mouseY, X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }
}
