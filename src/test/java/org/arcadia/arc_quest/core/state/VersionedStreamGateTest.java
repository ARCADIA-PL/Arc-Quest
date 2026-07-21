package org.arcadia.arc_quest.core.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VersionedStreamGateTest {

    @Test
    void acceptsSnapshotAndContiguousDelta() {
        VersionedStreamGate gate = new VersionedStreamGate();

        assertEquals(VersionedStreamGate.Decision.ACCEPT, gate.acceptSnapshot(5L, 10L));
        assertEquals(VersionedStreamGate.Decision.ACCEPT, gate.acceptDelta(5L, 10L, 11L));
        assertEquals(5L, gate.epoch());
        assertEquals(11L, gate.revision());
    }

    @Test
    void rejectsStalePacketsWithoutMovingCursor() {
        VersionedStreamGate gate = new VersionedStreamGate();
        gate.acceptSnapshot(5L, 10L);

        assertEquals(VersionedStreamGate.Decision.STALE, gate.acceptSnapshot(4L, 99L));
        assertEquals(VersionedStreamGate.Decision.STALE, gate.acceptDelta(5L, 9L, 10L));
        assertEquals(5L, gate.epoch());
        assertEquals(10L, gate.revision());
    }

    @Test
    void gapRequestsSingleResyncUntilSnapshotArrives() {
        VersionedStreamGate gate = new VersionedStreamGate();
        gate.acceptSnapshot(5L, 10L);

        assertEquals(VersionedStreamGate.Decision.GAP, gate.acceptDelta(5L, 12L, 13L));
        assertEquals(VersionedStreamGate.Decision.RESYNC_PENDING, gate.acceptDelta(5L, 13L, 14L));
        assertEquals(VersionedStreamGate.Decision.ACCEPT, gate.acceptSnapshot(5L, 14L));
        assertEquals(VersionedStreamGate.Decision.ACCEPT, gate.acceptDelta(5L, 14L, 15L));
    }

    @Test
    void newerEpochRequiresSnapshotBaseline() {
        VersionedStreamGate gate = new VersionedStreamGate();
        gate.acceptSnapshot(5L, 10L);

        assertEquals(VersionedStreamGate.Decision.GAP, gate.acceptDelta(6L, 0L, 1L));
        assertEquals(VersionedStreamGate.Decision.ACCEPT, gate.acceptSnapshot(6L, 1L));
    }

    @Test
    void legacyCompatibleModeAcceptsUntrackedPacketsWithoutMovingCursor() {
        VersionedStreamGate gate = new VersionedStreamGate(true);
        gate.acceptSnapshot(5L, 10L);

        assertEquals(VersionedStreamGate.Decision.ACCEPT, gate.acceptDelta(0L, 0L, 0L));
        assertEquals(5L, gate.epoch());
        assertEquals(10L, gate.revision());
    }

    @Test
    void clearResetsCursorAndPendingResync() {
        VersionedStreamGate gate = new VersionedStreamGate();
        gate.acceptSnapshot(5L, 10L);
        gate.acceptDelta(5L, 12L, 13L);

        gate.clear();

        assertEquals(-1L, gate.epoch());
        assertEquals(-1L, gate.revision());
        assertEquals(VersionedStreamGate.Decision.GAP, gate.acceptDelta(5L, 0L, 1L));
    }

    @Test
    void failedApplicationDoesNotAdvanceCursor() {
        VersionedStreamGate gate = new VersionedStreamGate();
        gate.acceptSnapshot(5L, 10L);

        assertThrows(IllegalStateException.class, () -> gate.applyDelta(5L, 10L, 11L, () -> {
            throw new IllegalStateException("failure");
        }));

        assertEquals(10L, gate.revision());
        assertEquals(VersionedStreamGate.Decision.ACCEPT, gate.acceptDelta(5L, 10L, 11L));
    }

    @Test
    void failedSnapshotApplicationDoesNotReplaceCursor() {
        VersionedStreamGate gate = new VersionedStreamGate();
        gate.acceptSnapshot(5L, 10L);

        assertThrows(IllegalStateException.class, () -> gate.applySnapshot(6L, 1L, () -> {
            throw new IllegalStateException("failure");
        }));

        assertEquals(5L, gate.epoch());
        assertEquals(10L, gate.revision());
    }
}
