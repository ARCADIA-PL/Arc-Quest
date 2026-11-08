package org.arcadia.arc_quest.questmarker.runtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureSearchHistoryTest {
    @Test void explicitNextRemembersReturnedStartAndPositionAfterSaveRoundTrip() {
        var entry = new CompoundTag();
        entry.putDouble("x", -16.5); entry.putDouble("y", 80); entry.putDouble("z", 42.5);
        entry.putLong("startChunk", 123L); entry.putLong("attempt", 50L);
        StructureSearchHistory.advance(entry);
        var saved = entry.copy();
        assertEquals(java.util.Set.of(123L), StructureSearchHistory.read(saved, "skippedChunks"));
        assertTrue(StructureSearchHistory.read(saved, "skippedPositions").contains(StructureSearchHistory.positionKey(new Vec3(-16.5, 0, 42.5))));
        assertFalse(saved.contains("x")); assertFalse(saved.contains("attempt"));
        StructureSearchHistory.advance(saved);
        assertArrayEquals(entry.getLongArray("skippedChunks"), saved.getLongArray("skippedChunks"));
    }
    @Test void personalHistoriesAreIndependentAndBounded() {
        var first = new CompoundTag(); var second = new CompoundTag();
        for (int i = 0; i < StructureSearchHistory.LIMIT + 3; i++) {
            first.putDouble("x", i); first.putDouble("z", 0); first.putLong("startChunk", i);
            StructureSearchHistory.advance(first);
        }
        assertEquals(StructureSearchHistory.LIMIT, first.getLongArray("skippedChunks").length);
        assertFalse(StructureSearchHistory.read(first, "skippedChunks").contains(0L));
        assertTrue(StructureSearchHistory.read(second, "skippedChunks").isEmpty());
    }
}
