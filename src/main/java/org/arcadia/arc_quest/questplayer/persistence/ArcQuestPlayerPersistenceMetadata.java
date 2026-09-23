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
        if (isPayloadEmpty(first) && isPayloadEmpty(second)) return new CompoundTag();
        return compare(first, second) >= 0 ? first.copy() : second.copy();
    }

    /** 正数表示第一个快照更新；空载荷不会覆盖实际玩家数据。 */
    public static int compare(CompoundTag first, CompoundTag second) {
        boolean firstEmpty = isPayloadEmpty(first);
        boolean secondEmpty = isPayloadEmpty(second);
        if (firstEmpty || secondEmpty) return Boolean.compare(secondEmpty, firstEmpty);
        int revisionOrder = Long.compare(revision(first), revision(second));
        return revisionOrder != 0 ? revisionOrder : Long.compare(writtenAt(first), writtenAt(second));
    }

}
