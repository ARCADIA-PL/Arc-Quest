package org.arcadia.arc_quest.guide.runtime;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.guide.registry.ArcQuestGuideContent;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

public final class GuideAutoTriggerService {

    private static final GuideUnlockService UNLOCK_SERVICE = new GuideUnlockService();

    private GuideAutoTriggerService() {
    }

    public static void onPlayerLogin(ServerPlayer player) {
        if (player == null) return;
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        UNLOCK_SERVICE.grant(player, ArcQuestGuideContent.JOURNAL_BASICS_GUIDE_ID);
        if (data.getAllActiveQuests().size() > 1) {
            UNLOCK_SERVICE.grant(player, ArcQuestGuideContent.TRACKING_MENU_GUIDE_ID);
        }
    }

    public static void onQuestStarted(ServerPlayer player) {
        if (player == null) return;
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        if (data.getAllActiveQuests().size() > 1) {
            UNLOCK_SERVICE.grant(player, ArcQuestGuideContent.TRACKING_MENU_GUIDE_ID);
        }
    }
}
