package org.arcadia.arc_quest.quest.api;

import net.minecraft.resources.ResourceLocation;

import java.util.*;

public record CollectionEntryPresentation(
        CollectionSubjectSpec subject,
        List<CollectionVariantDefinition> variants,
        String defaultVariantId,
        List<ResourceLocation> sourceSetIds,
        List<CollectionDetailPageSpec> detailPages,
        List<String> obtainableAnyFlags) {
    public CollectionEntryPresentation(CollectionSubjectSpec subject,
            List<CollectionVariantDefinition> variants, String defaultVariantId,
            List<ResourceLocation> sourceSetIds, List<CollectionDetailPageSpec> detailPages) {
        this(subject, variants, defaultVariantId, sourceSetIds, detailPages, List.of());
    }
    public CollectionEntryPresentation(CollectionSubjectSpec subject,
            List<CollectionVariantDefinition> variants, String defaultVariantId,
            List<ResourceLocation> sourceSetIds) {
        this(subject, variants, defaultVariantId, sourceSetIds, List.of());
    }

    /** Presentation only; never authorizes a purchase or creates a discovery record. */
    public boolean obtainable(java.util.function.Predicate<String> flags, boolean discovered) {
        return discovered || obtainableAnyFlags.isEmpty() || obtainableAnyFlags.stream().anyMatch(flags);
    }

    public CollectionEntryPresentation {
        obtainableAnyFlags = List.copyOf(obtainableAnyFlags == null ? List.of() : obtainableAnyFlags);
        if (obtainableAnyFlags.size() > 32 || obtainableAnyFlags.stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("Invalid obtainable flags");
        detailPages = List.copyOf(detailPages == null ? List.of() : detailPages);
        Set<String> pageIds = new HashSet<>();
        if (detailPages.size() > 16) throw new IllegalArgumentException("Too many detail pages");
        for (var page : detailPages)
            if (!pageIds.add(page.id())) throw new IllegalArgumentException("Duplicate detail page: " + page.id());
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
