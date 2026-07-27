package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

public final class GuideSplashOverlay implements LayeredDraw.Layer {

    public static final GuideSplashOverlay INSTANCE = new GuideSplashOverlay();

    private GuideSplashOverlay() {
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (GuideSplashRenderer.isActive()) {
            GuideSplashRenderer.render(graphics, graphics.guiWidth());
        }
    }
}
