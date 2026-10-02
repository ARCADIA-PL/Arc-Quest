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
    private final Map<String, CompoundTag> outcomes = new LinkedHashMap<>();
    private final Map<String, CompoundTag> rewardEntitlements = new LinkedHashMap<>();
    private final Set<String> pendingDeliveries = new LinkedHashSet<>();
    public boolean isDeliveryPending(String rewardId) { return pendingDeliveries.contains(rewardId); }
    boolean setDeliveryPending(String rewardId, boolean pending) {
        return pending ? validRewardId(rewardId) && pendingDeliveries.add(rewardId) : pendingDeliveries.remove(rewardId);
    }

    public CompoundTag getRewardEntitlement(String rewardId) {
        return rewardEntitlements.containsKey(rewardId) ? rewardEntitlements.get(rewardId).copy() : new CompoundTag();
    }
    public Set<String> getRewardEntitlementIds() { return Set.copyOf(rewardEntitlements.keySet()); }
    boolean snapshotReward(String rewardId, CompoundTag payload) {
        if (!validRewardId(rewardId) || payload.isEmpty() || rewardEntitlements.containsKey(rewardId)) return false;
        rewardEntitlements.put(rewardId, payload.copy()); return true;
    }

    public boolean hasOutcome(String outcomeId) { return outcomes.containsKey(outcomeId); }
    public Set<String> getOutcomeIds() { return Set.copyOf(outcomes.keySet()); }
    public String getOutcomeSource(String outcomeId) {
        CompoundTag fact = outcomes.get(outcomeId);
        return fact == null ? "" : fact.getString("Source");
    }
    boolean recordOutcome(String outcomeId, String source, long generation) {
        if (!discovered || outcomeId == null || outcomeId.isBlank() || outcomeId.length() > 128
                || source == null || source.isBlank() || source.length() > 1024 || outcomes.containsKey(outcomeId)) return false;
        CompoundTag fact = new CompoundTag();
        fact.putString("Source", source);
        fact.putLong("Generation", generation);
        outcomes.put(outcomeId, fact);
        return true;
    }

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
        CompoundTag facts = new CompoundTag();
        outcomes.forEach((id, fact) -> facts.put(id, fact.copy()));
        tag.put("Outcomes", facts);
        CompoundTag entitlements = new CompoundTag();
        rewardEntitlements.forEach((id, payload) -> entitlements.put(id, payload.copy()));
        tag.put("RewardEntitlements", entitlements);
        ListTag pending = new ListTag(); pendingDeliveries.forEach(id -> pending.add(StringTag.valueOf(id))); tag.put("PendingDeliveries", pending);
        return tag;
    }

    public static CollectionEntryRecord deserializeNBT(CompoundTag tag) {
        CollectionEntryRecord record = new CollectionEntryRecord();
        record.discovered = tag.getBoolean("Discovered");
        ListTag pending = tag.getList("PendingDeliveries", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(8192, pending.size()); i++) record.setDeliveryPending(pending.getString(i), true);
        CompoundTag entitlements = tag.getCompound("RewardEntitlements");
        for (String id : entitlements.getAllKeys())
            if (entitlements.contains(id, Tag.TAG_COMPOUND)) record.snapshotReward(id, entitlements.getCompound(id));
        CompoundTag facts = tag.getCompound("Outcomes");
        for (String id : facts.getAllKeys()) {
            if (id.isBlank() || id.length() > 128 || !facts.contains(id, Tag.TAG_COMPOUND)) continue;
            CompoundTag fact = facts.getCompound(id);
            if (record.discovered && fact.getString("Source").length() <= 1024) record.outcomes.put(id, fact.copy());
        }
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
        outcomes.forEach((id, fact) -> copy.outcomes.put(id, fact.copy()));
        rewardEntitlements.forEach((id, payload) -> copy.rewardEntitlements.put(id, payload.copy()));
        copy.pendingDeliveries.addAll(pendingDeliveries);
        return copy;
    }
}
