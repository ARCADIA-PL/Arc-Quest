package org.arcadia.arc_quest.questplayer;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ArcQuestPlayerTrackedPhaseTest {

    @Test
    void preservesTrackedPhaseDuringNbtRoundTrip() {
        ArcQuestPlayer original = new ArcQuestPlayer(UUID.randomUUID());
        original.setTrackedQuestId("arc_quest:epic_prologue");
        original.setTrackedPhaseId("arc_quest:scout_forest");

        CompoundTag serialized = original.serializeNBT();
        ArcQuestPlayer restored = new ArcQuestPlayer(UUID.randomUUID());
        restored.deserializeNBT(serialized);

        assertEquals("arc_quest:epic_prologue", restored.getTrackedQuestId());
        assertEquals("arc_quest:scout_forest", restored.getTrackedPhaseId());
    }

    @Test
    void clearsTrackedPhaseWhenTrackedQuestChanges() {
        ArcQuestPlayer playerData = new ArcQuestPlayer(UUID.randomUUID());
        playerData.setTrackedQuestId("arc_quest:epic_prologue");
        playerData.setTrackedPhaseId("arc_quest:scout_forest");

        playerData.setTrackedQuestId("arc_quest:another_quest");

        assertNull(playerData.getTrackedPhaseId());
    }

    @Test
    void acceptsLegacyNbtWithoutTrackedPhase() {
        ArcQuestPlayer playerData = new ArcQuestPlayer(UUID.randomUUID());
        CompoundTag legacy = new CompoundTag();
        legacy.putString("TrackedQuestId", "arc_quest:epic_prologue");

        playerData.deserializeNBT(legacy);

        assertEquals("arc_quest:epic_prologue", playerData.getTrackedQuestId());
        assertNull(playerData.getTrackedPhaseId());
    }
}
