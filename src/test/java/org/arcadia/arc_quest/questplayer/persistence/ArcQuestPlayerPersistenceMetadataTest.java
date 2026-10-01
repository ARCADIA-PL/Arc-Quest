package org.arcadia.arc_quest.questplayer.persistence;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArcQuestPlayerPersistenceMetadataTest {
    @Test
    void comparisonAndSelectionAgreeForEqualRevisions() {
        CompoundTag first = payload(7, 20);
        CompoundTag second = payload(7, 30);
        assertTrue(ArcQuestPlayerPersistenceMetadata.compare(first, second) < 0);
        assertEquals(second, ArcQuestPlayerPersistenceMetadata.newer(first, second));
        assertNotSame(second, ArcQuestPlayerPersistenceMetadata.newer(first, second));
    }

    @Test
    void emptyPayloadCannotOverridePlayerDataEvenWithAHigherRevision() {
        CompoundTag empty = ArcQuestPlayerPersistenceMetadata.stamp(new CompoundTag(), 99, 999);
        CompoundTag data = payload(1, 1);
        assertTrue(ArcQuestPlayerPersistenceMetadata.compare(empty, data) < 0);
        assertEquals(data, ArcQuestPlayerPersistenceMetadata.newer(empty, data));
        assertEquals(0, ArcQuestPlayerPersistenceMetadata.compare(null, empty));
        assertTrue(ArcQuestPlayerPersistenceMetadata.newer(null, empty).isEmpty());
    }

    @Test
    void revisionTakesPrecedenceOverClockTimeAndTiesKeepTheFirstPayload() {
        CompoundTag first = payload(8, 1);
        CompoundTag second = payload(7, 999);
        assertTrue(ArcQuestPlayerPersistenceMetadata.compare(first, second) > 0);
        CompoundTag sameVersion = payload(8, 1);
        sameVersion.putString("Value", "different");
        assertEquals(first, ArcQuestPlayerPersistenceMetadata.newer(first, sameVersion));
    }

    private static CompoundTag payload(long revision, long time) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Value", "player state");
        return ArcQuestPlayerPersistenceMetadata.stamp(tag, revision, time);
    }
}
