package org.arcadia.arc_quest.questmarker.runtime;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

/** Bounded personal discovery history. A new request skips returned starts, not other players' discoveries. */
final class StructureSearchHistory {
    static final int LIMIT = 1024;
    private StructureSearchHistory() {}

    static long positionKey(Vec3 position) { return BlockPos.asLong((int) Math.floor(position.x), 0, (int) Math.floor(position.z)); }

    static Set<Long> read(CompoundTag entry, String key) {
        return Arrays.stream(entry.getLongArray(key)).boxed().collect(Collectors.toUnmodifiableSet());
    }

    static void advance(CompoundTag entry) {
        if (entry.contains("x", Tag.TAG_ANY_NUMERIC) && entry.contains("z", Tag.TAG_ANY_NUMERIC)) {
            append(entry, "skippedPositions", positionKey(new Vec3(entry.getDouble("x"), 0, entry.getDouble("z"))));
            if (entry.contains("startChunk", Tag.TAG_LONG)) append(entry, "skippedChunks", entry.getLong("startChunk"));
        }
        for (String key : new String[]{"x", "y", "z", "startChunk", "attempt"}) entry.remove(key);
    }

    private static void append(CompoundTag entry, String key, long value) {
        long[] old = entry.getLongArray(key);
        if (Arrays.stream(old).anyMatch(v -> v == value)) return;
        int keep = Math.min(old.length, LIMIT - 1);
        long[] next = new long[keep + 1];
        System.arraycopy(old, old.length - keep, next, 0, keep);
        next[keep] = value;
        entry.putLongArray(key, next);
    }
}
