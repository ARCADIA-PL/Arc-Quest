package org.arcadia.arc_quest.questplayer.snapshot;

import java.nio.file.Path;
import java.util.UUID;

public record ArcQuestPlayerSnapshotRef(
        UUID playerUuid,
        String playerName,
        String worldHint,
        long createdAt,
        ArcQuestSnapshotReason reason,
        int formatVersion,
        Path path) {
}
