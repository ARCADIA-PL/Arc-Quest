package org.arcadia.arc_quest.quest.registry;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.api.ObjectiveTypeDefinition;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ObjectiveTypeRegistry {

    private static final Map<ResourceLocation, ObjectiveType> TYPES = new LinkedHashMap<>();

    private ObjectiveTypeRegistry() {
    }

    public static synchronized ObjectiveType register(ResourceLocation id, ObjectiveTypeDefinition definition) {
        if (TYPES.containsKey(id)) {
            throw new IllegalStateException("Duplicate objective type id: " + id);
        }
        ObjectiveType type = new ObjectiveType(
                id,
                definition.counting(),
                definition.builtin(),
                definition.displayKey(),
                definition.requireTargetId(),
                definition.defaultTargetKind()
        );
        TYPES.put(id, type);
        return type;
    }

    @Nullable
    public static synchronized ObjectiveType get(ResourceLocation id) {
        return TYPES.get(id);
    }

    public static synchronized ObjectiveType require(ResourceLocation id) {
        ObjectiveType type = TYPES.get(id);
        if (type == null) {
            throw new IllegalArgumentException("Unknown objective type id: " + id);
        }
        return type;
    }

    public static synchronized boolean contains(ResourceLocation id) {
        return TYPES.containsKey(id);
    }

    public static synchronized Collection<ObjectiveType> all() {
        return Collections.unmodifiableList(TYPES.values().stream().toList());
    }
}
