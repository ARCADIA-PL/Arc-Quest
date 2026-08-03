package org.arcadia.arc_quest.client.hud.quest.journal.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class JournalScaledTextRenderer {
    private JournalScaledTextRenderer() {
    }

    public static void draw(GuiGraphics graphics, Font font, String text, float x, float y,
                            float scale, int color, boolean shadow) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, text, 0, 0, color, shadow);
        graphics.pose().popPose();
    }
}
