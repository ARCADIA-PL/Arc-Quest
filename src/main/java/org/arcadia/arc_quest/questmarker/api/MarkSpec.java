package org.arcadia.arc_quest.questmarker.api;

import java.util.Map;
import java.util.Objects;

public record MarkSpec(
        String id,
        MarkableObject target,
        MarkActivation activateWhen,
        MarkActivation deactivateWhen,
        QuestMarkerType markerType,
        int priority,
        int maxDistance,
        int refreshTicks,
        boolean trackMovingEntity,
        boolean oneShot,
        Map<String, String> styleHints
) {
    public static final int DEFAULT_MAX_DISTANCE = 512;

    public MarkSpec {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id cannot be blank");
        Objects.requireNonNull(target, "target");
        activateWhen = activateWhen == null ? MarkActivations.always() : activateWhen;
        deactivateWhen = deactivateWhen == null ? MarkActivations.never() : deactivateWhen;
        markerType = markerType == null ? QuestMarkerType.QUEST_OBJECTIVE : markerType;
        priority = Math.max(0, priority);
        maxDistance = maxDistance <= 0 ? DEFAULT_MAX_DISTANCE : maxDistance;
        refreshTicks = refreshTicks <= 0 ? 20 : refreshTicks;
        styleHints = styleHints == null ? Map.of() : Map.copyOf(styleHints);
    }

    public static MarkSpec of(String id, MarkableObject target) {
        return new MarkSpec(id, target, MarkActivations.always(), MarkActivations.never(),
                QuestMarkerType.QUEST_OBJECTIVE, 0, DEFAULT_MAX_DISTANCE, 20, true, false, Map.of());
    }

    /**
     * Creates a marker whose visible label is resolved on the client from a translation key.
     */
    public static MarkSpec translated(String id, String translationKey, MarkableObject target) {
        if (translationKey == null || translationKey.isBlank()) {
            throw new IllegalArgumentException("translationKey cannot be blank");
        }
        return new MarkSpec(id, target, MarkActivations.always(), MarkActivations.never(),
                QuestMarkerType.QUEST_OBJECTIVE, 0, 256, 20, true, false,
                Map.of("labelKey", translationKey));
    }
}
