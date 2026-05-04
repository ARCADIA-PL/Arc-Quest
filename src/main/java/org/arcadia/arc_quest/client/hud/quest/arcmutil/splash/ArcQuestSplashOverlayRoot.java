package org.arcadia.arc_quest.client.hud.quest.arcmutil.splash;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.overlay.ArcOverlayRoot;

public class ArcQuestSplashOverlayRoot extends ArcOverlayRoot {
    private final ArcQuestSplashElement splashElement = new ArcQuestSplashElement();

    public ArcQuestSplashOverlayRoot(Minecraft minecraft) {
        super(minecraft, "arc_quest_splash");
        addChild(splashElement);
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!ArcQuestSplashManager.isActive()) return;
        splashElement.draw(graphics, context, refX, refY, inheritedOpacity);
    }
}
