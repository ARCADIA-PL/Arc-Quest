package org.arcadia.arc_quest.quest.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestClientRevisionGateTest {

    @Test
    void acceptsSnapshotAndContiguousDeltas() {
        QuestClientRevisionGate gate = new QuestClientRevisionGate();

        assertEquals(QuestClientRevisionGate.Decision.ACCEPT, gate.acceptSnapshot(10L, 4L));
        assertEquals(QuestClientRevisionGate.Decision.ACCEPT, gate.acceptDelta(10L, 4L, 5L));
        assertEquals(5L, gate.revision());
    }

    @Test
    void gapRequestsOneResyncUntilSnapshotArrives() {
        QuestClientRevisionGate gate = new QuestClientRevisionGate();
        gate.acceptSnapshot(10L, 4L);

        assertEquals(QuestClientRevisionGate.Decision.GAP, gate.acceptDelta(10L, 6L, 7L));
        assertEquals(QuestClientRevisionGate.Decision.RESYNC_PENDING, gate.acceptDelta(10L, 7L, 8L));
        assertEquals(QuestClientRevisionGate.Decision.ACCEPT, gate.acceptSnapshot(10L, 8L));
        assertEquals(QuestClientRevisionGate.Decision.ACCEPT, gate.acceptDelta(10L, 8L, 9L));
    }

    @Test
    void rejectsPacketsFromOlderLoginEpoch() {
        QuestClientRevisionGate gate = new QuestClientRevisionGate();
        gate.acceptSnapshot(20L, 2L);

        assertEquals(QuestClientRevisionGate.Decision.STALE, gate.acceptSnapshot(19L, 99L));
        assertEquals(QuestClientRevisionGate.Decision.STALE, gate.acceptDelta(19L, 2L, 3L));
        assertEquals(20L, gate.playerSessionEpoch());
        assertEquals(2L, gate.revision());
    }

    @Test
    void newerEpochDeltaRequiresSnapshotBaseline() {
        QuestClientRevisionGate gate = new QuestClientRevisionGate();
        gate.acceptSnapshot(20L, 5L);

        assertEquals(QuestClientRevisionGate.Decision.GAP, gate.acceptDelta(21L, 0L, 1L));
        assertEquals(QuestClientRevisionGate.Decision.ACCEPT, gate.acceptSnapshot(21L, 1L));
    }
}
