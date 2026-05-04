package org.arcadia.arc_quest.mutil.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;

public final class ArcScissorUtil {
    private ArcScissorUtil() {
    }

    public static void enable(GuiGraphics graphics, float scale, int x1, int y1, int x2, int y2) {
        enableGui(graphics, Math.round(x1 * scale), Math.round(y1 * scale), Math.round(x2 * scale), Math.round(y2 * scale));
    }

    public static void enableGui(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        graphics.enableScissor(x1, y1, x2, y2);
    }

    public static void enableScreenAware(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof QuestJournalScreen qjs) {
            qjs.enableScissor(graphics, x1, y1, x2, y2);
        } else {
            graphics.enableScissor(x1, y1, x2, y2);
        }
    }

    public static void disable(GuiGraphics graphics) {
        graphics.disableScissor();
    }
}
