package org.arcadia.arc_quest.questmarker.internal.runtime;

import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class MarkerReconciliationEngine {

    private MarkerReconciliationEngine() {
    }

    public static Result reconcile(ArcQuestPlayer data,
                                   Predicate<QuestMarkerData> managedScope,
                                   Collection<QuestMarkerData> desiredMarkers) {
        Map<String, QuestMarkerData> desired = new LinkedHashMap<>();
        for (QuestMarkerData marker : desiredMarkers) desired.put(marker.getId(), marker);

        List<String> removed = new ArrayList<>();
        for (QuestMarkerData current : List.copyOf(data.getAllMarkers().values())) {
            if (!managedScope.test(current) || desired.containsKey(current.getId())) continue;
            data.removeMarker(current.getId());
            removed.add(current.getId());
        }

        List<QuestMarkerData> upserted = new ArrayList<>();
        for (QuestMarkerData desiredMarker : desired.values()) {
            QuestMarkerData current = data.getAllMarkers().get(desiredMarker.getId());
            if (desiredMarker.equals(current)) continue;
            data.upsertMarker(desiredMarker);
            upserted.add(desiredMarker);
        }
        return new Result(List.copyOf(upserted), List.copyOf(removed));
    }

    public record Result(List<QuestMarkerData> upserted, List<String> removed) {
        public boolean changed() {
            return !upserted.isEmpty() || !removed.isEmpty();
        }
    }
}
