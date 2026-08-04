package org.arcadia.arc_quest.questmarker.internal.model;

import org.arcadia.arc_quest.questmarker.api.QuestMarkerState;

import java.util.Objects;

public record MarkerSnapshot(String id,
                             MarkerOwner owner,
                             MarkerTarget target,
                             MarkerPresentation presentation,
                             QuestMarkerState state,
                             MarkerPersistence persistence) {

    public MarkerSnapshot {
        Objects.requireNonNull(id);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(target);
        Objects.requireNonNull(presentation);
        state = state == null ? QuestMarkerState.ACTIVE : state;
        Objects.requireNonNull(persistence);
    }
}
