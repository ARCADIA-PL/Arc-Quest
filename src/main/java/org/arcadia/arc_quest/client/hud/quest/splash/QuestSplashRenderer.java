package org.arcadia.arc_quest.client.hud.quest.splash;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.splash.ArcQuestSplashManager;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.SplashType;

public class QuestSplashRenderer {
    public static void trigger(QuestDefinition quest, SplashType type, ResourceLocation texture) {
        ArcQuestSplashManager.trigger(quest, type, texture);
    }

    public static boolean isActive() {
        return ArcQuestSplashManager.isActive();
    }

    public static boolean mouseClicked() {
        return ArcQuestSplashManager.mouseClicked();
    }

    public static void render(GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
    }
}
