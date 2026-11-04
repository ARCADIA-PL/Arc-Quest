package org.arcadia.arc_quest.quest.api;

import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * One directed relation supports both incoming sources and outgoing contents. No author evidence is
 * synced.
 */
public record CollectionSourceSetDefinition(
        int schemaVersion, ResourceLocation id, List<Relation> relations) {
    public CollectionSourceSetDefinition {
        if (schemaVersion != 1 || id == null)
            throw new IllegalArgumentException("Invalid source set");
        relations = List.copyOf(relations);
        Set<ResourceLocation> ids = new HashSet<>();
        for (var r : relations)
            if (!ids.add(r.relationId())) throw new IllegalArgumentException("Duplicate relation");
    }

    public record Relation(
            ResourceLocation relationId,
            ResourceLocation sourceEntryId,
            ResourceLocation targetEntryId,
            List<String> variantIds,
            String method,
            QuestText description,
            int sortOrder) {
        public Relation {
            if (relationId == null || sourceEntryId == null || targetEntryId == null)
                throw new IllegalArgumentException("Source endpoints required");
            variantIds = List.copyOf(variantIds == null ? List.of() : variantIds);
            method = method == null ? "" : method;
            description = description == null ? QuestText.literal("") : description;
        }
    }
}
