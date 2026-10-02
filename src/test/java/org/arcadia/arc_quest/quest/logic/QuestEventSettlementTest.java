package org.arcadia.arc_quest.quest.logic;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class QuestEventSettlementTest {
    @Test void everySignalUpdatesBeforeOneSettlementPerRun() {
        var batch = new QuestEventSettlement.Batch();
        var trace = new ArrayList<String>();
        Object run = new Object();
        batch.begin();
        trace.add("craft");
        assertTrue(batch.defer(run, () -> trace.add("settle")));
        trace.add("collect");
        assertTrue(batch.defer(run, () -> fail("Same run must not settle twice")));
        assertEquals(List.of("craft", "collect"), trace);
        batch.end();
        assertEquals(List.of("craft", "collect", "settle"), trace);
        assertTrue(batch.idle());
        assertFalse(batch.defer(run, () -> fail("No event should not defer")));
    }

    @Test void nestedCallbacksWaitUntilTheOuterEventCloses() {
        var batch = new QuestEventSettlement.Batch();
        var count = new AtomicInteger();
        batch.begin();
        batch.defer("a", count::incrementAndGet);
        batch.begin();
        batch.defer("b", count::incrementAndGet);
        batch.end();
        assertEquals(0, count.get());
        batch.end();
        assertEquals(2, count.get());
    }

    @Test void anIndependentEventDuringSettlementIsQueuedWithoutRecursiveSettlement() {
        var batch = new QuestEventSettlement.Batch();
        var trace = new ArrayList<String>();
        batch.begin();
        batch.defer("initial", () -> {
            trace.add("initial-start");
            batch.begin();
            batch.defer("following", () -> trace.add("following-settle"));
            batch.end();
            trace.add("initial-end");
        });
        batch.end();
        assertEquals(List.of("initial-start", "initial-end", "following-settle"), trace);
        assertTrue(batch.idle());
    }

    @Test void failedSettlementReleasesTransientState() {
        var batch = new QuestEventSettlement.Batch();
        batch.begin();
        batch.defer("run", () -> { throw new IllegalArgumentException("callback"); });
        assertThrows(IllegalArgumentException.class, batch::end);
        assertTrue(batch.idle());
        batch.begin();
        var count = new AtomicInteger();
        batch.defer("run", count::incrementAndGet);
        batch.end();
        assertEquals(1, count.get());
    }
}
