package org.arcadia.arc_quest.quest.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.*;

public final class CollectionRuntimeData {

    private final LinkedHashSet<String> visiblePhaseIds;
    private final LinkedHashSet<String> discoveredPhaseIds;
    private final LinkedHashMap<String, Integer> entryCounts;
    private final LinkedHashMap<String, LinkedHashSet<String>> entryUniqueKeys;
    private final LinkedHashSet<String> unlockedRewardIds;
    private final LinkedHashSet<String> claimedRewardIds;
    @Nullable
    private String lastUpdatedPhaseId;
    @Nullable
    private String lastUpdatedCategoryId;
    private long lastUpdatedAtMs;
    private boolean dirty;

    public CollectionRuntimeData() {
        this(new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashSet<>(), new LinkedHashSet<>(), null, null, 0L, false);
    }

    private CollectionRuntimeData(LinkedHashSet<String> visiblePhaseIds,
                                  LinkedHashSet<String> discoveredPhaseIds,
                                  LinkedHashMap<String, Integer> entryCounts,
                                  LinkedHashMap<String, LinkedHashSet<String>> entryUniqueKeys,
                                  LinkedHashSet<String> unlockedRewardIds,
                                  LinkedHashSet<String> claimedRewardIds,
                                  @Nullable String lastUpdatedPhaseId,
                                  @Nullable String lastUpdatedCategoryId,
                                  long lastUpdatedAtMs,
                                  boolean dirty) {
        this.visiblePhaseIds = visiblePhaseIds;
        this.discoveredPhaseIds = discoveredPhaseIds;
        this.entryCounts = entryCounts;
        this.entryUniqueKeys = entryUniqueKeys;
        this.unlockedRewardIds = unlockedRewardIds;
        this.claimedRewardIds = claimedRewardIds;
        this.lastUpdatedPhaseId = lastUpdatedPhaseId;
        this.lastUpdatedCategoryId = lastUpdatedCategoryId;
        this.lastUpdatedAtMs = Math.max(0L, lastUpdatedAtMs);
        this.dirty = dirty;
    }

    public static CollectionRuntimeData deserializeNBT(CompoundTag tag) {
        LinkedHashSet<String> visible = readStringSet(tag, "VisiblePhases");
        LinkedHashSet<String> discovered = readStringSet(tag, "DiscoveredPhases");
        LinkedHashMap<String, Integer> entryCounts = new LinkedHashMap<>();
        if (tag.contains("EntryCounts", Tag.TAG_COMPOUND)) {
            CompoundTag entryCountsTag = tag.getCompound("EntryCounts");
            for (String key : entryCountsTag.getAllKeys()) entryCounts.put(key, entryCountsTag.getInt(key));
        }
        LinkedHashMap<String, LinkedHashSet<String>> uniqueKeys = new LinkedHashMap<>();
        if (tag.contains("EntryUniqueKeys", Tag.TAG_COMPOUND)) {
            CompoundTag uniqueKeysTag = tag.getCompound("EntryUniqueKeys");
            for (String key : uniqueKeysTag.getAllKeys()) uniqueKeys.put(key, readStringSet(uniqueKeysTag, key));
        }
        LinkedHashSet<String> unlockedRewards = readStringSet(tag, "UnlockedRewards");
        LinkedHashSet<String> claimedRewards = readStringSet(tag, "ClaimedRewards");
        String lastPhaseId = tag.contains("LastUpdatedPhaseId", Tag.TAG_STRING) ? emptyToNull(tag.getString("LastUpdatedPhaseId")) : null;
        String lastCategoryId = tag.contains("LastUpdatedCategoryId", Tag.TAG_STRING) ? emptyToNull(tag.getString("LastUpdatedCategoryId")) : null;
        long lastUpdatedAtMs = tag.contains("LastUpdatedAtMs", Tag.TAG_LONG) ? tag.getLong("LastUpdatedAtMs") : 0L;
        return new CollectionRuntimeData(visible, discovered, entryCounts, uniqueKeys, unlockedRewards, claimedRewards, lastPhaseId, lastCategoryId, lastUpdatedAtMs, false);
    }

    public static CollectionRuntimeData readFromNetwork(FriendlyByteBuf buf) {
        LinkedHashSet<String> visible = readStringSet(buf);
        LinkedHashSet<String> discovered = readStringSet(buf);

        int entryCountSize = buf.readVarInt();
        LinkedHashMap<String, Integer> entryCounts = new LinkedHashMap<>();
        for (int i = 0; i < entryCountSize; i++) entryCounts.put(buf.readUtf(256), buf.readVarInt());

        int uniqueSize = buf.readVarInt();
        LinkedHashMap<String, LinkedHashSet<String>> uniqueKeys = new LinkedHashMap<>();
        for (int i = 0; i < uniqueSize; i++) {
            String key = buf.readUtf(256);
            uniqueKeys.put(key, readStringSet(buf));
        }

        LinkedHashSet<String> unlockedRewards = readStringSet(buf);
        LinkedHashSet<String> claimedRewards = readStringSet(buf);
        String lastPhaseId = emptyToNull(buf.readUtf(256));
        String lastCategoryId = emptyToNull(buf.readUtf(256));
        long lastUpdatedAtMs = buf.readLong();
        return new CollectionRuntimeData(visible, discovered, entryCounts, uniqueKeys, unlockedRewards, claimedRewards, lastPhaseId, lastCategoryId, lastUpdatedAtMs, false);
    }

    private static LinkedHashSet<String> readStringSet(CompoundTag tag, String key) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (!tag.contains(key, Tag.TAG_LIST)) return values;
        ListTag listTag = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < listTag.size(); i++) {
            String value = listTag.getString(i);
            if (value != null && !value.isEmpty()) values.add(value);
        }
        return values;
    }

    private static LinkedHashSet<String> readStringSet(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (int i = 0; i < size; i++) values.add(buf.readUtf(256));
        return values;
    }

    private static void writeStringSet(CompoundTag tag, String key, Set<String> values) {
        ListTag listTag = new ListTag();
        for (String value : values) listTag.add(StringTag.valueOf(value));
        tag.put(key, listTag);
    }

    private static void writeStringSet(FriendlyByteBuf buf, Set<String> values) {
        buf.writeVarInt(values.size());
        for (String value : values) buf.writeUtf(value);
    }

    @Nullable
    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    public boolean isVisible(String phaseId) {
        return visiblePhaseIds.contains(phaseId);
    }

    public boolean isDiscovered(String phaseId) {
        return discoveredPhaseIds.contains(phaseId);
    }

    public int getEntryCount(String phaseId) {
        return entryCounts.getOrDefault(phaseId, 0);
    }

    public int incrementEntryCount(String phaseId, int amount, int max) {
        int current = getEntryCount(phaseId);
        int next = current + Math.max(0, amount);
        if (max > 0) next = Math.min(next, max);
        entryCounts.put(phaseId, next);
        dirty = true;
        return next;
    }

    public boolean addUniqueKey(String phaseId, String key) {
        Objects.requireNonNull(phaseId);
        Objects.requireNonNull(key);
        LinkedHashSet<String> keys = entryUniqueKeys.computeIfAbsent(phaseId, ignored -> new LinkedHashSet<>());
        boolean added = keys.add(key);
        if (added) dirty = true;
        return added;
    }

    public void markVisible(String phaseId) {
        if (visiblePhaseIds.add(phaseId)) dirty = true;
    }

    public void markDiscovered(String phaseId) {
        if (discoveredPhaseIds.add(phaseId)) dirty = true;
    }

    public boolean isRewardUnlocked(String rewardId) {
        return unlockedRewardIds.contains(rewardId);
    }

    public void markRewardUnlocked(String rewardId) {
        if (unlockedRewardIds.add(rewardId)) dirty = true;
    }

    public boolean isRewardClaimed(String rewardId) {
        return claimedRewardIds.contains(rewardId);
    }

    public void markRewardClaimed(String rewardId) {
        if (claimedRewardIds.add(rewardId)) dirty = true;
    }

    public void markUpdated(@Nullable String phaseId, @Nullable String categoryId, long updatedAtMs) {
        this.lastUpdatedPhaseId = phaseId;
        this.lastUpdatedCategoryId = categoryId;
        this.lastUpdatedAtMs = Math.max(0L, updatedAtMs);
        this.dirty = true;
    }

    public Set<String> getVisiblePhaseIds() {
        return Set.copyOf(visiblePhaseIds);
    }

    public Set<String> getDiscoveredPhaseIds() {
        return Set.copyOf(discoveredPhaseIds);
    }

    public Map<String, Integer> getEntryCounts() {
        return Map.copyOf(entryCounts);
    }

    public Set<String> getEntryUniqueKeys(String phaseId) {
        LinkedHashSet<String> keys = entryUniqueKeys.get(phaseId);
        return keys == null ? Set.of() : Set.copyOf(keys);
    }

    public Set<String> getUnlockedRewardIds() {
        return Set.copyOf(unlockedRewardIds);
    }

    public Set<String> getClaimedRewardIds() {
        return Set.copyOf(claimedRewardIds);
    }

    @Nullable
    public String getLastUpdatedPhaseId() {
        return lastUpdatedPhaseId;
    }

    @Nullable
    public String getLastUpdatedCategoryId() {
        return lastUpdatedCategoryId;
    }

    public long getLastUpdatedAtMs() {
        return lastUpdatedAtMs;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        writeStringSet(tag, "VisiblePhases", visiblePhaseIds);
        writeStringSet(tag, "DiscoveredPhases", discoveredPhaseIds);
        CompoundTag entryCountsTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : entryCounts.entrySet())
            entryCountsTag.putInt(entry.getKey(), entry.getValue());
        tag.put("EntryCounts", entryCountsTag);
        CompoundTag uniqueKeysTag = new CompoundTag();
        for (Map.Entry<String, LinkedHashSet<String>> entry : entryUniqueKeys.entrySet())
            writeStringSet(uniqueKeysTag, entry.getKey(), entry.getValue());
        tag.put("EntryUniqueKeys", uniqueKeysTag);
        writeStringSet(tag, "UnlockedRewards", unlockedRewardIds);
        writeStringSet(tag, "ClaimedRewards", claimedRewardIds);
        if (lastUpdatedPhaseId != null && !lastUpdatedPhaseId.isEmpty())
            tag.putString("LastUpdatedPhaseId", lastUpdatedPhaseId);
        if (lastUpdatedCategoryId != null && !lastUpdatedCategoryId.isEmpty())
            tag.putString("LastUpdatedCategoryId", lastUpdatedCategoryId);
        tag.putLong("LastUpdatedAtMs", lastUpdatedAtMs);
        return tag;
    }

    public void writeToNetwork(FriendlyByteBuf buf) {
        writeStringSet(buf, visiblePhaseIds);
        writeStringSet(buf, discoveredPhaseIds);
        buf.writeVarInt(entryCounts.size());
        for (Map.Entry<String, Integer> entry : entryCounts.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeVarInt(entry.getValue());
        }
        buf.writeVarInt(entryUniqueKeys.size());
        for (Map.Entry<String, LinkedHashSet<String>> entry : entryUniqueKeys.entrySet()) {
            buf.writeUtf(entry.getKey());
            writeStringSet(buf, entry.getValue());
        }
        writeStringSet(buf, unlockedRewardIds);
        writeStringSet(buf, claimedRewardIds);
        buf.writeUtf(lastUpdatedPhaseId != null ? lastUpdatedPhaseId : "");
        buf.writeUtf(lastUpdatedCategoryId != null ? lastUpdatedCategoryId : "");
        buf.writeLong(lastUpdatedAtMs);
    }

    public CollectionRuntimeData copy() {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>(entryCounts);
        LinkedHashMap<String, LinkedHashSet<String>> uniqueKeys = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashSet<String>> entry : entryUniqueKeys.entrySet())
            uniqueKeys.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        return new CollectionRuntimeData(new LinkedHashSet<>(visiblePhaseIds), new LinkedHashSet<>(discoveredPhaseIds), counts, uniqueKeys, new LinkedHashSet<>(unlockedRewardIds), new LinkedHashSet<>(claimedRewardIds), lastUpdatedPhaseId, lastUpdatedCategoryId, lastUpdatedAtMs, dirty);
    }
}
