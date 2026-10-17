package org.arcadia.arc_quest.quest.data;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestRuntimeDataSnapshotTest {
    @Test void advancingRuntimeCannotRewriteItsEarlierSnapshot() {
        var runtime = new QuestRuntimeData("example:quest", "first", 1, 0, 0, 0);
        runtime.setObjectiveProgress("first", 0, 2);
        var saved = runtime.serializeNBT();
        runtime.incrementProgress("first", 0, 3, 10);
        assertArrayEquals(new int[]{2}, saved.getCompound("PhaseProgress").getIntArray("first"));
        assertEquals(5, runtime.getObjectiveProgress("first", 0));
        runtime = QuestRuntimeData.deserializeNBT(saved);
        assertEquals(2, runtime.getObjectiveProgress("first", 0));
    }

    @Test void loadingCannotRetainMutableArraysOwnedByCaller() {
        var source = new QuestRuntimeData("example:quest", "first", 1, 0, 0, 0).serializeNBT();
        var runtime = QuestRuntimeData.deserializeNBT(source);
        source.getCompound("PhaseProgress").getIntArray("first")[0] = 99;
        assertEquals(0, runtime.getObjectiveProgress("first", 0));
        runtime.setObjectiveProgress("first", 0, 3);
        assertArrayEquals(new int[]{99}, source.getCompound("PhaseProgress").getIntArray("first"));
    }
}
