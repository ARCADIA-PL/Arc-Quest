package org.arcadia.arc_quest.quest.logic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import org.arcadia.arc_quest.api.event.quest.QuestMarkersRefreshedEvent;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerState;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerType;
import org.arcadia.arc_quest.questmarker.internal.MarkerIds;
import org.arcadia.arc_quest.questmarker.internal.runtime.MarkerReconciliationEngine;
import org.arcadia.arc_quest.questmarker.runtime.QuestMarkerReconciliationService;

import java.util.ArrayList;
import java.util.List;

public final class QuestMarkerService {

    private QuestMarkerService() {
    }

    public static void refreshQuestMarkers(ServerPlayer player,
                                           ArcQuestPlayer data,
                                           QuestRuntimeData qdata,
                                           QuestDefinition def) {
        String dimension = player.level().dimension().location().toString();
        QuestMarkerType questType = def.getCategory() == QuestCategory.ARCHON
                ? QuestMarkerType.QUEST_MAIN
                : QuestMarkerType.QUEST_SIDE;

        List<QuestMarkerData> desiredMarkers = new ArrayList<>();
        for (String phaseId : qdata.getActivePhaseIds()) {
            PhaseDefinition phase = def.getPhase(phaseId);
            if (phase == null) continue;

            List<ObjectiveEntry> objectives = phase.getObjectives();
            for (int i = 0; i < objectives.size(); i++) {
                ObjectiveEntry obj = objectives.get(i);
                if (obj.isHidden()) continue;
                if (!obj.getType().equals(ObjectiveType.REACH_LOCATION)) continue;

                Double x = parseDouble(obj.getExtra("x"));
                Double y = parseDouble(obj.getExtra("y"));
                Double z = parseDouble(obj.getExtra("z"));
                if (x == null || y == null || z == null) continue;

                String markerDimension = firstNonEmpty(
                        obj.getExtra("dimension"),
                        obj.getExtra("dim"),
                        obj.getExtra("world"),
                        dimension
                );

                String markerId = buildMarkerId(qdata.getQuestId(), phaseId, i);
                String label = obj.getDisplayText().getString();

                QuestMarkerData marker = new QuestMarkerData.Builder(markerId, x, y, z, label)
                        .dimension(markerDimension)
                        .bindQuest(qdata.getQuestId())
                        .bindPhase(phaseId)
                        .bindObjective(i)
                        .type(questType)
                        .state(QuestMarkerState.fromQuestState(qdata.getState()))
                        .color(0xFF000000 | def.getCategory().getThemeColor())
                        .showDistance(true)
                        .allowOffscreenArrow(true)
                        .persistent(false)
                        .build();
                desiredMarkers.add(marker);
            }
        }

        String questId = qdata.getQuestId();
        MarkerReconciliationEngine.Result result = MarkerReconciliationEngine.reconcile(data,
                marker -> MarkerIds.isLegacyLocation(marker.getId())
                        && marker.hasQuestBinding()
                        && questId.equals(marker.getQuestId()),
                desiredMarkers);
        result.removed().forEach(markerId -> ArcQuestNetwork.syncMarkerDeltaRemove(player, markerId));
        result.upserted().forEach(marker -> ArcQuestNetwork.syncMarkerDeltaUpsert(player, marker));
        QuestMarkerReconciliationService.reconcileTrackingPhaseMarkers(player, data, true);

        NeoForge.EVENT_BUS.post(new QuestMarkersRefreshedEvent(
                player,
                ResourceLocation.parse(qdata.getQuestId()),
                qdata.getActivePhaseIds().size(),
                desiredMarkers.size()
        ));
    }

    public static List<String> clearGeneratedLocationMarkers(ArcQuestPlayer data, String questId) {
        return MarkerReconciliationEngine.reconcile(data,
                marker -> MarkerIds.isLegacyLocation(marker.getId())
                        && marker.hasQuestBinding()
                        && questId.equals(marker.getQuestId()),
                List.of()).removed();
    }

    public static List<String> clearQuestMarkers(ArcQuestPlayer data, String questId) {
        List<String> toRemove = data.getAllMarkers().values().stream()
                .filter(m -> m.hasQuestBinding() && questId.equals(m.getQuestId()))
                .map(QuestMarkerData::getId)
                .toList();
        for (String id : toRemove) {
            data.removeMarker(id);
        }
        return toRemove;
    }

    public static String buildMarkerId(String questId, String phaseId, int objectiveIndex) {
        return MarkerIds.legacyLocation(questId, phaseId, objectiveIndex);
    }

    static String firstNonEmpty(String... candidates) {
        for (String s : candidates) {
            if (s != null && !s.isEmpty()) return s;
        }
        return "";
    }

    static Double parseDouble(String v) {
        if (v == null || v.isEmpty()) return null;
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
