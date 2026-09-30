package org.arcadia.arc_quest.client.hud.quest.icon;

import org.arcadia.arc_quest.quest.api.ObjectiveEntry;

public record ObjectiveIconContext(String questId, String phaseId, int objectiveIndex,
                                   ObjectiveEntry objective, int progress, int requiredCount,
                                   long generation) {
    public String key() {
        return questId + "/" + phaseId + "/" + (objective.hasObjectiveId()
                ? objective.getObjectiveId() : Integer.toString(objectiveIndex));
    }
}
