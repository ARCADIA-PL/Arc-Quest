package org.arcadia.arc_quest.questplayer.restore;

import net.minecraft.nbt.CompoundTag;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerRestoreCommitTest {
    @Test
    void publishesOnlyAfterSuccessfulPersistence() {
        ArcQuestPlayer target = player(1);
        ArcQuestPlayer candidate = player(2);
        CompoundTag before = target.serializeNBT();
        PlayerRestoreCommit.apply(target, candidate, saved -> {
            assertSame(candidate, saved);
            assertEquals(before, target.serializeNBT());
        });
        assertEquals(candidate.serializeNBT(), target.serializeNBT());
    }

    @Test
    void failedWriteCompensatesPersistenceAndPreservesLiveData() {
        ArcQuestPlayer target = player(1);
        ArcQuestPlayer candidate = player(2);
        CompoundTag before = target.serializeNBT();
        var writes = new ArrayList<CompoundTag>();
        RuntimeException diskFailure = new IllegalStateException("disk failure");
        RuntimeException actual = assertThrows(RuntimeException.class, () -> PlayerRestoreCommit.apply(target, candidate, saved -> {
            writes.add(saved.serializeNBT());
            if (saved == candidate) throw diskFailure;
        }));
        assertSame(diskFailure, actual);
        assertEquals(2, writes.size());
        assertEquals(before, writes.get(1));
        assertEquals(before, target.serializeNBT());
        assertTrue(target.isDirty());
    }

    @Test
    void reportsBothWriteAndCompensationFailureWithoutChangingLiveData() {
        ArcQuestPlayer target = player(1);
        CompoundTag before = target.serializeNBT();
        RuntimeException failure = assertThrows(RuntimeException.class, () -> PlayerRestoreCommit.apply(target, player(2), saved -> {
            throw new IllegalStateException("unavailable");
        }));
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(before, target.serializeNBT());
    }

    private static ArcQuestPlayer player(int count) {
        ArcQuestPlayer player = new ArcQuestPlayer(UUID.randomUUID());
        player.setGachaPityCounter("shop", count);
        return player;
    }
}
