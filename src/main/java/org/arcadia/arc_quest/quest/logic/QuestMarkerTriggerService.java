package org.arcadia.arc_quest.quest.logic;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.questmarker.api.MarkSpec;
import org.arcadia.arc_quest.questmarker.api.MarkTrigger;
import org.arcadia.arc_quest.questmarker.api.MarkTriggers;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.runtime.QuestMarkerRuntimeManager;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

import java.util.List;

public final class QuestMarkerTriggerService {
    private QuestMarkerTriggerService() {
    }

    public static void triggerQuest(ServerPlayer player, ArcQuestPlayer data, QuestRuntimeData runtime,
                                    QuestDefinition definition, MarkTrigger trigger) {
        trigger(player, data, runtime.getQuestId(), "quest", "", -1, definition.getRelatedMarks(), trigger);
    }

    public static void triggerPhase(ServerPlayer player, ArcQuestPlayer data, QuestRuntimeData runtime,
                                    PhaseDefinition phase, MarkTrigger trigger) {
        trigger(player, data, runtime.getQuestId(), "phase:" + phase.getPhaseId(),
                phase.getPhaseId(), -1, phase.getRelatedMarks(), trigger);
    }

    public static void triggerObjective(ServerPlayer player, ArcQuestPlayer data, QuestRuntimeData runtime,
                                        PhaseDefinition phase, int objectiveIndex, MarkTrigger trigger) {
        if (objectiveIndex < 0 || objectiveIndex >= phase.getObjectives().size()) return;
        ObjectiveEntry objective = phase.getObjectives().get(objectiveIndex);
        trigger(player, data, runtime.getQuestId(),
                "phase:" + phase.getPhaseId() + ":objective:" + objectiveIndex,
                phase.getPhaseId(), objectiveIndex, objective.getRelatedMarks(), trigger);
    }

    private static void trigger(ServerPlayer player, ArcQuestPlayer data, String questId, String scope,
                                String phaseId, int objectiveIndex, List<MarkSpec> specs, MarkTrigger trigger) {
        for (MarkSpec spec : specs) {
            if (MarkTriggers.trigger(spec) != trigger) continue;
            String markerId = "aq:trigger:quest:" + questId + ":" + scope + ":"
                    + trigger.name().toLowerCase() + ":" + spec.id();
            if (!QuestMarkerRuntimeManager.trigger(
                    player, data, markerId, questId, spec, phaseId, objectiveIndex)) continue;
            QuestMarkerData marker = data.getAllMarkers().get(markerId);
            if (marker != null) ArcQuestNetwork.syncMarkerDeltaUpsert(player, marker);
            else ArcQuestNetwork.syncMarkerDeltaRemove(player, markerId);
        }
    }
}
