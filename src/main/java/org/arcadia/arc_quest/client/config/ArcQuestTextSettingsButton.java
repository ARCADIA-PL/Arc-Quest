package org.arcadia.arc_quest.client.config;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ArcQuestTextSettingsButton {
    private static final long PULSE_DURATION_MS = 3600L;

    private final ArcQuestTextTarget target;
    private final long createdAt = Util.getMillis();
    private boolean pulseDismissed;

    public ArcQuestTextSettingsButton(ArcQuestTextTarget target) {
        this.target = target;
    }

    public void render(GuiGraphics graphics, Font font, int screenWidth, int mouseX, int mouseY, int accentColor) {
        renderAt(graphics, font, defaultX(font, screenWidth), mouseX, mouseY, accentColor);
    }

    public void renderAt(GuiGraphics graphics, Font font, int x,
                         int mouseX, int mouseY, int accentColor) {
        float pulse = pulseAmount();
        int color = withAlpha(accentColor, 225);
        if (pulse > 0f) color = withAlpha(0xFFFFFF, Math.round(170 + pulse * 85));
        ArcQuestTopBarButtonRenderer.render(graphics, font, buttonText(), x,
                mouseX, mouseY, accentColor, color, pulse > 0f);
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
        Font font = parent.getMinecraft().font;
        if (!ArcQuestTopBarButtonRenderer.bounds(font, buttonText(), x)
                .contains(mouseX, mouseY)) return false;
        pulseDismissed = true;
        parent.getMinecraft().setScreen(new ArcQuestTextConfigScreen(parent, target));
        return true;
    }

    public int width(Font font) {
        return ArcQuestTopBarButtonRenderer.width(font, buttonText());
    }

    private Component buttonText() {
        return Component.translatable("gui.arc_quest.text_config.button");
    }

    private int defaultX(Font font, int screenWidth) {
        return target == ArcQuestTextTarget.DIALOGUE
                ? screenWidth - width(font) - ArcQuestTopBarButtonRenderer.MARGIN
                : ArcQuestTopBarButtonRenderer.MARGIN;
    }

    private float pulseAmount() {
        if (pulseDismissed) return 0f;
        long elapsed = Util.getMillis() - createdAt;
        if (elapsed < 0 || elapsed >= PULSE_DURATION_MS) return 0f;
        double phase = elapsed / 1000.0 * Math.PI * 2.0;
        float envelope = 1f - elapsed / (float) PULSE_DURATION_MS;
        return (float) ((0.5 + 0.5 * Math.sin(phase * 1.2)) * envelope);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }
}
