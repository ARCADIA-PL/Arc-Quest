package org.arcadia.arc_quest.quest.data;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.api.QuestTimeLimitType;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questmarker.api.MarkSpec;
import org.arcadia.arc_quest.questmarker.api.MarkTriggers;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.runtime.QuestMarkerRuntimeManager;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QuestDataTickHandler {
    private QuestDataTickHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) return;

        ServerPlayer player = (ServerPlayer) event.player;
        if (player.tickCount % 20 != 0) return;

        checkQuestTimeouts(player);
        expireTriggeredMarkers(player);
        refreshDynamicMarkers(player);
        syncIfChanged(player);
    }

    private static void checkQuestTimeouts(ServerPlayer player) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;

        var now = CoreProcessors.get().time().capture(player);
        long nowRealMs = now.realTime();
        long nowDayTime = now.dayTime();

        for (QuestRuntimeData questData : List.copyOf(data.getAllActiveQuests().values())) {
            if (questData.getState() != QuestState.ACTIVE) continue;

            QuestDefinition definition = QuestRegistry.get(ResourceLocation.parse(questData.getQuestId()));
            if (definition == null || !definition.hasTimeLimit()) continue;

            QuestTimeLimitType type = definition.getTimeLimitType();
            long limit = definition.getTimeLimitValue();
            boolean timeout = false;
            if (type == QuestTimeLimitType.REAL_SECONDS) {
                long acceptedRealMs = questData.getAcceptedAtRealMs();
                if (acceptedRealMs > 0L) timeout = nowRealMs - acceptedRealMs >= limit * 1000L;
            } else if (type == QuestTimeLimitType.GAME_DAY_TIME) {
                long acceptedDay = questData.getAcceptedAtDayTime() % 24000L;
                long elapsed = (nowDayTime - acceptedDay + 24000L) % 24000L;
                timeout = elapsed >= limit;
            }

            if (timeout) {
                questData.setState(QuestState.FAILED);
                data.markFailed(questData.getQuestId());
                data.removeActiveQuest(questData.getQuestId());
                QuestMarkerRuntimeManager.clearQuest(player.getUUID(), questData.getQuestId());
            }
        }
    }

    private static void refreshDynamicMarkers(ServerPlayer player) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;

        for (QuestMarkerData marker : List.copyOf(data.getAllMarkers().values())) {
            if (!marker.getId().startsWith("aq:auto:") || !marker.hasQuestBinding()) continue;
            QuestRuntimeData quest = data.getActiveQuest(marker.getQuestId());
            boolean stale = quest == null || quest.getState() != QuestState.ACTIVE;
            if (!stale && marker.hasPhaseBinding()) stale = !quest.isPhaseActive(marker.getPhaseId());
            if (!stale && marker.hasObjectiveBinding()) {
                QuestDefinition definition = QuestRegistry.get(ResourceLocation.parse(marker.getQuestId()));
                var phase = definition == null ? null : definition.getPhase(marker.getPhaseId());
                int objectiveIndex = marker.getObjectiveIndex();
                if (phase == null || objectiveIndex < 0 || objectiveIndex >= phase.getObjectives().size()) {
                    stale = true;
                } else {
                    int[] progress = quest.getAllProgress(marker.getPhaseId());
                    int current = objectiveIndex < progress.length ? progress[objectiveIndex] : 0;
                    stale = current >= phase.getObjectives().get(objectiveIndex).resolveRequiredCount(player);
                }
            }
            if (stale) data.removeMarker(marker.getId());
        }

        for (Map.Entry<String, QuestRuntimeData> entry : data.getAllActiveQuests().entrySet()) {
            String questId = entry.getKey();
            QuestDefinition definition = QuestRegistry.get(ResourceLocation.parse(questId));
            if (definition == null) continue;

            for (MarkSpec spec : definition.getRelatedMarks()) {
                if (!MarkTriggers.isContinuous(spec)) continue;
                String markerId = "aq:auto:" + questId + ":" + spec.id();
                QuestMarkerRuntimeManager.refresh(player, data, markerId, questId, spec, null, -1, false);
            }

            for (String phaseId : entry.getValue().getActivePhaseIds()) {
                var phase = definition.getPhase(phaseId);
                if (phase == null) continue;

                for (MarkSpec spec : phase.getRelatedMarks()) {
                    if (!MarkTriggers.isContinuous(spec)) continue;
                    String markerId = "aq:auto:" + questId + ":" + phaseId + ":phase:" + spec.id();
                    QuestMarkerRuntimeManager.refresh(player, data, markerId, questId, spec, phaseId, -1, false);
                }

                int[] progress = entry.getValue().getAllProgress(phaseId);
                var objectives = phase.getObjectives();
                for (int objectiveIndex = 0; objectiveIndex < objectives.size(); objectiveIndex++) {
                    var objective = objectives.get(objectiveIndex);
                    int current = objectiveIndex < progress.length ? progress[objectiveIndex] : 0;
                    if (current >= objective.resolveRequiredCount(player)) continue;

                    for (MarkSpec spec : objective.getRelatedMarks()) {
                        if (!MarkTriggers.isContinuous(spec)) continue;
                        String markerId = "aq:auto:" + questId + ":" + phaseId
                                + ":obj" + objectiveIndex + ":" + spec.id();
                        QuestMarkerRuntimeManager.refresh(
                                player, data, markerId, questId, spec, phaseId, objectiveIndex, false);
                    }
                }
            }
        }
    }

    private static void expireTriggeredMarkers(ServerPlayer player) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        for (String markerId : QuestMarkerRuntimeManager.expire(player, data)) {
            ArcQuestNetwork.syncMarkerDeltaRemove(player, markerId);
        }
    }

    private static void syncIfChanged(ServerPlayer player) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data != null) QuestSyncCoordinator.syncIfChanged(player, data);
    }

    private static void persistAndSyncIfChanged(ServerPlayer player) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data != null) QuestSyncCoordinator.persistAndSyncIfChanged(player, data);
    }

    @SubscribeEvent
    public static void onWorldSave(LevelEvent.Save event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel serverLevel)) return;
        for (ServerPlayer player : serverLevel.players()) persistAndSyncIfChanged(player);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        QuestMarkerRuntimeManager.clearAll();
    }
}
