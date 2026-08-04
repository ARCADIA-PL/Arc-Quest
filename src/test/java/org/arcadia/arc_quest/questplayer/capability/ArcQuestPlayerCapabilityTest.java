package org.arcadia.arc_quest.questplayer.capability;

import net.minecraft.nbt.CompoundTag;
import org.arcadia.arc_quest.questplayer.persistence.ArcQuestPlayerPersistenceMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArcQuestPlayerCapabilityTest {

    @Test
    void capabilityRoundTripsRevisionedSnapshot() {
        ArcQuestPlayerCapability capability = new ArcQuestPlayerCapability();
        CompoundTag snapshot = new CompoundTag();
        snapshot.putString("Quest", "arc_quest:test");
        capability.replaceSnapshot(ArcQuestPlayerPersistenceMetadata.stamp(snapshot, 6L, 100L));

        ArcQuestPlayerCapability restored = new ArcQuestPlayerCapability();
        restored.deserializeNBT(capability.serializeNBT());

        assertEquals("arc_quest:test", restored.snapshot().getString("Quest"));
        assertEquals(6L, ArcQuestPlayerPersistenceMetadata.revision(restored.snapshot()));
    }

    @Test
    void capabilityUsesDefensiveCopies() {
        ArcQuestPlayerCapability capability = new ArcQuestPlayerCapability();
        CompoundTag source = new CompoundTag();
        source.putString("Value", "original");
        capability.replaceSnapshot(source);

        source.putString("Value", "source-mutated");
        CompoundTag extracted = capability.snapshot();
        extracted.putString("Value", "copy-mutated");

        assertEquals("original", capability.snapshot().getString("Value"));
        capability.clear();
        assertTrue(capability.isEmpty());
    }
}
