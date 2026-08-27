package com.example.arcqaddon;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.api.event.quest.QuestCompletedEvent;
import org.arcadia.arc_quest.guide.runtime.GuideUnlockService;

@Mod.EventBusSubscriber(modid = ExampleArcQuestAddon.MOD_ID)
public final class ExamplePlayerEvents {
    private static final GuideUnlockService GUIDE_UNLOCK_SERVICE = new GuideUnlockService();

    private ExamplePlayerEvents() {
    }

    @SubscribeEvent
    public static void onQuestCompleted(QuestCompletedEvent event) {
        if (!event.getQuestId().equals(ExampleQuestContent.QUEST_ID)) {
            return;
        }
        GUIDE_UNLOCK_SERVICE.grant(event.getPlayer(), ExampleGuideContent.GUIDE_ID);
    }
}
