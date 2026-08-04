package org.arcadia.arc_quest.questplayer.attachment;

import net.minecraft.nbt.CompoundTag;
import org.arcadia.arc_quest.questplayer.persistence.ArcQuestPlayerPersistenceMetadata;

public final class ArcQuestPlayerAttachment {

    private final long revision;
    private final long writtenAt;
    private final CompoundTag snapshot;
    private final boolean empty;

    private ArcQuestPlayerAttachment(long revision, long writtenAt, CompoundTag snapshot) {
        this.revision = Math.max(0L, revision);
        this.writtenAt = Math.max(0L, writtenAt);
        this.empty = ArcQuestPlayerPersistenceMetadata.isPayloadEmpty(snapshot);
        this.snapshot = ArcQuestPlayerPersistenceMetadata.stamp(snapshot, this.revision, this.writtenAt);
    }

    public static ArcQuestPlayerAttachment empty() {
        return new ArcQuestPlayerAttachment(0L, 0L, new CompoundTag());
    }

    public static ArcQuestPlayerAttachment fromSnapshot(CompoundTag snapshot) {
        return new ArcQuestPlayerAttachment(
                ArcQuestPlayerPersistenceMetadata.revision(snapshot),
                ArcQuestPlayerPersistenceMetadata.writtenAt(snapshot),
                snapshot);
    }

    public static ArcQuestPlayerAttachment of(long revision, long writtenAt, CompoundTag snapshot) {
        return new ArcQuestPlayerAttachment(revision, writtenAt, snapshot);
    }

    public long revision() {
        return revision;
    }

    public long writtenAt() {
        return writtenAt;
    }

    public CompoundTag snapshot() {
        return snapshot.copy();
    }

    public boolean isEmpty() {
        return empty;
    }
}
