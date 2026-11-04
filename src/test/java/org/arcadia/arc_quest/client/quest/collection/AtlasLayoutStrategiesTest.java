package org.arcadia.arc_quest.client.quest.collection;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.resources.ResourceLocation;

import org.arcadia.arc_quest.quest.registry.CollectionAtlasLayoutRegistry;
import org.junit.jupiter.api.Test;

import java.util.*;

class AtlasLayoutStrategiesTest {
    @Test
    void addonSchemaAndStrategyAreUsableAndMissingClientFallsBackToStableGrid() {
        var type = ResourceLocation.parse("test:spiral_atlas");
        CollectionAtlasLayoutRegistry.register(
                type,
                Set.of(CollectionAtlasLayoutRegistry.Scope.GROUP),
                p -> {
                    if (p.getOrDefault("radius", 0d) < 1)
                        throw new IllegalArgumentException("radius required");
                });
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CollectionAtlasLayoutRegistry.validate(
                                type, CollectionAtlasLayoutRegistry.Scope.GROUP, Map.of()));
        var context =
                new AtlasLayoutStrategies.Context(
                        CollectionAtlasLayoutRegistry.Scope.GROUP,
                        List.of("known_a", "known_b"),
                        2,
                        60,
                        70,
                        Map.of("radius", 20d));
        CollectionAtlasLayoutRegistry.validate(type, context.scope(), context.parameters());
        assertEquals(60, AtlasLayoutStrategies.layout(type, context).get("known_b").x());
        AtlasLayoutStrategies.register(
                type,
                c ->
                        List.of(
                                new AtlasLayoutStrategies.Placement("known_a", 20, 30),
                                new AtlasLayoutStrategies.Placement("known_b", 40, 50)));
        assertEquals(50, AtlasLayoutStrategies.layout(type, context).get("known_b").y());
    }

    @Test
    void maliciousOutputCannotInjectUndisclosedIdsOrNonFiniteCoordinates() {
        var type = ResourceLocation.parse("test:invalid_atlas");
        AtlasLayoutStrategies.register(
                type, c -> List.of(new AtlasLayoutStrategies.Placement("secret", Double.NaN, 0)));
        var context =
                new AtlasLayoutStrategies.Context(
                        CollectionAtlasLayoutRegistry.Scope.GROUP,
                        List.of("public"),
                        1,
                        40,
                        50,
                        Map.of());
        var result = AtlasLayoutStrategies.layout(type, context);
        assertEquals(Set.of("public"), result.keySet());
        assertEquals(0, result.get("public").x());
    }
}
