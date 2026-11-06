package org.arcadia.arc_quest.client.quest.collection;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.resources.ResourceLocation;

import org.arcadia.arc_quest.quest.registry.CollectionAtlasLayoutRegistry;
import org.junit.jupiter.api.Test;

import java.util.*;

class AtlasLayoutStrategiesTest {
    @Test void webRowFlowUsesActualPredecessorWidthAndTallestRowHeight() {
        var ids = List.of("a", "b", "c", "d", "e", "f");
        var sizes = Map.of("a", new AtlasLayoutStrategies.Size(152, 125),
                "b", new AtlasLayoutStrategies.Size(196, 125), "c", new AtlasLayoutStrategies.Size(196, 125),
                "d", new AtlasLayoutStrategies.Size(196, 125), "e", new AtlasLayoutStrategies.Size(196, 169),
                "f", new AtlasLayoutStrategies.Size(152, 81));
        var wide = AtlasLayoutStrategies.flowGrid(ids, sizes, 3, 24, 26);
        assertEquals(176, wide.get("b").x());
        assertEquals(396, wide.get("c").x());
        assertEquals(0, wide.get("d").x());
        assertEquals(151, wide.get("d").y());
        assertEquals(220, wide.get("e").x());
        var narrow = AtlasLayoutStrategies.flowGrid(ids, sizes, 2, 24, 26);
        assertEquals(151, narrow.get("c").y());
        assertEquals(302, narrow.get("e").y());
    }
    @Test void packedGroupsDoNotReserveTheTallestGroupsHeightInEveryRow() {
        var ids = List.of("a", "b", "c", "d", "e");
        var sizes = Map.of("a", new AtlasLayoutStrategies.Size(100, 40),
                "b", new AtlasLayoutStrategies.Size(60, 50), "c", new AtlasLayoutStrategies.Size(90, 120),
                "d", new AtlasLayoutStrategies.Size(70, 40), "e", new AtlasLayoutStrategies.Size(100, 30));
        var positions = AtlasLayoutStrategies.packedGrid(ids, sizes, 2, 12, 8);
        assertEquals(112, positions.get("b").x());
        assertEquals(58, positions.get("c").y());
        assertEquals(186, positions.get("e").y());
    }
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
