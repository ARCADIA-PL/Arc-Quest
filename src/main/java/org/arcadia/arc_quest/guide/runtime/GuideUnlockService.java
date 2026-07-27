package org.arcadia.arc_quest.guide.runtime;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;

import java.util.Collection;

public final class GuideUnlockService {

    public boolean unlock(ServerPlayer player, ResourceLocation guideId) {
        return grant(player, guideId);
    }

    public boolean grant(ServerPlayer player, ResourceLocation guideId) {
        if (player == null || guideId == null) return false;
        GuideDefinition guide = GuideRegistry.get(guideId);
        if (guide == null) {
            return false;
        }
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        boolean changed = data.unlockGuide(guideId);
        if (changed) {
            QuestSyncCoordinator.persistSnapshot(player, data);
            GuidePlayerStateSyncService.sync(player, data);
            data.clearDirty(ArcQuestPlayer.DirtyKind.GUIDE_STATE);
        }
        return changed;
    }

    public int grantAll(ServerPlayer player, Collection<ResourceLocation> guideIds) {
        if (player == null || guideIds == null || guideIds.isEmpty()) return 0;
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        int changed = 0;
        for (ResourceLocation guideId : guideIds) {
            if (guideId != null && GuideRegistry.get(guideId) != null && data.unlockGuide(guideId)) changed++;
        }
        if (changed > 0) {
            QuestSyncCoordinator.persistSnapshot(player, data);
            GuidePlayerStateSyncService.sync(player, data);
            data.clearDirty(ArcQuestPlayer.DirtyKind.GUIDE_STATE);
        }
        return changed;
    }

    public boolean ensureUnlockedIfEligible(ServerPlayer player, ResourceLocation guideId) {
        GuideDefinition guide = GuideRegistry.get(guideId);
        if (player == null || guide == null) {
            return false;
        }
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        if (data.isGuideUnlocked(guideId)) {
            return true;
        }
        boolean eligible = guide.canUnlock(
                player,
                data.getCompletedQuestLocations(),
                data.getAllFlags(),
                data.getAllVariables()
        );
        return eligible && unlock(player, guideId);
    }
}
