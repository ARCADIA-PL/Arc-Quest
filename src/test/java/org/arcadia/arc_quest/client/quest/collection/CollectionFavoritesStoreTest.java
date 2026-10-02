package org.arcadia.arc_quest.client.quest.collection;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CollectionFavoritesStoreTest {
    @TempDir Path directory;
    private static final ResourceLocation ENTRY = ResourceLocation.parse("example:shared_entry");

    @Test void sharedEntryBookmarksPersistAcrossReloadsAndRemovalUpdatesTheRevision() {
        Path file = directory.resolve("favorites.json");
        var store = new CollectionFavoritesStore(() -> file);
        long before = store.revision();
        assertTrue(store.toggle(ENTRY));
        assertTrue(store.isFavorite(ResourceLocation.parse("example:shared_entry")));
        assertTrue(store.revision() > before);
        var restored = new CollectionFavoritesStore(() -> file);
        assertEquals(Set.of(ENTRY), restored.snapshot());
        long loadedRevision = restored.revision();
        assertFalse(restored.toggle(ENTRY));
        assertTrue(restored.revision() > loadedRevision);
        assertFalse(new CollectionFavoritesStore(() -> file).isFavorite(ENTRY));
    }

    @Test void scopeChangesNeverLeakBookmarksAcrossServersWorldsOrPlayers() {
        UUID player = UUID.randomUUID();
        Path first = CollectionFavoritesPersistence.fileForScope(directory, "server", "example.test:25565", player);
        Path otherServer = CollectionFavoritesPersistence.fileForScope(directory, "server", "other.test:25565", player);
        Path otherPlayer = CollectionFavoritesPersistence.fileForScope(directory, "server", "example.test:25565", UUID.randomUUID());
        Path world = CollectionFavoritesPersistence.fileForScope(directory, "world", "/saves/example.test:25565", player);
        assertEquals(4, Set.of(first, otherServer, otherPlayer, world).size());
        assertEquals(first, CollectionFavoritesPersistence.fileForScope(directory, "server", " EXAMPLE.TEST:25565 ", player));
        assertFalse(first.getFileName().toString().contains(player.toString()));
        var active = new AtomicReference<>(first);
        var store = new CollectionFavoritesStore(active::get);
        store.toggle(ENTRY);
        long revision = store.revision();
        for (Path next : new Path[]{otherServer, otherPlayer, world}) {
            active.set(next);
            assertFalse(store.isFavorite(ENTRY));
            assertTrue(store.revision() > revision);
            revision = store.revision();
        }
        active.set(first);
        assertTrue(store.isFavorite(ENTRY));
    }

    @Test void frameQueriesUseTheLoadedSetWithoutReloadingTheDisk() throws Exception {
        Path file = directory.resolve("favorites.json");
        var store = new CollectionFavoritesStore(() -> file);
        store.toggle(ENTRY);
        long revision = store.revision();
        Files.writeString(file, "{\"schemaVersion\":1,\"entries\":[]}");
        for (int i = 0; i < 100; i++) {
            assertTrue(store.isFavorite(ENTRY));
            assertEquals(revision, store.revision());
        }
        assertFalse(new CollectionFavoritesStore(() -> file).isFavorite(ENTRY));
    }

    @Test void invalidIdsAreIgnoredAndDamagedFilesAreRetainedForRecovery() throws Exception {
        Path file = directory.resolve("favorites.json");
        Files.writeString(file, "{\"schemaVersion\":1,\"entries\":[\"example:shared_entry\",\"example:shared_entry\",null,\"Invalid ID\"]}");
        assertEquals(Set.of(ENTRY), new CollectionFavoritesStore(() -> file).snapshot());
        Files.writeString(file, "{broken");
        var fresh = new CollectionFavoritesStore(() -> file);
        assertTrue(fresh.snapshot().isEmpty());
        assertEquals("{broken", Files.readString(file.resolveSibling("favorites.json.broken")));
        assertTrue(fresh.toggle(ENTRY));
        assertTrue(new CollectionFavoritesStore(() -> file).isFavorite(ENTRY));
    }

    @Test void disconnectedBookmarksAreEphemeralAndDoNotBecomeAnotherPlayersFavorites() {
        var active = new AtomicReference<Path>();
        var store = new CollectionFavoritesStore(active::get);
        assertTrue(store.toggle(ENTRY));
        assertTrue(store.isFavorite(ENTRY));
        assertFalse(new CollectionFavoritesStore(() -> null).isFavorite(ENTRY));
        active.set(directory.resolve("player.json"));
        assertFalse(store.isFavorite(ENTRY));
    }
}
