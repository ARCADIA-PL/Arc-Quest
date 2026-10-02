package org.arcadia.arc_quest.client.quest.collection;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

final class CollectionFavoritesPersistence {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private CollectionFavoritesPersistence() {}

    static Path fileForScope(Path directory, String kind, String identity, UUID player) {
        String normalized = kind.equals("server") ? identity.strip().toLowerCase(Locale.ROOT) : identity;
        // The filename contains neither the server address nor the player's identity.
        String scope = kind + "\0" + normalized + "\0" + player;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8));
            return directory.resolve(HexFormat.of().formatHex(digest) + ".json");
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    static Set<ResourceLocation> load(Path file) {
        if (file == null || !Files.isRegularFile(file)) return Set.of();
        try {
            if (Files.size(file) > 4_194_304) throw new IllegalArgumentException("Bookmarks exceed the supported file size");
            SavedFavorites saved = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), SavedFavorites.class);
            if (saved == null || saved.entries == null) return Set.of();
            Set<ResourceLocation> result = new LinkedHashSet<>();
            for (String raw : saved.entries) {
                if (raw == null) continue;
                ResourceLocation id = ResourceLocation.tryParse(raw);
                if (id != null) result.add(id);
            }
            return result;
        } catch (Exception exception) {
            // Preserve a damaged file for recovery; a later user toggle may save a fresh set.
            try { Files.copy(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING); }
            catch (Exception ignored) {}
            ArcQuestLog.warn(ArcQuestLog.Category.QUEST, "Failed to load local collection favorites.", exception);
            return Set.of();
        }
    }

    static void save(Path file, Set<ResourceLocation> favorites) {
        if (file == null) return; // Disconnected menu fixtures have ephemeral bookmarks only.
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "collection-favorites-", ".tmp");
            var snapshot = new SavedFavorites();
            snapshot.entries = favorites.stream().map(ResourceLocation::toString).sorted().toList();
            Files.writeString(temporary, GSON.toJson(snapshot), StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unavailable) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception exception) {
            ArcQuestLog.warn(ArcQuestLog.Category.QUEST, "Failed to save local collection favorites.", exception);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (Exception ignored) {}
        }
    }

    private static final class SavedFavorites {
        int schemaVersion = 1;
        List<String> entries = List.of();
    }
}
