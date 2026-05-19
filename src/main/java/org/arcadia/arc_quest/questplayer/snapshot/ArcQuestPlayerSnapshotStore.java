package org.arcadia.arc_quest.questplayer.snapshot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public interface ArcQuestPlayerSnapshotStore {

    ArcQuestPlayerSnapshotRef writeSnapshot(ServerPlayer player, ArcQuestPlayer data, ArcQuestSnapshotReason reason);

    List<ArcQuestPlayerSnapshotRef> listSnapshots(UUID playerUuid);

    CompoundTag loadSnapshot(ArcQuestPlayerSnapshotRef ref);

    CompoundTag loadSnapshot(Path path);

    void deleteSnapshot(ArcQuestPlayerSnapshotRef ref);
}
