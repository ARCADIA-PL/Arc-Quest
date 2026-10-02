package org.arcadia.arc_quest.quest.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Sparse player-owned collection facts. Quest abandonment never resets this store. */
public final class CollectionRecordState {
    public static final String ROOT_KEY = "CollectionRecords";
    public static final String RESET_ENTRY_IDS_KEY = "ResetEntryIds";
    private final Map<ResourceLocation, CollectionEntryRecord> records = new LinkedHashMap<>();
    private final Set<ResourceLocation> resetEntryIds = new LinkedHashSet<>();
    private final Set<String> migratedLegacyEntries = new LinkedHashSet<>();
    private final Set<String> legacyRewardReceipts = new LinkedHashSet<>();
    private final Set<ResourceLocation> dirtyEntryIds = new LinkedHashSet<>();
    private long revision;
    private boolean dirty;

    public CollectionEntryRecord getRecord(ResourceLocation entryId) {
        CollectionEntryRecord record = records.get(entryId);
        return record == null ? new CollectionEntryRecord() : record.copy();
    }

    public boolean isDiscovered(ResourceLocation entryId) {
        CollectionEntryRecord record = records.get(entryId);
        return record != null && record.isDiscovered();
    }

    public int getProgress(ResourceLocation entryId, String stepId) {
        CollectionEntryRecord record = records.get(entryId);
        return record == null ? 0 : record.getProgress(stepId);
    }

    public Set<String> discoveredIds() {
        Set<String> result = new LinkedHashSet<>();
        records.forEach((id, value) -> { if (value.isDiscovered()) result.add(id.toString()); });
        return Set.copyOf(result);
    }

    public boolean discover(ResourceLocation entryId) {
        return entryChanged(entryId, records.computeIfAbsent(entryId, ignored -> new CollectionEntryRecord()).discover());
    }

    public boolean increment(ResourceLocation entryId, String stepId, int amount, int maximum) {
        if (amount <= 0 || maximum <= 0) return false;
        return entryChanged(entryId, records.computeIfAbsent(entryId, ignored -> new CollectionEntryRecord()).increment(stepId, amount, maximum));
    }

    public boolean importProgress(ResourceLocation entryId, String stepId, int count, int maximum) {
        return entryChanged(entryId, records.computeIfAbsent(entryId, ignored -> new CollectionEntryRecord()).importProgress(stepId, count, maximum));
    }

    public boolean markSeen(ResourceLocation entryId, String blockId) {
        CollectionEntryRecord record = records.get(entryId);
        return record != null && record.isDiscovered() && entryChanged(entryId, record.markSeen(blockId));
    }

    public boolean isRewardUnlocked(ResourceLocation entryId, String rewardId) {
        CollectionEntryRecord record = records.get(entryId); return record != null && record.isRewardUnlocked(rewardId);
    }
    public boolean isRewardClaimed(ResourceLocation entryId, String rewardId) {
        CollectionEntryRecord record = records.get(entryId); return record != null && record.isRewardClaimed(rewardId);
    }
    public boolean unlockReward(ResourceLocation entryId, String rewardId) {
        return entryChanged(entryId, records.computeIfAbsent(entryId, ignored -> new CollectionEntryRecord()).unlockReward(rewardId));
    }
    public boolean claimReward(ResourceLocation entryId, String rewardId) {
        CollectionEntryRecord record = records.get(entryId);
        return record != null && entryChanged(entryId, record.claimReward(rewardId));
    }

    public boolean markLegacyMigrated(String key) { return changed(migratedLegacyEntries.add(key)); }
    public boolean isLegacyMigrated(String key) { return migratedLegacyEntries.contains(key); }
    public boolean markLegacyRewardClaimed(String questId, String nodeId) {
        String key = legacyRewardKey(questId, nodeId);
        return key != null && changed(legacyRewardReceipts.add(key));
    }
    public boolean isLegacyRewardClaimed(String questId, String nodeId) {
        String key = legacyRewardKey(questId, nodeId);
        return key != null && legacyRewardReceipts.contains(key);
    }

    /** Explicit resets start these entries over; old inventory and legacy snapshots must not refill them. */
    public boolean isEntryReset(ResourceLocation entryId) { return resetEntryIds.contains(entryId); }

    public boolean resetQuest(String questId, Set<ResourceLocation> entryIds) {
        boolean reset = false;
        for (ResourceLocation id : entryIds) {
            // Empty records act as tombstones for the existing merge-only record delta protocol.
            records.put(id, new CollectionEntryRecord());
            dirtyEntryIds.add(id);
            resetEntryIds.add(id);
            reset = true;
        }
        reset |= migratedLegacyEntries.removeIf(key -> key.startsWith(questId + "/"));
        reset |= legacyRewardReceipts.removeIf(key -> key.startsWith(questId + "|"));
        return changed(reset);
    }
    private static String legacyRewardKey(String questId, String nodeId) {
        return questId == null || ResourceLocation.tryParse(questId) == null || nodeId == null || nodeId.isBlank()
                ? null : questId + "|" + nodeId;
    }
    public long getRevision() { return revision; }
    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; dirtyEntryIds.clear(); }
    public Set<ResourceLocation> getDirtyEntryIds() { return Set.copyOf(dirtyEntryIds); }

    private boolean entryChanged(ResourceLocation id, boolean result) {
        if (result) dirtyEntryIds.add(id);
        return changed(result);
    }

    private boolean changed(boolean changed) {
        if (changed) {
            dirty = true;
            revision = revision == Long.MAX_VALUE ? Long.MAX_VALUE : revision + 1;
        }
        return changed;
    }

    public void writeToRoot(CompoundTag root) { root.put(ROOT_KEY, serializeNBT()); }
    public void readFromRoot(CompoundTag root) { readSnapshot(root.getCompound(ROOT_KEY)); }

    public CompoundTag serializeNBT() {
        CompoundTag root = serializeEntries(records.keySet());
        root.putInt("Version", 1);
        ListTag migrated = new ListTag();
        migratedLegacyEntries.forEach(id -> migrated.add(StringTag.valueOf(id)));
        root.put("MigratedLegacyEntries", migrated);
        ListTag receipts = new ListTag();
        legacyRewardReceipts.forEach(id -> receipts.add(StringTag.valueOf(id)));
        root.put("LegacyRewardReceipts", receipts);
        ListTag resetIds = new ListTag();
        resetEntryIds.forEach(id -> resetIds.add(StringTag.valueOf(id.toString())));
        root.put(RESET_ENTRY_IDS_KEY, resetIds);
        return root;
    }

    public CompoundTag serializeEntries(Set<ResourceLocation> ids) {
        CompoundTag root = new CompoundTag();
        root.putLong("Revision", revision);
        CompoundTag entries = new CompoundTag();
        for (ResourceLocation id : ids) {
            CollectionEntryRecord record = records.get(id);
            if (record != null) entries.put(id.toString(), record.serializeNBT());
        }
        root.put("Entries", entries);
        return root;
    }

    public void readSnapshot(CompoundTag root) {
        records.clear();
        resetEntryIds.clear();
        migratedLegacyEntries.clear();
        legacyRewardReceipts.clear();
        revision = Math.max(0L, root.getLong("Revision"));
        readEntries(root.getCompound("Entries"));
        ListTag migrated = root.getList("MigratedLegacyEntries", Tag.TAG_STRING);
        for (int i = 0; i < migrated.size(); i++) {
            String id = migrated.getString(i);
            if (!id.isBlank() && id.length() <= 768) migratedLegacyEntries.add(id);
        }
        ListTag receipts = root.getList("LegacyRewardReceipts", Tag.TAG_STRING);
        for (int i = 0; i < receipts.size(); i++) {
            String id = receipts.getString(i);
            int separator = id.indexOf('|');
            if (separator > 0 && id.length() <= 768
                    && legacyRewardKey(id.substring(0, separator), id.substring(separator + 1)) != null)
                legacyRewardReceipts.add(id);
        }
        ListTag resetIds = root.getList(RESET_ENTRY_IDS_KEY, Tag.TAG_STRING);
        for (int i = 0; i < resetIds.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(resetIds.getString(i));
            if (id != null) resetEntryIds.add(id);
        }
        dirty = false;
        dirtyEntryIds.clear();
    }

    public void applyDelta(CompoundTag root) {
        long incoming = Math.max(0L, root.getLong("Revision"));
        if (incoming < revision) return;
        readEntries(root.getCompound("Entries"));
        revision = incoming;
        dirty = false;
    }

    private void readEntries(CompoundTag entries) {
        for (String key : entries.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null && entries.contains(key, Tag.TAG_COMPOUND)) records.put(id, CollectionEntryRecord.deserializeNBT(entries.getCompound(key)));
        }
    }

    public void copyFrom(CollectionRecordState source) { readSnapshot(source.serializeNBT()); }
    public void clear() {
        records.clear();
        resetEntryIds.clear();
        migratedLegacyEntries.clear();
        legacyRewardReceipts.clear();
        changed(true);
    }
}
