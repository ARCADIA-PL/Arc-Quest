package org.arcadia.arc_quest.client.config;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ArcQuestTextSettingsButton {
    private static final int Y = 8;
    private static final int WIDTH = 92;
    private static final int HEIGHT = 18;
    private static final float SCALE = 1.08f;
    private static final long PULSE_DURATION_MS = 3600L;

    private final ArcQuestTextTarget target;
    private final long createdAt = Util.getMillis();
    private boolean pulseDismissed;

    public ArcQuestTextSettingsButton(ArcQuestTextTarget target) {
        this.target = target;
    }

    public void render(GuiGraphics graphics, Font font, int screenWidth, int mouseX, int mouseY, int accentColor) {
        int x = buttonX(screenWidth);
        boolean hovered = contains(mouseX, mouseY, x, Y, WIDTH, HEIGHT);
        float pulse = pulseAmount();
        int color = hovered ? 0xFFFFFFFF : withAlpha(accentColor, 225);
        if (pulse > 0f) color = withAlpha(0xFFFFFF, Math.round(170 + pulse * 85));

        graphics.pose().pushPose();
        graphics.pose().translate(x, Y, 0);
        graphics.pose().scale(SCALE, SCALE, 1f);
        graphics.drawString(font, buttonText(), 0, 2, color, true);
        graphics.pose().popPose();
    }

    public boolean mouseClicked(Screen parent, double mouseX, double mouseY, int button) {
        if (button != 0 || parent.getMinecraft() == null) return false;
        int screenWidth = parent.getMinecraft().getWindow().getGuiScaledWidth();
        if (!contains(mouseX, mouseY, buttonX(screenWidth), Y, WIDTH, HEIGHT)) return false;
        pulseDismissed = true;
        parent.getMinecraft().setScreen(new ArcQuestTextConfigScreen(parent, target));
        return true;
    }

    private Component buttonText() {
        return Component.literal("【")
                .append(Component.translatable("gui.arc_quest.text_config.button"))
                .append("】");
    }

    private int buttonX(int screenWidth) {
        return target == ArcQuestTextTarget.DIALOGUE ? screenWidth - WIDTH - 10 : 8;
    }

    private float pulseAmount() {
        if (pulseDismissed) return 0f;
        long elapsed = Util.getMillis() - createdAt;
        if (elapsed < 0 || elapsed >= PULSE_DURATION_MS) return 0f;
        double phase = elapsed / 1000.0 * Math.PI * 2.0;
        float envelope = 1f - elapsed / (float) PULSE_DURATION_MS;
        return (float) ((0.5 + 0.5 * Math.sin(phase * 1.2)) * envelope);
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }
}
