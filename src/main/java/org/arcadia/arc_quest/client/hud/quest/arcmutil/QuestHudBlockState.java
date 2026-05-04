package org.arcadia.arc_quest.client.hud.quest.arcmutil;

import net.minecraft.client.Minecraft;
import org.arcadia.arc_quest.client.hud.dialogue.DialogueScreen;
import org.arcadia.arc_quest.client.hud.gacha.GachaResultRenderer;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.splash.ArcQuestSplashManager;
import org.arcadia.arc_quest.client.hud.shop.AbstractTradeScreen;

public record QuestHudBlockState(
        boolean hideAll,
        boolean freezeToasts,
        boolean hideTracker,
        boolean hideNonBlockingToasts,
        boolean blockInput
) {
    public static QuestHudBlockState current() {
        Minecraft mc = Minecraft.getInstance();
        boolean hideAll = mc.player == null || mc.options.hideGui;
        boolean splash = ArcQuestSplashManager.isActive();
        boolean blockingScreen = mc.screen instanceof QuestJournalScreen
                || mc.screen instanceof DialogueScreen
                || mc.screen instanceof AbstractTradeScreen
                || GachaResultRenderer.INSTANCE.isActive();
        boolean freezeToasts = splash || mc.screen instanceof QuestJournalScreen || mc.screen instanceof DialogueScreen;
        return new QuestHudBlockState(hideAll, freezeToasts, splash || blockingScreen, splash || blockingScreen, blockingScreen);
    }
}
