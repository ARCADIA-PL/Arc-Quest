package org.arcadia.arc_quest.dialogue.network;

import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.dialogue.network.ClientDialogueCache.TranscriptEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ClientDialogueTranscriptsTest {
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();

    @Test
    void preOpenSnapshotAndDeltaAreStagedWithoutReplacingActiveTranscript() {
        var store = new ClientDialogueTranscripts();
        store.activate(first);
        store.append(first, entry("first"));
        store.replace(second, List.of(wire("second")));
        store.append(second, entry("next"));
        assertEquals(List.of("first"), texts(store, first));
        assertTrue(store.current(second).isEmpty());
        store.activate(second);
        assertEquals(List.of("second", "next"), texts(store, second));
        assertTrue(store.current(first).isEmpty());
    }

    @Test
    void closingOrReplacingSessionRejectsLateHistory() {
        var store = new ClientDialogueTranscripts();
        store.activate(first);
        store.activate(second);
        store.append(second, entry("current"));
        store.replace(first, List.of(wire("late")));
        store.append(first, entry("late delta"));
        assertEquals(List.of("current"), texts(store, second));
        store.close(second);
        store.append(second, entry("after close"));
        assertTrue(store.current(second).isEmpty());
        assertTrue(store.isRetired(first));
        assertTrue(store.isRetired(second));
    }

    @Test
    void countWindowKeepsLatestEntriesAndPublishesStableReadOnlySnapshots() {
        var store = new ClientDialogueTranscripts(2, 4096, () -> 0);
        store.activate(first);
        store.append(first, entry("a"));
        store.append(first, entry("b"));
        var previous = store.current(first);
        assertSame(previous, store.current(first));
        assertThrows(UnsupportedOperationException.class, () -> previous.add(entry("mutation")));
        store.append(first, entry("c"));
        assertEquals(List.of("b", "c"), texts(store, first));
        assertEquals(List.of("a", "b"), previous.stream().map(e -> e.text().getString()).toList());
        assertNotSame(previous, store.current(first));
    }

    @Test
    void textBudgetAlsoEvictsOldEntries() {
        var store = new ClientDialogueTranscripts(100, 160, () -> 0);
        store.activate(first);
        store.append(first, entry("a".repeat(30)));
        store.append(first, entry("b".repeat(30)));
        assertEquals(List.of("b".repeat(30)), texts(store, first));
    }

    @Test
    void failedSnapshotPreparationDoesNotDiscardCurrentHistory() {
        var store = new ClientDialogueTranscripts(10, 160, () -> 0);
        store.activate(first);
        store.append(first, entry("original"));
        assertThrows(IllegalArgumentException.class, () -> store.replace(first, List.of(wire("x".repeat(500)))));
        assertEquals(List.of("original"), texts(store, first));
    }

    @Test
    void incomingMutableComponentsAreCopied() {
        var store = new ClientDialogueTranscripts();
        store.activate(first);
        var text = Component.literal("before");
        store.append(first, new TranscriptEntry(1, "npc", text, text, null, null, null, null));
        text.append(" after");
        assertEquals("before", store.current(first).get(0).speaker().getString());
        assertEquals(List.of("before"), texts(store, first));
    }

    @Test
    void unclaimedHistoryExpiresAndClearReleasesPendingAndClosedState() {
        AtomicLong clock = new AtomicLong();
        var store = new ClientDialogueTranscripts(10, 4096, clock::get);
        store.replace(first, List.of(wire("pending")));
        clock.set(30_000_000_000L);
        store.activate(first);
        assertTrue(store.current(first).isEmpty());
        store.close(first);
        store.replace(second, List.of(wire("pending second")));
        store.clear();
        assertFalse(store.isRetired(first));
        store.activate(second);
        assertTrue(store.current(second).isEmpty());
    }

    @Test
    void onlyOneUnclaimedSessionIsRetained() {
        var store = new ClientDialogueTranscripts();
        store.replace(first, List.of(wire("first pending")));
        store.replace(second, List.of(wire("second pending")));
        store.activate(first);
        assertTrue(store.current(first).isEmpty());
    }

    private static TranscriptEntry entry(String text) {
        return new TranscriptEntry(1, "npc", Component.empty(), Component.literal(text), null, null, null, null);
    }

    private static S2CDialogueTranscriptDeltaPacket.Entry wire(String text) {
        return new S2CDialogueTranscriptDeltaPacket.Entry(1, "npc", Component.empty(), Component.literal(text), null, null, null, -1);
    }

    private static List<String> texts(ClientDialogueTranscripts store, UUID id) {
        return store.current(id).stream().map(entry -> entry.text().getString()).toList();
    }
}
