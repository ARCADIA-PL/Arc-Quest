package org.arcadia.arc_quest.guide.runtime;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.guide.registry.ArcQuestGuideContent;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

public final class GuideAutoTriggerService {

    private static final GuideUnlockService UNLOCK_SERVICE = new GuideUnlockService();
    private static final GuideTriggerService TRIGGER_SERVICE = new GuideTriggerService();

    private GuideAutoTriggerService() {
    }

    public static void onPlayerLogin(ServerPlayer player) {
        if (player == null) return;
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        ensureUnlockedAndOpen(player, ArcQuestGuideContent.JOURNAL_BASICS_GUIDE_ID);
        if (data.getAllActiveQuests().size() > 1) {
            ensureUnlocked(player, ArcQuestGuideContent.TRACKING_MENU_GUIDE_ID);
        }
        for (QuestRuntimeData runtime : data.getAllActiveQuests().values()) {
            if (runtime.getActivePhaseIds().size() > 1) {
                ensureUnlocked(player, ArcQuestGuideContent.PARALLEL_PHASES_GUIDE_ID);
                break;
            }
        }
    }

    private static void ensureUnlockedAndOpen(ServerPlayer player, ResourceLocation guideId) {
        if (UNLOCK_SERVICE.grant(player, guideId)) {
            TRIGGER_SERVICE.openIfUnread(player, guideId);
        }
    }

    private static void ensureUnlocked(ServerPlayer player, ResourceLocation guideId) {
        UNLOCK_SERVICE.grant(player, guideId);
    }

    public static void onQuestStarted(ServerPlayer player) {
        if (player == null) return;
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        if (data.getAllActiveQuests().size() > 1) {
            ensureUnlocked(player, ArcQuestGuideContent.TRACKING_MENU_GUIDE_ID);
        }
        grantParallelPhasesGuideIfNeeded(player, data);
    }

    public static void onPhaseActivated(ServerPlayer player) {
        if (player == null) return;
        grantParallelPhasesGuideIfNeeded(player, ArcQuestPlayerManager.getOrCreate(player));
    }

    private static void grantParallelPhasesGuideIfNeeded(ServerPlayer player, ArcQuestPlayer data) {
        for (QuestRuntimeData runtime : data.getAllActiveQuests().values()) {
            if (runtime.getActivePhaseIds().size() > 1) {
                ensureUnlocked(player, ArcQuestGuideContent.PARALLEL_PHASES_GUIDE_ID);
                return;
            }
        }
    }
}
