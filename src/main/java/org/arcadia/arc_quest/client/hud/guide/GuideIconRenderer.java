package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.guide.api.GuideVisualConfig;

/** Renders a guide's declared icon without intercepting unrelated GuiGraphics operations. */
final class GuideIconRenderer {
    private GuideIconRenderer() {}

    static void draw(GuiGraphics graphics, GuideVisualConfig visual, int x, int y) {
        if (visual.getIconMedia() != null)
            GuideImageRenderer.draw(graphics, visual.getIconMedia(), x, y, 16, 16, GuideImageLayout.Fit.CONTAIN, 1);
        else graphics.renderItem(visual.getIcon(), x, y);
    }
}
