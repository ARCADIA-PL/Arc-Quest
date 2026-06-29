package org.arcadia.arc_quest.questplayer.snapshot;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.migration.ArcQuestPlayerMigrationBundle;
import org.arcadia.arc_quest.questplayer.migration.ArcQuestPlayerMigrationMeta;
import org.arcadia.arc_quest.questplayer.migration.ArcQuestPlayerMigrationParser;
import org.arcadia.arc_quest.questplayer.migration.ArcQuestPlayerMigrationSections;
import org.arcadia.arc_quest.questplayer.migration.ArcQuestPlayerMigrationSerializer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FileArcQuestPlayerSnapshotStore implements ArcQuestPlayerSnapshotStore {

    public static final FileArcQuestPlayerSnapshotStore INSTANCE = new FileArcQuestPlayerSnapshotStore();

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withLocale(Locale.ROOT)
            .withZone(ZoneId.systemDefault());
    private static final Pattern SNAPSHOT_FILE_PATTERN = Pattern.compile(
            "^(?<uuid>[0-9a-fA-F\\-]{36})_(?<time>\\d{8}_\\d{6})_(?<reason>[a-z_]+)\\.dat$"
    );

    private final ArcQuestPlayerMigrationSerializer serializer = new ArcQuestPlayerMigrationSerializer();
    private final ArcQuestPlayerMigrationParser parser = new ArcQuestPlayerMigrationParser();

    private FileArcQuestPlayerSnapshotStore() {
    }

    @Override
    public ArcQuestPlayerSnapshotRef writeSnapshot(ServerPlayer player, ArcQuestPlayer data, ArcQuestSnapshotReason reason) {
        long createdAt = System.currentTimeMillis();
        String worldHint = sanitizePathSegment(player.server.getWorldData().getLevelName());
        Path directory = getPlayerDirectory(player.server, worldHint, player.getUUID());
        String fileName = player.getUUID() + "_" + FILE_TIME_FORMAT.format(Instant.ofEpochMilli(createdAt))
                + "_" + reason.name().toLowerCase(Locale.ROOT) + ".dat";
        Path path = directory.resolve(fileName);

        ArcQuestPlayerMigrationBundle bundle = new ArcQuestPlayerMigrationBundle(
                ArcQuestPlayerMigrationBundle.FORMAT,
                ArcQuestPlayerMigrationBundle.VERSION,
                buildMeta(player, data, createdAt, worldHint),
                buildSections(data)
        );
        CompoundTag root = serializer.serialize(bundle);

        try {
            Files.createDirectories(directory);
            NbtIo.writeCompressed(root, path);
            return new ArcQuestPlayerSnapshotRef(
                    player.getUUID(),
                    player.getGameProfile().getName(),
                    worldHint,
                    createdAt,
                    reason,
                    bundle.getVersion(),
                    path
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write ArcQuest snapshot: " + path, e);
        }
    }

    @Override
    public List<ArcQuestPlayerSnapshotRef> listSnapshots(UUID playerUuid) {
        Path root = getSnapshotsRoot();
        if (!Files.isDirectory(root)) {
            return List.of();
        }

        List<ArcQuestPlayerSnapshotRef> refs = new ArrayList<>();
        try (var worlds = Files.list(root)) {
            for (Path worldDir : worlds.filter(Files::isDirectory).toList()) {
                Path playerDir = worldDir.resolve("players").resolve(playerUuid.toString());
                if (!Files.isDirectory(playerDir)) {
                    continue;
                }
                try (var files = Files.list(playerDir)) {
                    for (Path path : files.filter(Files::isRegularFile).toList()) {
                        ArcQuestPlayerSnapshotRef ref = tryParseSnapshotRef(playerUuid, worldDir.getFileName().toString(), path);
                        if (ref != null) {
                            refs.add(ref);
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[ArcQuest] Failed to list snapshots for player {}", playerUuid, e);
        }

        refs.sort(Comparator.comparingLong(ArcQuestPlayerSnapshotRef::createdAt).reversed());
        return refs;
    }

    @Override
    public CompoundTag loadSnapshot(ArcQuestPlayerSnapshotRef ref) {
        return loadSnapshot(ref.path());
    }

    @Override
    public CompoundTag loadSnapshot(Path path) {
        try {
            return NbtIo.readCompressed(path, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read ArcQuest snapshot: " + path, e);
        }
    }

    @Override
    public void deleteSnapshot(ArcQuestPlayerSnapshotRef ref) {
        try {
            Files.deleteIfExists(ref.path());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete ArcQuest snapshot: " + ref.path(), e);
        }
    }

    private ArcQuestPlayerMigrationMeta buildMeta(ServerPlayer player, ArcQuestPlayer data, long createdAt, String worldHint) {
        Set<String> sections = new LinkedHashSet<>();
        sections.add("flagsVars");
        sections.add("questState");
        sections.add("dialogue");
        sections.add("trade");
        sections.add("gacha");
        sections.add("markers");
        return new ArcQuestPlayerMigrationMeta(
                player.getUUID(),
                player.getGameProfile().getName(),
                createdAt,
                playerDataVersion(data),
                modVersion(),
                worldHint,
                sections
        );
    }

    private ArcQuestPlayerMigrationSections buildSections(ArcQuestPlayer data) {
        CompoundTag full = data.serializeNBT();

        CompoundTag flagsVars = data.serializeFlagsVars();

        CompoundTag questState = new CompoundTag();
        if (full.contains("ActiveQuests")) questState.put("ActiveQuests", full.get("ActiveQuests").copy());
        if (full.contains("CompletedQuests")) questState.put("CompletedQuests", full.get("CompletedQuests").copy());
        if (full.contains("FailedQuests")) questState.put("FailedQuests", full.get("FailedQuests").copy());

        CompoundTag dialogue = full.contains("DialogueProgress") ? full.getCompound("DialogueProgress").copy() : new CompoundTag();
        CompoundTag trade = full.contains("TradeData") ? full.getCompound("TradeData").copy() : new CompoundTag();
        CompoundTag gacha = full.contains("GachaData") ? full.getCompound("GachaData").copy() : new CompoundTag();

        CompoundTag markers = new CompoundTag();
        if (full.contains("Markers")) {
            markers.put("Markers", full.get("Markers").copy());
        }

        return new ArcQuestPlayerMigrationSections(flagsVars, questState, dialogue, trade, gacha, markers);
    }

    private static Path getSnapshotsRoot() {
        return FMLPaths.GAMEDIR.get().resolve("arcquest_backups");
    }

    private static Path getPlayerDirectory(MinecraftServer server, String worldHint, UUID playerUuid) {
        return getSnapshotsRoot().resolve(worldHint).resolve("players").resolve(playerUuid.toString());
    }

    private ArcQuestPlayerSnapshotRef tryParseSnapshotRef(UUID playerUuid, String worldHint, Path path) {
        Matcher matcher = SNAPSHOT_FILE_PATTERN.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            return null;
        }

        if (!playerUuid.toString().equalsIgnoreCase(matcher.group("uuid"))) {
            return null;
        }

        long createdAt;
        try {
            createdAt = FILE_TIME_FORMAT.parse(matcher.group("time"), Instant::from).toEpochMilli();
        } catch (Exception ignored) {
            createdAt = path.toFile().lastModified();
        }

        ArcQuestSnapshotReason reason;
        try {
            reason = ArcQuestSnapshotReason.valueOf(matcher.group("reason").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            reason = ArcQuestSnapshotReason.MANUAL;
        }

        String playerName = "";
        int formatVersion = ArcQuestPlayerMigrationBundle.VERSION;
        String parsedWorldHint = worldHint;
        try {
            CompoundTag root = loadSnapshot(path);
            ArcQuestPlayerMigrationBundle bundle = parser.parse(root);
            playerName = bundle.getMeta().getSourcePlayerName();
            formatVersion = bundle.getVersion();
            if (!bundle.getMeta().getSourceWorldHint().isBlank()) {
                parsedWorldHint = bundle.getMeta().getSourceWorldHint();
            }
            if (bundle.getMeta().getExportedAt() > 0L) {
                createdAt = bundle.getMeta().getExportedAt();
            }
        } catch (Exception e) {
            LOGGER.debug("[ArcQuest] Failed to parse snapshot meta for {}", path, e);
        }

        return new ArcQuestPlayerSnapshotRef(
                playerUuid,
                playerName,
                parsedWorldHint,
                createdAt,
                reason,
                formatVersion,
                path
        );
    }

    private static int playerDataVersion(ArcQuestPlayer data) {
        CompoundTag tag = data.serializeNBT();
        return tag.getInt("_ArcQuestVer");
    }

    private static String modVersion() {
        Package pkg = Arc_Quest.class.getPackage();
        String version = pkg != null ? pkg.getImplementationVersion() : null;
        return version == null || version.isBlank() ? "unknown" : version;
    }

    private static String sanitizePathSegment(String value) {
        String sanitized = value == null ? "unknown_world" : value.trim();
        if (sanitized.isEmpty()) {
            sanitized = "unknown_world";
        }
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.isEmpty() ? "unknown_world" : sanitized;
    }
}
