package org.arcadia.arc_quest.quest.builder;

import org.arcadia.arc_quest.questmarker.api.MarkTrigger;
import org.arcadia.arc_quest.questmarker.api.MarkTriggers;
import org.arcadia.arc_quest.questmarker.api.MarkableObject;
import org.arcadia.arc_quest.questmarker.api.MarkSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhaseBuilderTrackingMarkerTest {

    @Test
    void buildsTrackingMarkerWithoutMixingRelatedMarkers() {
        var phase = PhaseBuilder.create("travel")
                .objective(ObjectiveBuilder.nullObjective())
                .trackingMarker(new MarkableObject.Pos(12, 64, -8))
                .build();

        assertEquals(0, phase.getRelatedMarks().size());
        assertEquals(1, phase.getTrackingMarks().size());
        assertEquals("travel::tracking_mark_0", phase.getTrackingMarks().get(0).id());
        assertEquals(MarkSpec.DEFAULT_MAX_DISTANCE, phase.getTrackingMarks().get(0).maxDistance());
    }

    @Test
    void rejectsTriggeredTrackingMarker() {
        MarkSpec triggered = MarkTriggers.withTrigger(
                MarkSpec.of("entered", new MarkableObject.Pos(0, 64, 0)),
                MarkTrigger.PHASE_ENTERED,
                100);

        assertThrows(IllegalArgumentException.class, () -> PhaseBuilder.create("travel")
                .trackingMarker(triggered));
    }

    @Test
    void mapsTrackingMarkersInPlacePreservingOrderAndRelatedMarkers() {
        var builder = PhaseBuilder.create("travel").objective(ObjectiveBuilder.nullObjective())
                .trackingMarker(MarkSpec.of("first", new MarkableObject.Pos(1, 64, 0)))
                .trackingMarker(MarkSpec.of("second", new MarkableObject.Pos(2, 64, 0)));
        builder.mapTrackingMarkers(spec -> new MarkSpec(spec.id(), spec.target(), spec.activateWhen(), spec.deactivateWhen(),
                spec.markerType(), spec.priority(), spec.maxDistance(), spec.refreshTicks(), spec.trackMovingEntity(), spec.oneShot(),
                java.util.Map.of("addon", "policy")));
        var result = builder.build();
        assertEquals(java.util.List.of("first", "second"), result.getTrackingMarks().stream().map(MarkSpec::id).toList());
        assertEquals("policy", result.getTrackingMarks().get(0).styleHints().get("addon"));
        assertEquals(0, result.getRelatedMarks().size());
        assertThrows(IllegalArgumentException.class, () -> builder.mapTrackingMarkers(spec ->
                MarkTriggers.withTrigger(spec, MarkTrigger.PHASE_ENTERED, 100)));
        assertEquals(result.getTrackingMarks(), builder.build().getTrackingMarks());
    }
}
