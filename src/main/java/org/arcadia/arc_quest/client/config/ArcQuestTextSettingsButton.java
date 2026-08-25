package org.arcadia.arc_quest.client.config;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ArcQuestTextSettingsButton {
    private static final int Y = 6;
    private static final int HORIZONTAL_MARGIN = 8;
    private static final float SCALE = 0.86f;
    private static final long PULSE_DURATION_MS = 3600L;

    private final ArcQuestTextTarget target;
    private final long createdAt = Util.getMillis();
    private boolean pulseDismissed;

    public ArcQuestTextSettingsButton(ArcQuestTextTarget target) {
        this.target = target;
    }

    public void render(GuiGraphics graphics, Font font, int screenWidth, int mouseX, int mouseY, int accentColor) {
        Geometry geometry = geometry(font, screenWidth);
        boolean hovered = geometry.contains(mouseX, mouseY);
        float pulse = pulseAmount();
        int color = hovered ? 0xFFFFFFFF : withAlpha(accentColor, 225);
        if (pulse > 0f) color = withAlpha(0xFFFFFF, Math.round(170 + pulse * 85));

        graphics.pose().pushPose();
        graphics.pose().translate(geometry.x(), geometry.y(), 0);
        graphics.pose().scale(SCALE, SCALE, 1f);
        graphics.drawString(font, buttonText(), 0, 1, color, true);
        graphics.pose().popPose();
    }

    public boolean mouseClicked(Screen parent, double mouseX, double mouseY, int button) {
        if (button != 0 || parent.getMinecraft() == null) return false;
        Font font = parent.getMinecraft().font;
        int screenWidth = parent.getMinecraft().getWindow().getGuiScaledWidth();
        if (!geometry(font, screenWidth).contains(mouseX, mouseY)) return false;
        pulseDismissed = true;
        parent.getMinecraft().setScreen(new ArcQuestTextConfigScreen(parent, target));
        return true;
    }

    private Component buttonText() {
        return Component.literal("【")
                .append(Component.translatable("gui.arc_quest.text_config.button"))
                .append("】");
    }

    private Geometry geometry(Font font, int screenWidth) {
        int width = Math.max(1, (int) Math.ceil(font.width(buttonText()) * SCALE));
        int height = Math.max(1, (int) Math.ceil(font.lineHeight * SCALE)) + 4;
        int x = target == ArcQuestTextTarget.DIALOGUE
                ? screenWidth - width - HORIZONTAL_MARGIN
                : HORIZONTAL_MARGIN;
        return new Geometry(x, Y, width, height);
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

    private record Geometry(int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
