package org.arcadia.arc_quest.client.quest.collection;

import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Local personal bookmarks, keyed by shared entry identity rather than quest, phase or accepted run. */
public final class CollectionFavoritesStore {
    public static final CollectionFavoritesStore INSTANCE = new CollectionFavoritesStore(CollectionFavoriteScope::currentFile);

    private final Supplier<Path> currentFile;
    private final Set<ResourceLocation> favorites = new LinkedHashSet<>();
    private Path loadedFile;
    private boolean loaded;
    private long revision;

    CollectionFavoritesStore(Supplier<Path> currentFile) {
        this.currentFile = Objects.requireNonNull(currentFile);
    }

    public synchronized boolean isFavorite(ResourceLocation entryId) {
        ensureLoaded();
        return entryId != null && favorites.contains(entryId);
    }

    /** Returns the new state. Persistence happens only on a user toggle, never on rendering. */
    public synchronized boolean toggle(ResourceLocation entryId) {
        Objects.requireNonNull(entryId, "entryId");
        ensureLoaded();
        boolean added = favorites.add(entryId);
        if (!added) favorites.remove(entryId);
        revision++;
        CollectionFavoritesPersistence.save(loadedFile, favorites);
        return added;
    }

    /** Includes scope switches so callers can invalidate filters after reconnecting or changing players. */
    public synchronized long revision() {
        ensureLoaded();
        return revision;
    }

    public synchronized Set<ResourceLocation> snapshot() {
        ensureLoaded();
        return Set.copyOf(favorites);
    }

    private void ensureLoaded() {
        Path path = currentFile.get();
        if (loaded && Objects.equals(loadedFile, path)) return;
        favorites.clear();
        favorites.addAll(CollectionFavoritesPersistence.load(path));
        loadedFile = path;
        loaded = true;
        revision++;
    }
}
