package com.example.arcqaddon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

@Mod.EventBusSubscriber(modid = ExampleArcQuestAddon.MOD_ID)
public final class ExampleQuestRuntimeHooks {
    private ExampleQuestRuntimeHooks() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        String questId = ExampleQuestContent.QUEST_ID.toString();
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        if (!data.isQuestActive(questId) && !data.isQuestCompleted(questId)) {
            QuestProgressHandler.acceptQuestWithCode(player, questId);
        }
    }

    public static void onForemanConversationCompleted(ServerPlayer player) {
        QuestProgressHandler.incrementObjective(
                player,
                ExampleQuestContent.QUEST_ID.toString(),
                ExampleQuestContent.REPORT_PHASE,
                0,
                1
        );
    }
}
