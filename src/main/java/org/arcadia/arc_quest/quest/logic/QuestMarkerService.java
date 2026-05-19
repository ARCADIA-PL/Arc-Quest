package org.arcadia.arc_quest.quest.logic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import org.arcadia.arc_quest.api.event.quest.QuestMarkersRefreshedEvent;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.player.ArcQuestPlayer;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerState;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerType;

import java.util.List;

public final class QuestMarkerService {

    private QuestMarkerService() {
    }

    public static void refreshQuestMarkers(ServerPlayer player,
                                           ArcQuestPlayer data,
                                           QuestRuntimeData data,
                                           QuestDefinition def) {
        List<String> removedIds = clearQuestMarkers(cap, data.getQuestId());
        for (String markerId : removedIds) {
            ArcQuestNetwork.syncMarkerDeltaRemove(player, markerId);
        }

        String dimension = player.level().dimension().location().toString();
        QuestMarkerType questType = def.getCategory() == QuestCategory.ARCHON
                ? QuestMarkerType.QUEST_MAIN
                : QuestMarkerType.QUEST_SIDE;

        for (String phaseId : data.getActivePhaseIds()) {
            PhaseDefinition phase = def.getPhase(phaseId);
            if (phase == null) continue;

            List<ObjectiveEntry> objectives = phase.getObjectives();
            for (int i = 0; i < objectives.size(); i++) {
                ObjectiveEntry obj = objectives.get(i);
                if (obj.isHidden()) continue;
                if (obj.getType() != ObjectiveType.REACH_LOCATION) continue;

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

                String markerId = buildMarkerId(data.getQuestId(), phaseId, i);
                String label = obj.getDisplayText().getString();

                QuestMarkerData marker = new QuestMarkerData.Builder(markerId, x, y, z, label)
                        .dimension(markerDimension)
                        .bindQuest(data.getQuestId())
                        .bindPhase(phaseId)
                        .bindObjective(i)
                        .type(questType)
                        .state(QuestMarkerState.fromQuestState(data.getState()))
                        .color(0xFF000000 | def.getCategory().getThemeColor())
                        .showDistance(true)
                        .allowOffscreenArrow(true)
                        .build();
                data.upsertMarker(marker);
                ArcQuestNetwork.syncMarkerDeltaUpsert(player, marker);
            }
        }

        MinecraftForge.EVENT_BUS.post(new QuestMarkersRefreshedEvent(
                player,
                ResourceLocation.parse(data.getQuestId()),
                data.getActivePhaseIds().size()
        ));
    }

    public static List<String> clearQuestMarkers(ArcQuestPlayer data, String questId) {
        List<String> toRemove = cap.getAllMarkers().values().stream()
                .filter(m -> m.hasQuestBinding() && questId.equals(m.getQuestId()))
                .map(QuestMarkerData::getId)
                .toList();
        for (String id : toRemove) {
            data.removeMarker(id);
        }
        return toRemove;
    }

    public static String buildMarkerId(String questId, String phaseId, int objectiveIndex) {
        return "quest:" + questId + ":" + phaseId + ":" + objectiveIndex;
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
