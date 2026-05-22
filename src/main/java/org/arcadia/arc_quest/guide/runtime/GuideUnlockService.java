package org.arcadia.arc_quest.guide.runtime;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

public final class GuideUnlockService {

    public boolean unlock(ServerPlayer player, ResourceLocation guideId) {
        GuideDefinition guide = GuideRegistry.get(guideId);
        if (guide == null) {
            return false;
        }
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        boolean changed = data.unlockGuide(guideId);
        if (changed) {
            GuidePlayerStateSyncService.sync(player, data);
        }
        return changed;
    }
}
