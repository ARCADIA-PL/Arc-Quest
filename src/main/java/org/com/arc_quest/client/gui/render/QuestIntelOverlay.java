package org.com.arc_quest.client.gui.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class QuestIntelOverlay implements IGuiOverlay {
    public static final QuestIntelOverlay INSTANCE = new QuestIntelOverlay();

    private QuestIntelOverlay() {}

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (QuestIntelRenderer.isActive()) {
            QuestIntelRenderer.render(guiGraphics, partialTick, screenWidth, screenHeight);
        }
    }
}