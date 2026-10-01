package org.arcadia.arc_quest.questplayer;

import net.minecraft.nbt.CompoundTag;
import org.arcadia.arc_quest.core.identity.PlayerSessionRef;
import org.arcadia.arc_quest.questplayer.persistence.ArcQuestPlayerPersistenceMetadata;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerStateSessionsTest {
    private final UUID uuid = UUID.randomUUID();
    private final PlayerSessionRef session = new PlayerSessionRef(uuid, 10);
    private final PlayerStateSessions sessions = new PlayerStateSessions(() -> 100);
    private final Storage storage = new Storage();

    @Test
    void restoringEpochPreservesObjectStoreIdentityAndPersistenceVersion() {
        ArcQuestPlayer data = sessions.getOrCreate(session, storage);
        data.setGachaPityCounter("shop", 7);
        sessions.persist(session, data, storage, false);
        long version = revision(storage.saved);
        var store = data.getGachaDataStore();
        var renewed = new PlayerSessionRef(uuid, 11);
        int writes = storage.saveWrites;
        sessions.rebind(session, renewed);
        assertEquals(writes, storage.saveWrites);
        assertNull(sessions.get(session));
        assertSame(data, sessions.get(renewed));
        assertSame(store, sessions.get(renewed).getGachaDataStore());
        assertEquals(7, sessions.get(renewed).getGachaPityCounter("shop"));
        sessions.persist(renewed, data, storage, false);
        assertTrue(revision(storage.saved) > version);
        assertThrows(IllegalStateException.class, () -> sessions.persist(session, data, storage, false));
    }

    @Test
    void invalidRenewalLeavesCurrentSessionUntouched() {
        ArcQuestPlayer data = sessions.getOrCreate(session, storage);
        assertThrows(IllegalArgumentException.class, () -> sessions.rebind(session, session));
        assertThrows(IllegalArgumentException.class, () -> sessions.rebind(session, new PlayerSessionRef(uuid, 9)));
        assertThrows(IllegalArgumentException.class,
                () -> sessions.rebind(session, new PlayerSessionRef(UUID.randomUUID(), 11)));
        assertThrows(IllegalStateException.class,
                () -> sessions.rebind(new PlayerSessionRef(uuid, 9), new PlayerSessionRef(uuid, 11)));
        assertSame(data, sessions.get(session));
        assertEquals(1, sessions.size());
    }

    @Test
    void coldCloneUsesTheLatestCheckpointAndContinuesItsVersion() {
        storage.saved = snapshot(uuid, 2, 5, 90);
        storage.checkpoint = snapshot(uuid, 8, 5, 95);
        Storage destination = new Storage();
        sessions.clone(session, session, storage, destination);
        assertEquals(8, sessions.get(session).getGachaPityCounter("shop"));
        assertEquals(6, revision(destination.saved));
        assertEquals(destination.saved, destination.checkpoint);
        assertEquals(1, sessions.size());
        assertTrue(destination.lastSynchronous);
    }

    @Test
    void warmClonePreservesUnsavedProgressAndPublishesANewObject() {
        ArcQuestPlayer original = sessions.getOrCreate(session, storage);
        original.setGachaPityCounter("shop", 7);
        Storage destination = new Storage();
        sessions.clone(session, session, storage, destination);
        ArcQuestPlayer replacement = sessions.get(session);
        assertNotSame(original, replacement);
        assertEquals(7, replacement.getGachaPityCounter("shop"));
        original.setGachaPityCounter("shop", 99);
        assertEquals(7, replacement.getGachaPityCounter("shop"));
    }

    @Test
    void cloneFailureRetainsTheSourceAndStillAttemptsCheckpoint() {
        ArcQuestPlayer original = sessions.getOrCreate(session, storage);
        original.setGachaPityCounter("shop", 7);
        Storage destination = new Storage();
        destination.failSave = true;
        assertThrows(IllegalStateException.class, () -> sessions.clone(session, session, storage, destination));
        assertSame(original, sessions.get(session));
        assertEquals(1, destination.checkpointWrites);
        long attempted = revision(destination.checkpoint);
        destination.failSave = false;
        sessions.clone(session, session, storage, destination);
        assertTrue(revision(destination.saved) > attempted);
        assertEquals(7, sessions.get(session).getGachaPityCounter("shop"));
    }

    @Test
    void bothWriteFailuresAreReportedAndLiveStateIsKept() {
        ArcQuestPlayer original = sessions.getOrCreate(session, storage);
        storage.failSave = true;
        storage.failCheckpoint = true;
        RuntimeException failure = assertThrows(RuntimeException.class, () -> sessions.persistAndUnload(session, storage));
        assertEquals(1, failure.getSuppressed().length);
        assertSame(original, sessions.get(session));
        assertEquals(1, storage.saveWrites);
        assertEquals(1, storage.checkpointWrites);
    }

    @Test
    void reconnectRecoversStateRetainedAfterFailedLogout() {
        ArcQuestPlayer original = sessions.getOrCreate(session, storage);
        original.setGachaPityCounter("shop", 7);
        storage.failSave = true;
        storage.failCheckpoint = true;
        assertThrows(RuntimeException.class, () -> sessions.persistAndUnload(session, storage));
        storage.failSave = false;
        storage.failCheckpoint = false;
        PlayerSessionRef nextSession = new PlayerSessionRef(uuid, 11);
        ArcQuestPlayer reconnected = sessions.getOrCreate(nextSession, storage);
        assertEquals(7, reconnected.getGachaPityCounter("shop"));
        assertNotSame(original, reconnected);
        assertNull(sessions.get(session));
        assertEquals(1, sessions.size());
        assertEquals(storage.saved, storage.checkpoint);
        assertEquals(7, read(storage.saved).getGachaPityCounter("shop"));
    }

    @Test
    void advancingLoginEpochDoesNotDiscardChangesMadeBeforeTheLoginEvent() {
        storage.saved = snapshot(uuid, 1, 3, 90);
        storage.checkpoint = storage.saved.copy();
        ArcQuestPlayer original = sessions.getOrCreate(session, storage);
        original.setGachaPityCounter("shop", 7);
        // 同步清脏不等于磁盘落盘，不能只依据 dirty 标记决定是否保留。
        original.clearDirty();
        ArcQuestPlayer reconnected = sessions.getOrCreate(new PlayerSessionRef(uuid, 11), storage);
        assertEquals(7, reconnected.getGachaPityCounter("shop"));
        assertTrue(revision(storage.saved) > 3);
        assertEquals(7, read(storage.saved).getGachaPityCounter("shop"));
    }

    @Test
    void olderSessionCannotOverwriteOrUnloadTheReplacement() {
        sessions.getOrCreate(session, storage);
        PlayerSessionRef nextSession = new PlayerSessionRef(uuid, 11);
        ArcQuestPlayer current = sessions.getOrCreate(nextSession, storage);
        int writes = storage.saveWrites;
        sessions.persistAndUnload(session, storage);
        assertSame(current, sessions.get(nextSession));
        assertThrows(IllegalStateException.class, () -> sessions.getOrCreate(session, storage));
        assertThrows(IllegalStateException.class, () -> sessions.persist(session, current, storage, false));
        assertEquals(writes, storage.saveWrites);
    }

    @Test
    void checkpointReadFailureCannotBeMistakenForAnEmptyPlayer() {
        storage.failRead = true;
        assertThrows(IllegalStateException.class, () -> sessions.getOrCreate(session, storage));
        assertNull(sessions.get(session));
        assertEquals(0, storage.saveWrites);
        storage.failRead = false;
        storage.checkpoint = snapshot(uuid, 7, 3, 90);
        assertEquals(7, sessions.getOrCreate(session, storage).getGachaPityCounter("shop"));
    }

    @Test
    void failedRecoveryLeavesThePreviousSessionAvailableForRetry() {
        ArcQuestPlayer original = sessions.getOrCreate(session, storage);
        original.setGachaPityCounter("shop", 7);
        storage.failSave = true;
        PlayerSessionRef nextSession = new PlayerSessionRef(uuid, 11);
        assertThrows(IllegalStateException.class, () -> sessions.getOrCreate(nextSession, storage));
        assertSame(original, sessions.get(session));
        assertNull(sessions.get(nextSession));
        storage.failSave = false;
        assertEquals(7, sessions.getOrCreate(nextSession, storage).getGachaPityCounter("shop"));
    }

    @Test
    void savingWithoutACachedSessionSeedsTheVersionFromStorage() {
        storage.saved = snapshot(uuid, 1, 99, 200);
        storage.checkpoint = storage.saved.copy();
        ArcQuestPlayer replacement = new ArcQuestPlayer(uuid);
        replacement.setGachaPityCounter("shop", 7);
        sessions.persist(session, replacement, storage, false);
        assertEquals(100, revision(storage.saved));
        assertEquals(200, ArcQuestPlayerPersistenceMetadata.writtenAt(storage.saved));
        assertEquals(7, read(storage.saved).getGachaPityCounter("shop"));
    }

    @Test
    void repositoryMutationCannotCorruptTheIndependentCheckpoint() {
        ArcQuestPlayer data = sessions.getOrCreate(session, storage);
        data.setGachaPityCounter("shop", 7);
        storage.mutateSavedInput = true;
        sessions.persist(session, data, storage, false);
        assertEquals(7, read(storage.checkpoint).getGachaPityCounter("shop"));
        assertEquals(1, revision(storage.checkpoint));
    }

    @Test
    void successfulUnloadRemovesTheRuntimeStateAndReloadsItsProgress() {
        ArcQuestPlayer original = sessions.getOrCreate(session, storage);
        original.setGachaPityCounter("shop", 7);
        sessions.persistAndUnload(session, storage);
        assertNull(sessions.get(session));
        assertEquals(0, sessions.size());
        assertEquals(7, sessions.getOrCreate(new PlayerSessionRef(uuid, 11), storage).getGachaPityCounter("shop"));
    }

    @Test
    void crossPlayerCloneUsesTheDestinationVersionWatermark() {
        ArcQuestPlayer source = sessions.getOrCreate(session, storage);
        source.setGachaPityCounter("shop", 7);
        UUID destinationId = UUID.randomUUID();
        PlayerSessionRef destinationSession = new PlayerSessionRef(destinationId, 12);
        Storage destination = new Storage();
        destination.saved = snapshot(destinationId, 1, 99, 200);
        destination.checkpoint = destination.saved.copy();
        sessions.clone(session, destinationSession, storage, destination);
        assertEquals(100, revision(destination.saved));
        assertEquals(destinationId, sessions.get(destinationSession).getOwnerUuid());
        assertEquals(7, sessions.get(destinationSession).getGachaPityCounter("shop"));
        assertNull(sessions.get(session));
    }

    @Test
    void ownerMismatchIsRejectedBeforeAnyStorageAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> sessions.persist(session, new ArcQuestPlayer(UUID.randomUUID()), storage, false));
        assertEquals(0, storage.reads);
        assertEquals(0, storage.saveWrites);
    }

    @Test
    void repeatedAccessDoesNotReloadOrRewriteThePlayer() {
        ArcQuestPlayer data = sessions.getOrCreate(session, storage);
        assertSame(data, sessions.getOrCreate(session, storage));
        assertEquals(2, storage.reads);
        assertEquals(0, storage.saveWrites);
    }

    @Test
    void failedExitCapacityBlocksNewAdmissionsWithoutEvictingUnsavedPlayers() {
        PlayerStateSessions bounded = new PlayerStateSessions(() -> 100, 1);
        ArcQuestPlayer original = bounded.getOrCreate(session, storage);
        original.setGachaPityCounter("shop", 7);
        storage.failSave = true;
        storage.failCheckpoint = true;
        assertThrows(RuntimeException.class, () -> bounded.persistAndUnload(session, storage));
        PlayerSessionRef newcomer = new PlayerSessionRef(UUID.randomUUID(), 20);
        assertThrows(IllegalStateException.class, () -> bounded.getOrCreate(newcomer, new Storage()));
        assertSame(original, bounded.get(session));
        storage.failSave = false;
        storage.failCheckpoint = false;
        ArcQuestPlayer recovered = bounded.getOrCreate(new PlayerSessionRef(uuid, 11), storage);
        assertEquals(7, recovered.getGachaPityCounter("shop"));
        assertNotNull(bounded.getOrCreate(newcomer, new Storage()));
    }

    private static long revision(CompoundTag tag) { return ArcQuestPlayerPersistenceMetadata.revision(tag); }

    private static ArcQuestPlayer read(CompoundTag tag) {
        ArcQuestPlayer player = new ArcQuestPlayer(UUID.randomUUID());
        player.deserializeNBT(tag);
        return player;
    }

    private static CompoundTag snapshot(UUID owner, int progress, long revision, long time) {
        ArcQuestPlayer player = new ArcQuestPlayer(owner);
        player.setGachaPityCounter("shop", progress);
        return ArcQuestPlayerPersistenceMetadata.stamp(player.serializeNBT(), revision, time);
    }

    private static final class Storage implements PlayerStateSessions.Storage {
        CompoundTag saved = new CompoundTag();
        CompoundTag checkpoint = new CompoundTag();
        boolean failRead;
        boolean failSave;
        boolean failCheckpoint;
        boolean mutateSavedInput;
        boolean lastSynchronous;
        int reads;
        int saveWrites;
        int checkpointWrites;

        @Override
        public CompoundTag loadSaved() { reads++; return saved.copy(); }

        @Override
        public CompoundTag loadCheckpoint() {
            reads++;
            if (failRead) throw new IllegalStateException("checkpoint unavailable");
            return checkpoint.copy();
        }

        @Override
        public void save(CompoundTag snapshot) {
            saveWrites++;
            if (failSave) throw new IllegalStateException("repository unavailable");
            saved = snapshot.copy();
            if (mutateSavedInput) {
                for (String key : java.util.Set.copyOf(snapshot.getAllKeys())) snapshot.remove(key);
            }
        }

        @Override
        public void checkpoint(CompoundTag snapshot, boolean synchronous) {
            checkpointWrites++;
            if (failCheckpoint) throw new IllegalStateException("checkpoint unavailable");
            checkpoint = snapshot.copy();
            lastSynchronous = synchronous;
        }
    }
}
