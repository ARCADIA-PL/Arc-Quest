package org.arcadia.arc_quest.client.hud.quest.arcmutil.splash;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.quest.splash.QuestSplashRenderer;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.overlay.ArcOverlayRoot;

public class ArcQuestSplashOverlayRoot extends ArcOverlayRoot {
    public ArcQuestSplashOverlayRoot(Minecraft minecraft) {
        super(minecraft, "arc_quest_splash");
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!QuestSplashRenderer.isActive()) return;
        QuestSplashRenderer.render(graphics, context.partialTick(), context.screenWidth(), context.screenHeight());
    }
}
