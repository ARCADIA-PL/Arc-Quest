package org.arcadia.arc_quest.questplayer.persistence;

import net.minecraft.nbt.CompoundTag;

public final class ArcQuestPlayerPersistenceMetadata {

    private static final String REVISION_KEY = "ArcQuestPersistenceRevision";
    private static final String WRITTEN_AT_KEY = "ArcQuestPersistenceWrittenAt";

    private ArcQuestPlayerPersistenceMetadata() {
    }

    public static CompoundTag stamp(CompoundTag snapshot, long revision, long writtenAt) {
        CompoundTag stamped = snapshot.copy();
        stamped.putLong(REVISION_KEY, Math.max(0L, revision));
        stamped.putLong(WRITTEN_AT_KEY, Math.max(0L, writtenAt));
        return stamped;
    }

    public static long revision(CompoundTag snapshot) {
        return snapshot == null ? 0L : Math.max(0L, snapshot.getLong(REVISION_KEY));
    }

    public static long writtenAt(CompoundTag snapshot) {
        return snapshot == null ? 0L : Math.max(0L, snapshot.getLong(WRITTEN_AT_KEY));
    }

    public static boolean isPayloadEmpty(CompoundTag snapshot) {
        if (snapshot == null || snapshot.isEmpty()) return true;
        for (String key : snapshot.getAllKeys()) {
            if (!REVISION_KEY.equals(key) && !WRITTEN_AT_KEY.equals(key)) return false;
        }
        return true;
    }

    public static CompoundTag newer(CompoundTag first, CompoundTag second) {
        boolean firstEmpty = isPayloadEmpty(first);
        boolean secondEmpty = isPayloadEmpty(second);
        if (firstEmpty && secondEmpty) return new CompoundTag();
        if (firstEmpty) return second.copy();
        if (secondEmpty) return first.copy();

        long firstRevision = revision(first);
        long secondRevision = revision(second);
        if (firstRevision != secondRevision) {
            return firstRevision > secondRevision ? first.copy() : second.copy();
        }
        return writtenAt(first) >= writtenAt(second) ? first.copy() : second.copy();
    }

}
