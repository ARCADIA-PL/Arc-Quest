package org.com.arc_quest.client.gui.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class QuestSplashOverlay implements IGuiOverlay {
    public static final QuestSplashOverlay INSTANCE = new QuestSplashOverlay();

    private QuestSplashOverlay() {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (QuestSplashRenderer.isActive()) {
            QuestSplashRenderer.render(guiGraphics, partialTick, screenWidth, screenHeight);
        }
    }
}