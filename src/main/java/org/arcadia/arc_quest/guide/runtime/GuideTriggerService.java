package org.arcadia.arc_quest.guide.runtime;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.network.S2COpenGuidePacket;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

public final class GuideTriggerService {

    private final GuideUnlockService unlockService = new GuideUnlockService();

    public boolean open(ServerPlayer player, ResourceLocation guideId) {
        return open(player, guideId, 0, true);
    }

    public boolean open(ServerPlayer player, ResourceLocation guideId, int initialPage, boolean markSeenOnClose) {
        GuideDefinition guide = GuideRegistry.get(guideId);
        if (player == null || guide == null) {
            return false;
        }
        ArcQuestNetwork.sendGuideOpenPacket(player, new S2COpenGuidePacket(guideId, clampInitialPage(initialPage, guide), markSeenOnClose));
        return true;
    }

    public boolean openIfUnread(ServerPlayer player, ResourceLocation guideId) {
        GuideDefinition guide = GuideRegistry.get(guideId);
        if (player == null || guide == null) {
            return false;
        }
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        if (!data.isGuideUnlocked(guideId) && !unlockService.ensureUnlockedIfEligible(player, guideId)) {
            return false;
        }
        return (!data.isGuideSeen(guideId) || guide.isRepeatablePopup()) && open(player, guideId, 0, true);
    }

    public boolean openIfUnlocked(ServerPlayer player, ResourceLocation guideId) {
        if (player == null) {
            return false;
        }
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        return data.isGuideUnlocked(guideId) && open(player, guideId, 0, true);
    }

    public boolean openIfEligible(ServerPlayer player, ResourceLocation guideId) {
        GuideDefinition guide = GuideRegistry.get(guideId);
        if (player == null || guide == null) {
            return false;
        }
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        if (!data.isGuideUnlocked(guideId) && !unlockService.ensureUnlockedIfEligible(player, guideId)) {
            return false;
        }
        return (!data.isGuideSeen(guideId) || guide.isRepeatablePopup()) && open(player, guideId, 0, true);
    }

    private int clampInitialPage(int initialPage, GuideDefinition guide) {
        return Math.max(0, Math.min(initialPage, Math.max(0, guide.getPageCount() - 1)));
    }
}
