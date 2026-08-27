package com.example.arcqaddon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.api.event.guide.GuideEvents;
import org.arcadia.arc_quest.guide.runtime.GuideUnlockService;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

import java.util.Collection;
import java.util.LinkedHashSet;

@Mod.EventBusSubscriber(modid = ExampleArcQuestAddon.MOD_ID)
public final class ScenarioGuideRewardHandler {
    private static final GuideUnlockService UNLOCK_SERVICE = new GuideUnlockService();
    private static final String FINAL_PAGE_REWARD_FLAG =
            "example_arcq_addon:guide/field_manual_final_page_rewarded";

    private ScenarioGuideRewardHandler() {
    }

    public static int grantDistinct(ServerPlayer player, Collection<ResourceLocation> guideIds) {
        return UNLOCK_SERVICE.grantAll(player, new LinkedHashSet<>(guideIds));
    }

    @SubscribeEvent
    public static void onGuideProgressChanged(GuideEvents.ProgressChanged event) {
        if (!event.getGuideId().equals(ExampleGuideContent.GUIDE_ID)
                || !event.hasReachedFinalPage()) {
            return;
        }

        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(event.getPlayer());
        if (data.hasFlag(FINAL_PAGE_REWARD_FLAG)) {
            return;
        }
        data.setFlag(FINAL_PAGE_REWARD_FLAG);
        event.getPlayer().giveExperiencePoints(50);
    }
}
