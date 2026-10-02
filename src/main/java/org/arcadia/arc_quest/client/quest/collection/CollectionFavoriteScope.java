package org.arcadia.arc_quest.client.quest.collection;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/** Resolves local storage without sending bookmarks or player preferences to the server. */
final class CollectionFavoriteScope {
    private static Object connection, server;
    private static UUID playerId;
    private static String serverAddress;
    private static Path file;

    private CollectionFavoriteScope() {}

    static Path currentFile() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return null;
        Object currentConnection = minecraft.getConnection();
        var currentServer = minecraft.getSingleplayerServer();
        UUID currentPlayer = minecraft.player == null ? null : minecraft.player.getUUID();
        var serverData = minecraft.getCurrentServer();
        String address = serverData == null ? null : serverData.ip;
        if (connection == currentConnection && server == currentServer && Objects.equals(playerId, currentPlayer)
                && Objects.equals(serverAddress, address)) return file;
        connection = currentConnection; server = currentServer; playerId = currentPlayer; serverAddress = address;
        file = null;
        if (currentConnection == null || currentPlayer == null) return null;
        Path directory = minecraft.gameDirectory.toPath().resolve("config").resolve("arc_quest").resolve("collection-favorites");
        if (currentServer != null) {
            Path world = currentServer.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            file = CollectionFavoritesPersistence.fileForScope(directory, "world", world.toString(), currentPlayer);
        } else if (address != null && !address.isBlank()) {
            file = CollectionFavoritesPersistence.fileForScope(directory, "server", address, currentPlayer);
        }
        return file;
    }
}
