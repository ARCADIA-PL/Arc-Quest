package org.arcadia.arc_quest.mutil.screen;

import net.minecraft.client.gui.GuiGraphics;

public final class ArcScreenInput {
    private ArcScreenInput() {
    }

    public static int toScaledMouseX(double mouseX, float uiScale) {
        return Math.round((float) mouseX / Math.max(0.1f, uiScale));
    }

    public static int toScaledMouseY(double mouseY, float uiScale) {
        return Math.round((float) mouseY / Math.max(0.1f, uiScale));
    }

    public static void beginScaled(GuiGraphics graphics, float uiScale) {
        graphics.pose().pushPose();
        graphics.pose().scale(uiScale, uiScale, 1.0f);
    }

    public static void endScaled(GuiGraphics graphics) {
        graphics.pose().popPose();
    }
}
