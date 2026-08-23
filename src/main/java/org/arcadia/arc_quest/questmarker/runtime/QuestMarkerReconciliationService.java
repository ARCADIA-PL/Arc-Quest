package org.arcadia.arc_quest.questmarker.runtime;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questmarker.api.MarkSpec;
import org.arcadia.arc_quest.questmarker.api.MarkTriggers;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.internal.MarkerIds;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class QuestMarkerReconciliationService {
    private QuestMarkerReconciliationService() {
    }

    public static boolean reconcileContinuousQuestMarkers(ServerPlayer player, ArcQuestPlayer data, boolean force) {
        boolean changed = removeStaleMarkers(player, data);
        changed |= reconcileTrackingPhaseMarkers(player, data, force);

        for (Map.Entry<String, QuestRuntimeData> entry : data.getAllActiveQuests().entrySet()) {
            QuestRuntimeData runtime = entry.getValue();
            if (runtime.getState() != QuestState.ACTIVE) continue;

            String questId = entry.getKey();
            ResourceLocation parsedQuestId = ResourceLocation.tryParse(questId);
            QuestDefinition definition = parsedQuestId == null ? null : QuestRegistry.get(parsedQuestId);
            if (definition == null) continue;

            for (MarkSpec spec : definition.getRelatedMarks()) {
                if (!MarkTriggers.isContinuous(spec)) continue;
                String markerId = MarkerIds.autoQuest(questId, spec.id());
                changed |= QuestMarkerRuntimeManager.refresh(
                        player, data, markerId, questId, spec, null, -1, force);
            }

            for (String phaseId : runtime.getActivePhaseIds()) {
                var phase = definition.getPhase(phaseId);
                if (phase == null) continue;

                for (MarkSpec spec : phase.getRelatedMarks()) {
                    if (!MarkTriggers.isContinuous(spec)) continue;
                    String markerId = MarkerIds.autoPhase(questId, phaseId, spec.id());
                    changed |= QuestMarkerRuntimeManager.refresh(
                            player, data, markerId, questId, spec, phaseId, -1, force);
                }

                int[] progress = runtime.getAllProgress(phaseId);
                var objectives = phase.getObjectives();
                for (int objectiveIndex = 0; objectiveIndex < objectives.size(); objectiveIndex++) {
                    var objective = objectives.get(objectiveIndex);
                    int current = objectiveIndex < progress.length ? progress[objectiveIndex] : 0;
                    if (current >= objective.resolveRequiredCount(player)) continue;

                    for (MarkSpec spec : objective.getRelatedMarks()) {
                        if (!MarkTriggers.isContinuous(spec)) continue;
                        String markerId = MarkerIds.autoObjective(questId, phaseId, objectiveIndex, spec.id());
                        changed |= QuestMarkerRuntimeManager.refresh(
                                player, data, markerId, questId, spec, phaseId, objectiveIndex, force);
                    }
                }
            }
        }
        return changed;
    }

    public static boolean reconcileTrackingPhaseMarkers(ServerPlayer player, ArcQuestPlayer data, boolean force) {
        Set<String> desiredMarkerIds = new LinkedHashSet<>();
        String trackedQuestId = data.getTrackedQuestId();
        QuestRuntimeData runtime = trackedQuestId == null ? null : data.getActiveQuest(trackedQuestId);
        ResourceLocation parsedQuestId = trackedQuestId == null ? null : ResourceLocation.tryParse(trackedQuestId);
        QuestDefinition definition = parsedQuestId == null ? null : QuestRegistry.get(parsedQuestId);

        if (runtime != null && runtime.getState() == QuestState.ACTIVE && definition != null) {
            for (String phaseId : runtime.getActivePhaseIds()) {
                var phase = definition.getPhase(phaseId);
                if (phase == null) continue;
                for (MarkSpec spec : phase.getTrackingMarks()) {
                    if (!MarkTriggers.isContinuous(spec)) continue;
                    desiredMarkerIds.add(MarkerIds.trackingPhase(trackedQuestId, phaseId, spec.id()));
                }
            }
        }

        boolean changed = false;
        for (QuestMarkerData marker : List.copyOf(data.getAllMarkers().values())) {
            if (!MarkerIds.isTracking(marker.getId()) || desiredMarkerIds.contains(marker.getId())) continue;
            data.removeMarker(marker.getId());
            changed = true;
        }

        if (runtime == null || runtime.getState() != QuestState.ACTIVE || definition == null) return changed;
        for (String phaseId : runtime.getActivePhaseIds()) {
            var phase = definition.getPhase(phaseId);
            if (phase == null) continue;
            for (MarkSpec spec : phase.getTrackingMarks()) {
                if (!MarkTriggers.isContinuous(spec)) continue;
                String markerId = MarkerIds.trackingPhase(trackedQuestId, phaseId, spec.id());
                changed |= QuestMarkerRuntimeManager.refresh(
                        player, data, markerId, trackedQuestId, spec, phaseId, -1, force);
            }
        }
        return changed;
    }

    private static boolean removeStaleMarkers(ServerPlayer player, ArcQuestPlayer data) {
        boolean changed = false;
        for (QuestMarkerData marker : List.copyOf(data.getAllMarkers().values())) {
            if (!MarkerIds.isAuto(marker.getId()) || !marker.hasQuestBinding()) continue;

            QuestRuntimeData runtime = data.getActiveQuest(marker.getQuestId());
            boolean stale = runtime == null || runtime.getState() != QuestState.ACTIVE;
            if (!stale && marker.hasPhaseBinding()) stale = !runtime.isPhaseActive(marker.getPhaseId());
            if (!stale && marker.hasObjectiveBinding()) {
                ResourceLocation questId = ResourceLocation.tryParse(marker.getQuestId());
                QuestDefinition definition = questId == null ? null : QuestRegistry.get(questId);
                var phase = definition == null ? null : definition.getPhase(marker.getPhaseId());
                int objectiveIndex = marker.getObjectiveIndex();
                if (phase == null || objectiveIndex < 0 || objectiveIndex >= phase.getObjectives().size()) {
                    stale = true;
                } else {
                    int[] progress = runtime.getAllProgress(marker.getPhaseId());
                    int current = objectiveIndex < progress.length ? progress[objectiveIndex] : 0;
                    stale = current >= phase.getObjectives().get(objectiveIndex).resolveRequiredCount(player);
                }
            }
            if (stale) {
                data.removeMarker(marker.getId());
                changed = true;
            }
        }
        return changed;
    }
}
