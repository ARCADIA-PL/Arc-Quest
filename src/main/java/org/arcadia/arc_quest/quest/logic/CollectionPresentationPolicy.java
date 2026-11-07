package org.arcadia.arc_quest.quest.logic;

import java.util.Objects;
import java.util.stream.Collectors;
import org.arcadia.arc_quest.quest.api.*;

/** Shared compatibility boundary: only presentation may follow a compatible live atlas. */
public final class CollectionPresentationPolicy {
    private CollectionPresentationPolicy() {}

    public static boolean accepts(QuestDefinition pinned, QuestDefinition live) {
        if (pinned == null || live == null) return false;
        var old = pinned.getCollectionConfig();
        var current = live.getCollectionConfig();
        return old != null && current != null
                && old.getJournalMode() == CollectionPresentationMode.ATLAS
                && current.getJournalMode() == CollectionPresentationMode.ATLAS
                && old.getAtlasId() != null && Objects.equals(old.getAtlasId(), current.getAtlasId())
                && old.getEntries().stream().map(CollectionEntryDefinition::getEntryId).collect(Collectors.toSet())
                        .equals(current.getEntries().stream().map(CollectionEntryDefinition::getEntryId).collect(Collectors.toSet()));
    }

    public static CollectionEntryPresentation presentation(CollectionEntryDefinition pinned, CollectionEntryDefinition live) {
        var view = live.getPresentation();
        var variants = view.variants().stream()
                .filter(v -> v.outcomeId().isEmpty() || pinned.getOutcome(v.outcomeId()) != null).toList();
        String selected = variants.stream().anyMatch(v -> v.variantId().equals(view.defaultVariantId()))
                ? view.defaultVariantId() : "";
        return new CollectionEntryPresentation(view.subject(), variants, selected, view.sourceSetIds(),
                view.detailPages(), view.obtainableAnyFlags(), view.dimUntilDiscovered());
    }
}
