package org.arcadia.arc_quest.quest.tracking.application;

import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TrackedPhaseFocusService {

    private static final Map<UUID, Focus> FOCUSES = new ConcurrentHashMap<>();

    private TrackedPhaseFocusService() {
    }

    public static void prepare(UUID playerId, @Nullable String questId, @Nullable String phaseId) {
        String normalizedQuestId = normalize(questId);
        String normalizedPhaseId = normalize(phaseId);
        if (normalizedQuestId == null || normalizedPhaseId == null) {
            FOCUSES.remove(playerId);
            return;
        }
        FOCUSES.put(playerId, new Focus(normalizedQuestId, normalizedPhaseId));
    }

    public static void onTrackedQuestChanged(UUID playerId, @Nullable String trackedQuestId) {
        String normalizedQuestId = normalize(trackedQuestId);
        FOCUSES.computeIfPresent(playerId, (ignored, focus) ->
                Objects.equals(focus.questId(), normalizedQuestId) ? focus : null);
    }

    @Nullable
    public static String resolve(UUID playerId, String trackedQuestId, QuestRuntimeData runtime) {
        Focus focus = FOCUSES.get(playerId);
        if (focus != null) {
            if (focus.questId().equals(trackedQuestId) && runtime.isPhaseActive(focus.phaseId())) {
                return focus.phaseId();
            }
            FOCUSES.remove(playerId, focus);
        }

        String currentPhaseId = runtime.getCurrentPhaseId();
        return currentPhaseId != null && runtime.isPhaseActive(currentPhaseId) ? currentPhaseId : null;
    }

    public static void clearPlayer(UUID playerId) {
        FOCUSES.remove(playerId);
    }

    public static void clearAll() {
        FOCUSES.clear();
    }

    @Nullable
    private static String normalize(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Focus(String questId, String phaseId) {
    }
}
