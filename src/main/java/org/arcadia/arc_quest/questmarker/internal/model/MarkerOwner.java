package org.arcadia.arc_quest.questmarker.internal.model;

public sealed interface MarkerOwner permits MarkerOwner.Quest, MarkerOwner.Phase,
        MarkerOwner.Objective, MarkerOwner.Dialogue, MarkerOwner.Manual {

    default String questId() {
        return "";
    }

    record Quest(String questId) implements MarkerOwner {
    }

    record Phase(String questId, String phaseId) implements MarkerOwner {
    }

    record Objective(String questId, String phaseId, int objectiveIndex) implements MarkerOwner {
    }

    record Dialogue(String sourceId) implements MarkerOwner {
    }

    record Manual(String sourceId) implements MarkerOwner {
    }
}
