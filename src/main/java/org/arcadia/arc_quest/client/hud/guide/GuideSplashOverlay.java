package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.DeltaTracker;

public final class GuideSplashOverlay implements LayeredDraw.Layer {

    public static final GuideSplashOverlay INSTANCE = new GuideSplashOverlay();

    private GuideSplashOverlay() {
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        if (Minecraft.getInstance().screen != null) return;
        if (GuideSplashRenderer.isActive()) {
            GuideSplashRenderer.render(graphics, screenWidth);
        }
    }
}
