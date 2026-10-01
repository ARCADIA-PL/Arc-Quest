package org.arcadia.arc_quest.quest.tracking;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ObjectiveTrackerTagHandlesTest {
    @Test void keepsEveryExpandedHandleAndRemovesAllOnPlayerLogout() {
        UUID player = UUID.randomUUID();
        var oak = handle(player, "phase", "minecraft:oak_log");
        var birch = handle(player, "phase", "minecraft:birch_log");
        assertNotEquals(oak, birch);
        try {
            ObjectiveTracker.INSTANCE.register(oak);
            ObjectiveTracker.INSTANCE.register(birch);
            assertEquals(1, ObjectiveTracker.INSTANCE.lookup(player, oak.getKey()).size());
            assertEquals(1, ObjectiveTracker.INSTANCE.lookup(player, birch.getKey()).size());
            ObjectiveTracker.INSTANCE.unregisterPlayer(player);
            assertTrue(ObjectiveTracker.INSTANCE.lookup(player, oak.getKey()).isEmpty());
            assertTrue(ObjectiveTracker.INSTANCE.lookup(player, birch.getKey()).isEmpty());
        } finally { ObjectiveTracker.INSTANCE.unregisterPlayer(player); }
    }

    @Test void phaseRemovalUsesStoredKeysAndPreservesOtherPhases() {
        UUID player = UUID.randomUUID();
        var oldOak = handle(player, "old", "minecraft:oak_log");
        var oldBirch = handle(player, "old", "minecraft:birch_log");
        var next = handle(player, "next", "minecraft:oak_log");
        try {
            ObjectiveTracker.INSTANCE.register(oldOak);
            ObjectiveTracker.INSTANCE.register(oldBirch);
            ObjectiveTracker.INSTANCE.register(next);
            ObjectiveTracker.INSTANCE.unregisterPhase(player, oldOak.getQuestId(), "old");
            assertEquals(java.util.List.of(next), ObjectiveTracker.INSTANCE.lookup(player, oldOak.getKey()));
            assertTrue(ObjectiveTracker.INSTANCE.lookup(player, oldBirch.getKey()).isEmpty());
            ObjectiveTracker.INSTANCE.unregisterQuest(player, oldOak.getQuestId().toString());
            assertTrue(ObjectiveTracker.INSTANCE.lookup(player, next.getKey()).isEmpty());
        } finally { ObjectiveTracker.INSTANCE.unregisterPlayer(player); }
    }

    private static TrackedObjective handle(UUID player, String phase, String item) {
        return new TrackedObjective(player, ResourceLocation.parse("arc_quest:tag_handles_test"), phase, 0,
                new ObjectiveKey(ObjectiveType.COLLECT, ResourceLocation.parse(item)), 10);
    }
}
