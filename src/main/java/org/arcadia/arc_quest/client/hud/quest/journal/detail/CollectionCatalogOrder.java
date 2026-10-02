package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** Bookmark appearance is live; catalog ordering changes only at an actual browser refresh. */
public final class CollectionCatalogOrder {
    private Object progress;
    private String filter;
    private Set<ResourceLocation> favorites = Set.of();
    private boolean initialized;

    public void refresh(Object progress, String filter, Supplier<Set<ResourceLocation>> bookmarks) {
        if (initialized && this.progress == progress && Objects.equals(this.filter, filter)) return;
        this.progress = progress;
        this.filter = filter;
        this.favorites = Set.copyOf(bookmarks.get());
        initialized = true;
    }

    public <T> List<T> sort(List<T> entries, Function<T, ResourceLocation> identity) {
        return entries.stream().sorted(Comparator.comparing(entry -> !favorites.contains(identity.apply(entry)))).toList();
    }

    public void reset() {
        initialized = false;
        progress = null;
        filter = null;
        favorites = Set.of();
    }
}
