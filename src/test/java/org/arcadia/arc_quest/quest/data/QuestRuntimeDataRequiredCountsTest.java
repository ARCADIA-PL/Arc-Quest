package org.arcadia.arc_quest.quest.data;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuestRuntimeDataRequiredCountsTest {
    @Test void legacyFallbackAndPartialCountsAreExplicit() {
        var runtime = runtime();
        assertFalse(runtime.hasRequiredCount("first", 0));
        assertEquals(19, runtime.getRequiredCount("first", 0, 19));
        assertEquals(1, runtime.getRequiredCount("missing", 0, -1));
        assertFalse(runtime.setRequiredCount("missing", 0, 12));
        assertFalse(runtime.setRequiredCount("first", -1, 12));
        assertFalse(runtime.setRequiredCount("first", 0, 0));
        assertTrue(runtime.setRequiredCount("first", 1, 65537));
        assertFalse(runtime.setRequiredCount("first", 1, 65537));
        assertEquals(65537, runtime.getRequiredCount("first", 1, 1));
        assertEquals(19, runtime.getRequiredCount("first", 0, 19));
    }

    @Test void changesInvalidateCompletionCacheWithoutDirtyingBusinessProgress() {
        var runtime = runtime();
        runtime.clearDirty();
        runtime.setPhaseCompletionCached("first", false);
        int[] values = {12, 35};
        assertTrue(runtime.setRequiredCounts("first", values));
        values[0] = 99;
        assertEquals(12, runtime.getRequiredCount("first", 0, 1));
        assertFalse(runtime.isPhaseCompletionCached("first"));
        assertFalse(runtime.isDirty());
        runtime.setPhaseCompletionCached("first", true);
        assertFalse(runtime.setRequiredCounts("first", new int[]{12, 35}));
        assertTrue(runtime.isPhaseCompletionSatisfied("first"));
    }

    @Test void nbtAndCopiesRetainCountsWithoutSharingMutableArrays() {
        var original = runtime();
        original.setRequiredCounts("first", new int[]{2, 65537});
        var tag = original.serializeNBT();
        assertEquals(QuestRuntimeData.SCHEMA_VERSION, tag.getInt("SchemaVersion"));
        var restored = QuestRuntimeData.deserializeNBT(tag);
        assertEquals(65537, restored.getRequiredCount("first", 1, 1));
        tag.getCompound("EffectiveRequiredCounts").getIntArray("first")[0] = 99;
        assertEquals(2, original.getRequiredCount("first", 0, 1));
        assertEquals(2, restored.getRequiredCount("first", 0, 1));
        var copy = original.copy();
        original.setRequiredCount("first", 1, 90);
        assertEquals(65537, copy.getRequiredCount("first", 1, 1));
        copy.setRequiredCount("first", 0, 80);
        assertEquals(2, original.getRequiredCount("first", 0, 1));
        tag.remove("EffectiveRequiredCounts");
        tag.putInt("SchemaVersion", 3);
        var legacy = QuestRuntimeData.deserializeNBT(tag);
        assertFalse(legacy.hasRequiredCount("first", 1));
        assertEquals(7, legacy.getRequiredCount("first", 1, 7));
    }

    @Test void networkRoundTripPreservesParallelPhasesAndLargeCounts() {
        var original = runtime();
        original.activatePhase("second", 1);
        original.setRequiredCounts("first", new int[]{65537, Integer.MAX_VALUE});
        original.setRequiredCount("second", 0, 136);
        original.setObjectiveProgress("first", 0, 65536);
        original.completePhase("second");
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            original.writeToNetwork(buf);
            var decoded = QuestRuntimeData.readFromNetwork(buf);
            assertEquals(0, buf.readableBytes());
            assertEquals(65537, decoded.getRequiredCount("first", 0, 1));
            assertEquals(Integer.MAX_VALUE, decoded.getRequiredCount("first", 1, 1));
            assertEquals(136, decoded.getRequiredCount("second", 0, 1));
            assertEquals(65536, decoded.getObjectiveProgress("first", 0));
            assertTrue(decoded.isPhaseCompleted("second"));
        } finally { buf.release(); }
    }

    @Test void malformedNetworkCountsCannotTargetUnknownOrDuplicatePhases() {
        var runtime = runtime();
        runtime.activatePhase("second", 1);
        for (String phase : new String[]{"missing", "first"}) {
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                runtime.writeToNetwork(buf);
                buf.writerIndex(buf.writerIndex() - 1); // Replace the zero required-phase count.
                buf.writeVarInt(2);
                for (int i = 0; i < 2; i++) {
                    buf.writeUtf(phase);
                    buf.writeVarInt(2);
                    buf.writeVarInt(3);
                    buf.writeVarInt(4);
                }
                assertThrows(IllegalArgumentException.class, () -> QuestRuntimeData.readFromNetwork(buf));
            } finally { buf.release(); }
        }
    }

    @Test void resetAndAbandonDiscardDerivedCountsWhileCompletionKeepsThem() {
        var runtime = runtime();
        runtime.setRequiredCount("first", 0, 12);
        runtime.resetObjectives(1);
        assertFalse(runtime.hasRequiredCount("first", 0));
        runtime.setRequiredCount("first", 0, 14);
        runtime.completePhase("first");
        assertEquals(14, runtime.getRequiredCount("first", 0, 1));
        runtime.activatePhase("second", 1);
        runtime.setRequiredCount("second", 0, 90);
        assertTrue(runtime.abandonPhase("second"));
        assertFalse(runtime.hasRequiredCount("second", 0));
        runtime.setCurrentPhaseId("replacement");
        assertFalse(runtime.hasRequiredCount("first", 0));
    }

    @Test void largeProgressCannotWrapBeforeItIsClamped() {
        var runtime = runtime();
        runtime.setObjectiveProgress("first", 0, Integer.MAX_VALUE - 2);
        assertEquals(Integer.MAX_VALUE, runtime.incrementProgress("first", 0, 50, Integer.MAX_VALUE));
        assertEquals(0, runtime.incrementProgress("first", 0, Integer.MIN_VALUE, Integer.MAX_VALUE));
        runtime.setObjectiveProgress("first", 0, Integer.MAX_VALUE - 2);
        assertEquals(65537, runtime.incrementProgress("first", 0, 50, 65537));
    }

    private static QuestRuntimeData runtime() {
        return new QuestRuntimeData("arc_quest:required_test", "first", 2, 1L, 2L, 3L);
    }
}
