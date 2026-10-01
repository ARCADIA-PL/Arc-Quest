package org.arcadia.arc_quest.questplayer;

import net.minecraft.nbt.CompoundTag;
import org.arcadia.arc_quest.questplayer.persistence.ArcQuestPlayerPersistenceMetadata;

/** 单个玩家会话的持久化时钟；计数器到达上限后用时间戳继续保持严格递增。 */
record PlayerPersistenceVersion(long revision, long writtenAt) {
    static final PlayerPersistenceVersion INITIAL = new PlayerPersistenceVersion(0L, 0L);

    static PlayerPersistenceVersion from(CompoundTag snapshot) {
        return new PlayerPersistenceVersion(ArcQuestPlayerPersistenceMetadata.revision(snapshot),
                ArcQuestPlayerPersistenceMetadata.writtenAt(snapshot));
    }

    PlayerPersistenceVersion next(long now) {
        if (revision < Long.MAX_VALUE) {
            return new PlayerPersistenceVersion(revision + 1L, Math.max(writtenAt, Math.max(0L, now)));
        }
        if (writtenAt == Long.MAX_VALUE) {
            throw new IllegalStateException("Player persistence version exhausted");
        }
        return new PlayerPersistenceVersion(Long.MAX_VALUE, Math.max(writtenAt + 1L, now));
    }
}
