package org.arcadia.arc_quest.client.hud.quest.journal.component;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;

public final class JournalUnreadBadgeRenderer {
    private JournalUnreadBadgeRenderer() {
    }

    public static void draw(GuiGraphics graphics, int centerX, int centerY, int alpha) {
        if (alpha > 8) HudRenderUtil.drawBreathingRedDot(graphics, centerX, centerY, alpha / 255f);
    }
}
