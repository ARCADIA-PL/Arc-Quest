package org.arcadia.arc_quest.mutil.screen;

import net.minecraft.client.gui.GuiGraphics;

public final class ArcScissorUtil {
    private ArcScissorUtil() {
    }

    public static void enable(GuiGraphics graphics, float scale, int x1, int y1, int x2, int y2) {
        graphics.enableScissor(
                Math.round(x1 * scale),
                Math.round(y1 * scale),
                Math.round(x2 * scale),
                Math.round(y2 * scale)
        );
    }

    public static void disable(GuiGraphics graphics) {
        graphics.disableScissor();
    }
}
