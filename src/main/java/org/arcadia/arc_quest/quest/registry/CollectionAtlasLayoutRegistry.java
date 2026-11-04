package org.arcadia.arc_quest.quest.registry;

import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.function.Consumer;

/**
 * Common layout parameter schemas; registrations contain no client classes or executable player
 * rules.
 */
public final class CollectionAtlasLayoutRegistry {
    public enum Scope {
        CATEGORY,
        GROUP
    }

    private record Schema(Set<Scope> scopes, Consumer<Map<String, Double>> validator) {}

    private static final Map<ResourceLocation, Schema> SCHEMAS = new HashMap<>();

    static {
        register(ResourceLocation.parse("arc_quest:group_grid"), Set.of(Scope.CATEGORY), p -> {});
        register(ResourceLocation.parse("arc_quest:node_grid"), Set.of(Scope.GROUP), p -> {});
        register(
                ResourceLocation.parse("arc_quest:manual"),
                Set.of(Scope.CATEGORY, Scope.GROUP),
                p -> {});
    }

    private CollectionAtlasLayoutRegistry() {}

    public static synchronized void register(
            ResourceLocation type, Set<Scope> scopes, Consumer<Map<String, Double>> validator) {
        if (type == null
                || scopes.isEmpty()
                || SCHEMAS.putIfAbsent(
                                type,
                                new Schema(Set.copyOf(scopes), Objects.requireNonNull(validator)))
                        != null)
            throw new IllegalArgumentException("Invalid/duplicate layout schema: " + type);
    }

    public static void validate(
            ResourceLocation type, Scope scope, Map<String, Double> parameters) {
        var schema = SCHEMAS.get(type);
        if (schema == null || !schema.scopes().contains(scope))
            throw new IllegalArgumentException(
                    "Unsupported atlas layout: " + type + " for " + scope);
        schema.validator().accept(Map.copyOf(parameters));
    }
}
