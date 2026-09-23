package org.arcadia.arc_quest.dialogue.network;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClientDialogueCacheTest {
    private final ClientDialogueCache cache = ClientDialogueCache.INSTANCE;
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();

    @BeforeEach
    @AfterEach
    void clear() {
        cache.clear();
    }

    @Test
    void snapshotCannotSwitchSessionAndOpenAdoptsStagedHistory() {
        assertTrue(update(first, 1, 1, true, "first"));
        cache.replaceTranscriptSnapshot(second, List.of(entry("second")));
        assertEquals(first, cache.getCurrentSessionId());
        assertEquals("first", cache.getCurrentSession().treeId);
        assertTrue(update(second, 1, 1, true, "second"));
        assertEquals("second", cache.getCurrentTranscript().get(0).text().getString());
    }

    @Test
    void rejectedOpenLeavesCurrentSessionAndTranscriptIntact() {
        assertTrue(update(first, 1, 5, true, "first"));
        cache.replaceTranscriptSnapshot(first, List.of(entry("original")));
        assertFalse(update(second, 1, 4, true, "second"));
        assertFalse(update(first, 2, 5, true, "other tree"));
        assertEquals(first, cache.getCurrentSessionId());
        assertEquals("original", cache.getCurrentTranscript().get(0).text().getString());
    }

    @Test
    void duplicateRevisionAndClosedSessionCannotReopen() {
        assertTrue(update(first, 1, 5, true, "first"));
        assertFalse(update(first, 1, 5, false, "first"));
        assertTrue(update(first, 2, 5, false, "first"));
        assertTrue(cache.closeSession(first, 5));
        cache.replaceTranscriptSnapshot(first, List.of(entry("late")));
        assertFalse(update(first, 3, 5, true, "first"));
        assertNull(cache.getCurrentSession());
        assertTrue(cache.getCurrentTranscript().isEmpty());
    }

    @Test
    void staleCloseDoesNotRemoveCurrentSession() {
        assertTrue(update(first, 1, 5, true, "first"));
        assertFalse(cache.closeSession(first, 4));
        assertEquals(first, cache.getCurrentSessionId());
        assertTrue(update(second, 1, 5, true, "second"));
        assertFalse(cache.closeSession(first, 5));
        assertEquals(second, cache.getCurrentSessionId());
    }

    private boolean update(UUID id, long revision, long epoch, boolean open, String tree) {
        return cache.updateFromPacket(id, revision, epoch, open, tree, "node", Component.empty(), Component.empty(),
                new Component[0], false, false, 0, -1, null, null, null, null, null, null, null, null, null, null);
    }

    private static S2CDialogueTranscriptDeltaPacket.Entry entry(String text) {
        return new S2CDialogueTranscriptDeltaPacket.Entry(1, "npc", null, Component.literal(text), null, null, null, -1);
    }
}
