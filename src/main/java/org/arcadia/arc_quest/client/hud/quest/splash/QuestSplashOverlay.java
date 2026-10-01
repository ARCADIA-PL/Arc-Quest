package org.arcadia.arc_quest.client.hud.quest.splash;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.DeltaTracker;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;

public class QuestSplashOverlay implements LayeredDraw.Layer {
    public static final QuestSplashOverlay INSTANCE = new QuestSplashOverlay();

    private QuestSplashOverlay() {
    }

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        if (Minecraft.getInstance().screen != null) return;
        if (QuestSplashRenderer.isActive()) {
            HudCursorManager.renderHud(() ->
                    QuestSplashRenderer.render(guiGraphics, partialTick, screenWidth, screenHeight));
        }
    }
}
