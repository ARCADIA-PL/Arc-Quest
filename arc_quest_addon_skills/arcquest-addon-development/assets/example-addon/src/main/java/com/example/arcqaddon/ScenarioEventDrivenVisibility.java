package com.example.arcqaddon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.api.event.quest.QuestCompletedEvent;
import org.arcadia.arc_quest.api.event.quest.QuestPhaseActivatedEvent;
import org.arcadia.arc_quest.api.event.quest.QuestPhaseCompletedEvent;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = ExampleArcQuestAddon.MOD_ID)
public final class ScenarioEventDrivenVisibility {
    private static final Map<UUID, Boolean> MARKET_ACTOR_VISIBILITY =
            new ConcurrentHashMap<>();

    private ScenarioEventDrivenVisibility() {
    }

    public static boolean isMarketActorVisible(UUID playerId) {
        return MARKET_ACTOR_VISIBILITY.getOrDefault(playerId, false);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refresh(player);
        }
    }

    @SubscribeEvent
    public static void onPhaseActivated(QuestPhaseActivatedEvent event) {
        if (event.getQuestId().equals(ScenarioParallelQuestContent.QUEST_ID)) {
            refresh(event.getPlayer());
        }
    }

    @SubscribeEvent
    public static void onPhaseCompleted(QuestPhaseCompletedEvent event) {
        if (event.getQuestId().equals(ScenarioParallelQuestContent.QUEST_ID)) {
            refresh(event.getPlayer());
        }
    }

    @SubscribeEvent
    public static void onQuestCompleted(QuestCompletedEvent event) {
        if (event.getQuestId().equals(ScenarioParallelQuestContent.QUEST_ID)) {
            refresh(event.getPlayer());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        MARKET_ACTOR_VISIBILITY.remove(event.getEntity().getUUID());
    }

    private static void refresh(ServerPlayer player) {
        QuestRuntimeData runtime = ArcQuestPlayerManager.getOrCreate(player)
                .getActiveQuest(ScenarioParallelQuestContent.QUEST_ID.toString());
        boolean visible = runtime != null
                && runtime.isPhaseActive(ScenarioParallelQuestContent.VISIT_MARKET_PHASE);
        MARKET_ACTOR_VISIBILITY.put(player.getUUID(), visible);
    }
}
