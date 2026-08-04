package org.arcadia.arc_quest.questplayer.persistence;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArcQuestPlayerPersistenceMetadataTest {

    @Test
    void newerPrefersHigherRevision() {
        CompoundTag saved = ArcQuestPlayerPersistenceMetadata.stamp(snapshot("saved"), 4L, 200L);
        CompoundTag checkpoint = ArcQuestPlayerPersistenceMetadata.stamp(snapshot("checkpoint"), 5L, 100L);

        CompoundTag selected = ArcQuestPlayerPersistenceMetadata.newer(saved, checkpoint);

        assertEquals("checkpoint", selected.getString("Source"));
        assertEquals(5L, ArcQuestPlayerPersistenceMetadata.revision(selected));
    }

    @Test
    void newerUsesTimestampWhenRevisionsMatch() {
        CompoundTag saved = ArcQuestPlayerPersistenceMetadata.stamp(snapshot("saved"), 7L, 300L);
        CompoundTag checkpoint = ArcQuestPlayerPersistenceMetadata.stamp(snapshot("checkpoint"), 7L, 200L);

        CompoundTag selected = ArcQuestPlayerPersistenceMetadata.newer(saved, checkpoint);

        assertEquals("saved", selected.getString("Source"));
    }

    @Test
    void selectionReturnsDefensiveCopy() {
        CompoundTag saved = ArcQuestPlayerPersistenceMetadata.stamp(snapshot("saved"), 2L, 100L);

        CompoundTag selected = ArcQuestPlayerPersistenceMetadata.newer(saved, new CompoundTag());
        selected.putString("Source", "mutated");

        assertEquals("saved", saved.getString("Source"));
        assertTrue(ArcQuestPlayerPersistenceMetadata.newer(new CompoundTag(), new CompoundTag()).isEmpty());
    }

    @Test
    void metadataOnlySnapshotHasNoBusinessPayload() {
        CompoundTag metadataOnly = ArcQuestPlayerPersistenceMetadata.stamp(new CompoundTag(), 3L, 100L);

        assertTrue(ArcQuestPlayerPersistenceMetadata.isPayloadEmpty(metadataOnly));
    }

    @Test
    void metadataOnlySnapshotCannotOverrideBusinessPayload() {
        CompoundTag metadataOnly = ArcQuestPlayerPersistenceMetadata.stamp(new CompoundTag(), 99L, 500L);
        CompoundTag payload = ArcQuestPlayerPersistenceMetadata.stamp(snapshot("payload"), 1L, 100L);

        CompoundTag selected = ArcQuestPlayerPersistenceMetadata.newer(metadataOnly, payload);

        assertEquals("payload", selected.getString("Source"));
    }

    private static CompoundTag snapshot(String source) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Source", source);
        return tag;
    }
}
