package org.arcadia.arc_quest.quest.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Persisted knowledge about one entry, independent of any accepted quest. */
public final class CollectionEntryRecord {
    private boolean discovered;
    private final Map<String, Integer> progress = new LinkedHashMap<>();
    private final Set<String> seenBlocks = new LinkedHashSet<>();

    public boolean isDiscovered() { return discovered; }
    public int getProgress(String stepId) { return progress.getOrDefault(stepId, 0); }
    public boolean isSeen(String blockId) { return seenBlocks.contains(blockId); }
    public boolean isSeen() { return seenBlocks.contains("entry"); }
    public Map<String, Integer> getAllProgress() { return Collections.unmodifiableMap(progress); }

    boolean discover() {
        if (discovered) return false;
        discovered = true;
        return true;
    }

    boolean increment(String stepId, int amount, int maximum) {
        if (stepId == null || stepId.isBlank() || amount <= 0 || maximum <= 0) return false;
        int previous = getProgress(stepId);
        int next = (int) Math.min(maximum, (long) previous + amount);
        if (next == previous) return false;
        progress.put(stepId, next);
        return true;
    }

    boolean importProgress(String stepId, int count, int maximum) {
        return increment(stepId, Math.max(0, Math.min(count, maximum) - getProgress(stepId)), maximum);
    }

    boolean markSeen(String blockId) {
        return blockId != null && !blockId.isBlank() && blockId.length() <= 256 && seenBlocks.add(blockId);
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Discovered", discovered);
        CompoundTag counts = new CompoundTag();
        progress.forEach(counts::putInt);
        tag.put("Steps", counts);
        ListTag seen = new ListTag();
        seenBlocks.forEach(id -> seen.add(StringTag.valueOf(id)));
        tag.put("Seen", seen);
        return tag;
    }

    public static CollectionEntryRecord deserializeNBT(CompoundTag tag) {
        CollectionEntryRecord record = new CollectionEntryRecord();
        record.discovered = tag.getBoolean("Discovered");
        CompoundTag counts = tag.getCompound("Steps");
        for (String key : counts.getAllKeys()) {
            if (!key.isBlank() && key.length() <= 256) record.progress.put(key, Math.max(0, counts.getInt(key)));
        }
        ListTag seen = tag.getList("Seen", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(8192, seen.size()); i++) record.markSeen(seen.getString(i));
        return record;
    }

    public CollectionEntryRecord copy() {
        CollectionEntryRecord copy = new CollectionEntryRecord();
        copy.discovered = discovered;
        copy.progress.putAll(progress);
        copy.seenBlocks.addAll(seenBlocks);
        return copy;
    }
}
