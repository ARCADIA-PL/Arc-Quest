package org.arcadia.arc_quest.client.config;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.config.ArcQuestTextConfig;

public final class ArcQuestTextSettingsOverlay {
    public enum Target {
        DIALOGUE,
        JOURNAL,
        GUIDE,
        SHOP
    }

    private static final int BUTTON_Y = 8;
    private static final int BUTTON_SIZE = 16;
    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 104;
    private static final double STEP = 0.1;
    private static final long FIRST_OPEN_PULSE_MS = 3600L;

    private final Target target;
    private final long createdAt = Util.getMillis();
    private boolean open;
    private boolean pulseDismissed;

    public ArcQuestTextSettingsOverlay(Target target) {
        this.target = target;
    }

    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, int mouseX, int mouseY, int accentColor) {
        int buttonX = buttonX(screenWidth);
        boolean hovered = contains(mouseX, mouseY, buttonX, BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE);
        float pulse = pulseAmount();
        if (pulse > 0f && !open) {
            graphics.fill(buttonX + 1, BUTTON_Y + 1, buttonX + BUTTON_SIZE - 1, BUTTON_Y + BUTTON_SIZE - 1,
                    withAlpha(accentColor, Math.round(70 + pulse * 100)));
        }
        int iconColor = hovered || open ? 0xFFFFFFFF : withAlpha(accentColor, 220);
        graphics.drawString(font, "⚙", buttonX, BUTTON_Y + 2, iconColor, true);

        if (!open) return;

        int panelX = (screenWidth - PANEL_WIDTH) / 2;
        int panelY = (screenHeight - PANEL_HEIGHT) / 2;
        int border = withAlpha(accentColor, 230);
        graphics.fill(0, 0, screenWidth, screenHeight, 0xB8000000);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xF20D1218);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, border);
        graphics.fill(panelX, panelY + PANEL_HEIGHT - 1, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, border);
        graphics.fill(panelX, panelY, panelX + 1, panelY + PANEL_HEIGHT, border);
        graphics.fill(panelX + PANEL_WIDTH - 1, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, border);

        Component label = Component.translatable(labelKey());
        graphics.drawString(font, label, panelX + 16, panelY + 14, 0xFFFFFFFF, true);
        String value = percent() + "%";
        graphics.drawCenteredString(font, value, panelX + PANEL_WIDTH / 2, panelY + 34, accentColor);

        drawAction(graphics, font, panelX + 16, panelY + 66, 48, "−", contains(mouseX, mouseY, panelX + 16, panelY + 66, 48, 20));
        drawAction(graphics, font, panelX + 72, panelY + 66, 116, Component.translatable("gui.arc_quest.text_config.restore_defaults").getString(),
                contains(mouseX, mouseY, panelX + 72, panelY + 66, 116, 20));
        drawAction(graphics, font, panelX + 196, panelY + 66, 48, "+", contains(mouseX, mouseY, panelX + 196, panelY + 66, 48, 20));
    }

    public boolean mouseClicked(Screen screen, double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (contains(mouseX, mouseY, buttonX(screenWidth(screen)), BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            pulseDismissed = true;
            open = !open;
            return true;
        }
        if (!open) return false;
        int panelX = screenWidth(screen);
        int panelLeft = (panelX - PANEL_WIDTH) / 2;
        int panelTop = (screen.getMinecraft() == null ? PANEL_HEIGHT : screen.getMinecraft().getWindow().getGuiScaledHeight() - PANEL_HEIGHT) / 2;
        if (!contains(mouseX, mouseY, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT)) {
            open = false;
            return true;
        }
        if (contains(mouseX, mouseY, panelLeft + 16, panelTop + 66, 48, 20)) {
            setScale(Math.max(ArcQuestTextConfig.MIN_SCALE, scale() - STEP));
            return true;
        }
        if (contains(mouseX, mouseY, panelLeft + 72, panelTop + 66, 116, 20)) {
            setScale(defaultScale());
            return true;
        }
        if (contains(mouseX, mouseY, panelLeft + 196, panelTop + 66, 48, 20)) {
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
            case SHOP -> "gui.arc_quest.text_config.shop";
        };
    }

    private double scale() {
        return switch (target) {
            case DIALOGUE -> ArcQuestTextConfig.dialogueScale();
            case JOURNAL -> ArcQuestTextConfig.journalScale();
            case GUIDE -> ArcQuestTextConfig.guideScale();
            case SHOP -> ArcQuestTextConfig.shopScale();
        };
    }

    private double defaultScale() {
        return switch (target) {
            case DIALOGUE -> ArcQuestTextConfig.DIALOGUE_SCALE.getDefault();
            case JOURNAL -> ArcQuestTextConfig.JOURNAL_SCALE.getDefault();
            case GUIDE -> ArcQuestTextConfig.GUIDE_SCALE.getDefault();
            case SHOP -> ArcQuestTextConfig.SHOP_SCALE.getDefault();
        };
    }

    private void setScale(double value) {
        switch (target) {
            case DIALOGUE -> ArcQuestTextConfig.DIALOGUE_SCALE.set(value);
            case JOURNAL -> ArcQuestTextConfig.JOURNAL_SCALE.set(value);
            case GUIDE -> ArcQuestTextConfig.GUIDE_SCALE.set(value);
            case SHOP -> ArcQuestTextConfig.SHOP_SCALE.set(value);
        }
        ArcQuestTextConfig.save();
    }

    private float pulseAmount() {
        if (pulseDismissed) return 0f;
        long elapsed = Util.getMillis() - createdAt;
        if (elapsed < 0 || elapsed >= FIRST_OPEN_PULSE_MS) return 0f;
        double phase = elapsed / 1000.0 * Math.PI * 2.0;
        float envelope = 1f - elapsed / (float) FIRST_OPEN_PULSE_MS;
        return (float) ((0.5 + 0.5 * Math.sin(phase * 1.2)) * envelope);
    }

    private int buttonX(int screenWidth) {
        return target == Target.DIALOGUE && screenWidth > 0 ? screenWidth - BUTTON_SIZE - 10 : 8;
    }

    private static int screenWidth(Screen screen) {
        return screen.getMinecraft() == null ? 0 : screen.getMinecraft().getWindow().getGuiScaledWidth();
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }
}
