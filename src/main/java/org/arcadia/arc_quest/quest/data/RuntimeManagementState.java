package org.arcadia.arc_quest.quest.data;

import java.util.*;
import net.minecraft.nbt.CompoundTag;

/** Server-only migration preimages/receipts and pause reasons, in the same player snapshot as runs. */
public final class RuntimeManagementState {
    public static final String ROOT_KEY = "RuntimeManagement";
    private CompoundTag state = new CompoundTag();
    public CompoundTag snapshot() { return state.copy(); }
    public void restore(CompoundTag value) { state = value.copy(); }
    public boolean contains(String key) { return state.getCompound("Migrations").contains(key); }
    public boolean hasCapacity() { return state.getCompound("Migrations").size() < 64; }
    public void record(String key, CompoundTag before, CompoundTag after) {
        if (contains(key)) throw new IllegalStateException("Migration already applied");
        if (!hasCapacity() || before.toString().length() + after.toString().length() > 2_000_000)
            throw new IllegalStateException("Migration backup limit reached; archive backups before proceeding");
        var receipt = new CompoundTag(); receipt.put("Before", before.copy()); receipt.put("After", after.copy());
        var migrations = state.getCompound("Migrations"); migrations.put(key, receipt); state.put("Migrations", migrations);
    }
    public CompoundTag receipt(String key) { return state.getCompound("Migrations").getCompound(key).copy(); }
    public void markRestored(String key) { state.getCompound("Migrations").getCompound(key).putBoolean("Restored", true); }
    public void paused(String quest, String run, String reason) {
        var pauses = state.getCompound("Paused"); var value = new CompoundTag();
        value.putString("RunId", run); value.putString("Reason", reason); pauses.put(quest, value); state.put("Paused", pauses);
    }
    public void pauseRun(String quest, org.arcadia.arc_quest.quest.data.QuestRuntimeData run, String reason) {
        paused(quest, run.hasCollectionData()?run.getCollectionData().getRunId():"", reason);
        state.getCompound("Paused").getCompound(quest).put("Snapshot",run.serializeNBT());
    }
    public org.arcadia.arc_quest.quest.data.QuestRuntimeData pausedRun(String quest) {
        var tag=state.getCompound("Paused").getCompound(quest).getCompound("Snapshot");
        return tag.isEmpty()?null:org.arcadia.arc_quest.quest.data.QuestRuntimeData.deserializeNBT(tag.copy());
    }
    public void resumed(String quest) { state.getCompound("Paused").remove(quest); }
    public String pauseReason(String quest) { return state.getCompound("Paused").getCompound(quest).getString("Reason"); }
    public void resetQuest(String quest) { resumed(quest); }
    public void clear() { state = new CompoundTag(); }
}
