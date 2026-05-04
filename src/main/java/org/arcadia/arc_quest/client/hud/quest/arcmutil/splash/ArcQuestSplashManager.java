package org.arcadia.arc_quest.client.hud.quest.arcmutil.splash;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.SplashType;

public final class ArcQuestSplashManager {
    private static ArcQuestSplashViewModel active;

    private ArcQuestSplashManager() {
    }

    public static void trigger(QuestDefinition quest, SplashType type, ResourceLocation texture) {
        if (quest == null || texture == null) return;
        active = new ArcQuestSplashViewModel(quest, type, texture, Util.getMillis());
    }

    public static boolean isActive() {
        return active != null;
    }

    public static boolean mouseClicked() {
        if (active == null || active.state == ArcQuestSplashViewModel.State.EXIT) return false;
        if (active.state == ArcQuestSplashViewModel.State.ENTER) return false;
        active.state = ArcQuestSplashViewModel.State.EXIT;
        active.exitStartTime = Util.getMillis();
        active.skipStartX = active.lastRenderX;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.5f));
        return true;
    }

    public static ArcQuestSplashViewModel active() {
        return active;
    }

    public static void clear(ArcQuestSplashViewModel model) {
        if (active == model) active = null;
    }
}
