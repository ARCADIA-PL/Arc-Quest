package org.arcadia.arc_quest.quest.api;

import net.minecraft.resources.ResourceLocation;

import java.util.*;

public record CollectionEntryPresentation(
        CollectionSubjectSpec subject,
        List<CollectionVariantDefinition> variants,
        String defaultVariantId,
        List<ResourceLocation> sourceSetIds) {
    public CollectionEntryPresentation {
        variants = List.copyOf(variants == null ? List.of() : variants);
        sourceSetIds = List.copyOf(sourceSetIds == null ? List.of() : sourceSetIds);
        defaultVariantId = defaultVariantId == null ? "" : defaultVariantId;
        Set<String> ids = new HashSet<>();
        for (var v : variants)
            if (!ids.add(v.variantId()))
                throw new IllegalArgumentException("Duplicate variant: " + v.variantId());
        if (!defaultVariantId.isEmpty() && !ids.contains(defaultVariantId))
            throw new IllegalArgumentException("Unknown default variant");
    }

    public static CollectionEntryPresentation empty() {
        return new CollectionEntryPresentation(null, List.of(), "", List.of());
    }
}
