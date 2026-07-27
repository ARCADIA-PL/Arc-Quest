package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public final class GuideSplashOverlay implements IGuiOverlay {

    public static final GuideSplashOverlay INSTANCE = new GuideSplashOverlay();

    private GuideSplashOverlay() {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick,
                       int screenWidth, int screenHeight) {
        if (GuideSplashRenderer.isActive()) {
            GuideSplashRenderer.render(graphics, screenWidth);
        }
    }
}
