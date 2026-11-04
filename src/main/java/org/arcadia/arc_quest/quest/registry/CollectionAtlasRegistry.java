package org.arcadia.arc_quest.quest.registry;

import net.minecraft.resources.ResourceLocation;

import org.arcadia.arc_quest.quest.api.CollectionAtlasDefinition;

import java.util.*;

public final class CollectionAtlasRegistry {
    private static final Map<ResourceLocation, CollectionAtlasDefinition> DEFAULTS =
            new LinkedHashMap<>();
    private static volatile Map<ResourceLocation, CollectionAtlasDefinition> loaded = Map.of();

    private CollectionAtlasRegistry() {}

    public static synchronized void register(CollectionAtlasDefinition atlas) {
        if (DEFAULTS.putIfAbsent(atlas.id(), atlas) != null)
            throw new IllegalArgumentException("Duplicate atlas: " + atlas.id());
    }

    public static CollectionAtlasDefinition get(ResourceLocation id) {
        if (id == null) return null;
        return loaded.getOrDefault(id, DEFAULTS.get(id));
    }

    public static synchronized void replaceLoaded(
            Map<ResourceLocation, CollectionAtlasDefinition> values) {
        loaded = Map.copyOf(values);
    }

    public static Map<ResourceLocation, CollectionAtlasDefinition> loadedSnapshot() {
        return loaded;
    }

    public static Map<ResourceLocation, CollectionAtlasDefinition> snapshot() {
        var all = new LinkedHashMap<>(DEFAULTS);
        all.putAll(loaded);
        return Map.copyOf(all);
    }
}
