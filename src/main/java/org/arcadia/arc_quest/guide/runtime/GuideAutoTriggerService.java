package org.arcadia.arc_quest.guide.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.slf4j.Logger;

public final class GuideAutoTriggerService {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final GuideUnlockService unlockService = new GuideUnlockService();
    private static final GuideTriggerService triggerService = new GuideTriggerService();

    private GuideAutoTriggerService() {
    }

    public static void onPlayerLogin(ServerPlayer player) {
        if (player == null) return;
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);

        for (GuideDefinition guide : GuideRegistry.getAll()) {
            if (guide.getUnlockConditions().isEmpty() && !data.isGuideUnlocked(guide.getId())) {
                unlockService.unlock(player, guide.getId());
            }
        }

        for (GuideDefinition guide : GuideRegistry.getAll()) {
            if (guide.isHidden()) continue;
            if (!data.isGuideUnlocked(guide.getId())) continue;
            if (data.isGuideSeen(guide.getId()) && !guide.isRepeatablePopup()) continue;

            triggerService.open(player, guide.getId());
            LOGGER.info("[ArcQuest] Auto-triggered guide '{}' for {}", guide.getId(), player.getName().getString());
            break;
        }
    }
}
