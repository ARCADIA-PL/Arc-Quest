package org.arcadia.arc_quest.mutil.theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public final class ArcTooltipRenderer {
    private ArcTooltipRenderer() {
    }

    public static void render(GuiGraphics graphics, Font font, List<Component> lines, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (lines == null || lines.isEmpty()) return;
        List<FormattedCharSequence> visualLines = lines.stream().map(Component::getVisualOrderText).toList();
        graphics.renderTooltip(font, visualLines, mouseX, mouseY);
    }
}
