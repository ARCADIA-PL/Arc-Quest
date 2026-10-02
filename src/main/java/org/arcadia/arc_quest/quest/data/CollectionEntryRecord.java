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
    private final Set<String> unlockedRewards = new LinkedHashSet<>();
    private final Set<String> claimedRewards = new LinkedHashSet<>();

    public boolean isDiscovered() { return discovered; }
    public int getProgress(String stepId) { return progress.getOrDefault(stepId, 0); }
    public boolean isSeen(String blockId) { return seenBlocks.contains(blockId); }
    public boolean isSeen() { return seenBlocks.contains("entry"); }
    public Map<String, Integer> getAllProgress() { return Collections.unmodifiableMap(progress); }
    public boolean isRewardUnlocked(String rewardId) { return unlockedRewards.contains(rewardId); }
    public boolean isRewardClaimed(String rewardId) { return claimedRewards.contains(rewardId); }
    public Set<String> getUnlockedRewardIds() { return Set.copyOf(unlockedRewards); }
    public Set<String> getClaimedRewardIds() { return Set.copyOf(claimedRewards); }
    boolean unlockReward(String rewardId) { return validRewardId(rewardId) && unlockedRewards.add(rewardId); }
    boolean claimReward(String rewardId) {
        if (!validRewardId(rewardId) || !unlockedRewards.contains(rewardId)) return false;
        return claimedRewards.add(rewardId);
    }
    private static boolean validRewardId(String id) { return id != null && !id.isBlank() && id.length() <= 128; }

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
        ListTag unlocked = new ListTag(), claimed = new ListTag();
        unlockedRewards.forEach(id -> unlocked.add(StringTag.valueOf(id)));
        claimedRewards.forEach(id -> claimed.add(StringTag.valueOf(id)));
        tag.put("UnlockedRewards", unlocked); tag.put("ClaimedRewards", claimed);
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
        ListTag unlocked = tag.getList("UnlockedRewards", Tag.TAG_STRING), claimed = tag.getList("ClaimedRewards", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(8192, unlocked.size()); i++) record.unlockReward(unlocked.getString(i));
        for (int i = 0; i < Math.min(8192, claimed.size()); i++) {
            record.unlockReward(claimed.getString(i)); record.claimReward(claimed.getString(i));
        }
        return record;
    }

    public CollectionEntryRecord copy() {
        CollectionEntryRecord copy = new CollectionEntryRecord();
        copy.discovered = discovered;
        copy.progress.putAll(progress);
        copy.seenBlocks.addAll(seenBlocks);
        copy.unlockedRewards.addAll(unlockedRewards); copy.claimedRewards.addAll(claimedRewards);
        return copy;
    }
}
