package org.arcadia.arc_quest.guide.runtime;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import org.arcadia.arc_quest.api.event.guide.GuideEvents;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;

import java.util.Collection;

public final class GuideUnlockService {

    public boolean unlock(ServerPlayer player, ResourceLocation guideId) {
        return grant(player, guideId, GuideEvents.UnlockSource.DIRECT);
    }

    public boolean grant(ServerPlayer player, ResourceLocation guideId) {
        return grant(player, guideId, GuideEvents.UnlockSource.DIRECT);
    }

    private boolean grant(ServerPlayer player, ResourceLocation guideId, GuideEvents.UnlockSource source) {
        if (player == null || guideId == null) return false;
        GuideDefinition guide = GuideRegistry.get(guideId);
        if (guide == null) {
            return false;
        }
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        boolean changed = data.unlockGuide(guideId);
        if (changed) {
            NeoForge.EVENT_BUS.post(new GuideEvents.Unlocked(player, guideId, guide, source));
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
        Collection<ResourceLocation> unlocked = new java.util.ArrayList<>();
        for (ResourceLocation guideId : guideIds) {
            if (guideId != null && GuideRegistry.get(guideId) != null && data.unlockGuide(guideId)) {
                changed++;
                unlocked.add(guideId);
            }
        }
        if (changed > 0) {
            for (ResourceLocation guideId : unlocked) {
                NeoForge.EVENT_BUS.post(new GuideEvents.Unlocked(
                        player, guideId, GuideRegistry.get(guideId), GuideEvents.UnlockSource.BULK));
            }
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
        return eligible && grant(player, guideId, GuideEvents.UnlockSource.ELIGIBILITY);
    }
}
