package org.arcadia.arc_quest.questmarker.internal;

import org.arcadia.arc_quest.questmarker.api.MarkActivations;
import org.arcadia.arc_quest.questmarker.api.MarkSpec;
import org.arcadia.arc_quest.questmarker.api.MarkableObject;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MarkerPresentationResolverTest {

    private static final MarkableObject TARGET = new MarkableObject.Pos(1, 2, 3);

    @Test
    void treatsTheDefaultMarkerIdAsALegacyTranslationKey() {
        var presentation = MarkerPresentationResolver.resolve(MarkSpec.of("marker.example.target", TARGET));

        assertEquals("marker.example.target", presentation.label());
        assertEquals("marker.example.target", presentation.extensionHints().get("labelKey"));
    }

    @Test
    void keepsAnExplicitLabelLiteral() {
        var spec = spec(Map.of("label", "Village square"));

        var presentation = MarkerPresentationResolver.resolve(spec);

        assertEquals("Village square", presentation.label());
        assertFalse(presentation.extensionHints().containsKey("labelKey"));
    }

    @Test
    void preservesAnExplicitTranslationKey() {
        var spec = spec(Map.of(
                "label", "Fallback label",
                "labelKey", "marker.example.localized"));

        var presentation = MarkerPresentationResolver.resolve(spec);

        assertEquals("Fallback label", presentation.label());
        assertEquals("marker.example.localized", presentation.extensionHints().get("labelKey"));
    }

    @Test
    void translatedFactoryKeepsMarkerIdentitySeparateFromItsLabelKey() {
        var presentation = MarkerPresentationResolver.resolve(
                MarkSpec.translated("target_marker", "marker.example.target", TARGET));

        assertEquals("target_marker", presentation.label());
        assertEquals("marker.example.target", presentation.extensionHints().get("labelKey"));
    }

    @Test
    void resolvedEntityNameIsTranslatedByTheClientWithoutChangingMarkerIdentity() {
        var presentation = MarkerPresentationResolver.resolve(
                MarkSpec.of("moving_target", TARGET), "entity.minecraft.pig");
        assertEquals("moving_target", presentation.label());
        assertEquals("entity.minecraft.pig", presentation.extensionHints().get("labelKey"));
    }

    @Test
    void resolvedEntityNameDoesNotOverrideAuthoredLabels() {
        var literal = MarkerPresentationResolver.resolve(spec(Map.of("label", "Quest giver")), "entity.minecraft.pig");
        assertEquals("Quest giver", literal.label());
        assertFalse(literal.extensionHints().containsKey("labelKey"));
        var translated = MarkerPresentationResolver.resolve(spec(Map.of("labelKey", "marker.custom")), "entity.minecraft.pig");
        assertEquals("marker.custom", translated.extensionHints().get("labelKey"));
        var hidden = MarkerPresentationResolver.resolve(spec(Map.of("label", "")), "entity.minecraft.pig");
        assertEquals("", hidden.label());
        assertFalse(hidden.extensionHints().containsKey("labelKey"));
    }

    @Test
    void absentEntityNameKeepsLegacyPositionLabel() {
        var presentation = MarkerPresentationResolver.resolve(MarkSpec.of("marker.position", TARGET), "");
        assertEquals("marker.position", presentation.extensionHints().get("labelKey"));
    }

    private static MarkSpec spec(Map<String, String> hints) {
        return new MarkSpec("target_marker", TARGET,
                MarkActivations.always(), MarkActivations.never(),
                QuestMarkerType.QUEST_OBJECTIVE, 0, 256, 20,
                true, false, hints);
    }
}
