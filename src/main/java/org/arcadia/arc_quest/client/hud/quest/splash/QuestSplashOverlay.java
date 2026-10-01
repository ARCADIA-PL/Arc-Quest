package org.arcadia.arc_quest.client.hud.quest.splash;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;

public class QuestSplashOverlay implements IGuiOverlay {
    public static final QuestSplashOverlay INSTANCE = new QuestSplashOverlay();

    private QuestSplashOverlay() {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (Minecraft.getInstance().screen != null) return;
        if (QuestSplashRenderer.isActive()) {
            HudCursorManager.renderHud(() ->
                    QuestSplashRenderer.render(guiGraphics, partialTick, screenWidth, screenHeight));
        }
    }
}
