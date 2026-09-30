package org.arcadia.arc_quest.quest.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.arcadia.arc_quest.quest.api.QuestState;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 任务运行时数据，存储在 Capability 中。
 * 支持同一 Quest 内多阶段并行推进。
 */
public final class QuestRuntimeData {

    private final String questId;
    private final long acceptedAtTick;
    private final long acceptedAtRealMs;
    private final long acceptedAtDayTime;
    /**
     * 当前活跃并行阶段
     */
    private final ObjectOpenHashSet<String> activePhaseIds;
    /**
     * 已完成阶段
     */
    private final ObjectOpenHashSet<String> completedPhaseIds;
    /**
     * 已满足目标、等待玩家手动确认推进的阶段
     */
    private final ObjectOpenHashSet<String> pendingManualAdvancePhaseIds;
    /**
     * 每个阶段的目标进度
     */
    private final Object2ObjectOpenHashMap<String, int[]> phaseProgress;
    /** Server-resolved display thresholds; legacy snapshots may omit these derived values. */
    private final Map<String, int[]> effectiveRequiredCounts = new HashMap<>();
    @Nullable
    private CollectionRuntimeData collectionData;
    private QuestState state;
    private boolean isDirty = false;

    private transient final Object2ByteOpenHashMap<String> phaseCompletionCache
            = new Object2ByteOpenHashMap<>();

    private transient final Object2ByteOpenHashMap<String> enterConditionCache
            = new Object2ByteOpenHashMap<>();

    public QuestRuntimeData(String questId,
                            String initialPhaseId,
                            int objectiveCount,
                            long acceptedAtTick,
                            long acceptedAtRealMs,
                            long acceptedAtDayTime) {
        this.questId = Objects.requireNonNull(questId);
        state = QuestState.ACTIVE;
        this.acceptedAtTick = acceptedAtTick;
        this.acceptedAtRealMs = acceptedAtRealMs;
        this.acceptedAtDayTime = acceptedAtDayTime;

        activePhaseIds = new ObjectOpenHashSet<>();
        completedPhaseIds = new ObjectOpenHashSet<>();
        pendingManualAdvancePhaseIds = new ObjectOpenHashSet<>();
        phaseProgress = new Object2ObjectOpenHashMap<>();
        collectionData = null;

        activePhaseIds.add(Objects.requireNonNull(initialPhaseId));
        phaseProgress.put(initialPhaseId, new int[Math.max(0, objectiveCount)]);
    }

    private QuestRuntimeData(String questId,
                             QuestState state,
                             ObjectOpenHashSet<String> activePhaseIds,
                             ObjectOpenHashSet<String> completedPhaseIds,
                             ObjectOpenHashSet<String> pendingManualAdvancePhaseIds,
                             Object2ObjectOpenHashMap<String, int[]> phaseProgress,
                             @Nullable CollectionRuntimeData collectionData,
                             long acceptedAtTick,
                             long acceptedAtRealMs,
                             long acceptedAtDayTime) {
        this.questId = questId;
        this.state = state;
        this.activePhaseIds = activePhaseIds;
        this.completedPhaseIds = completedPhaseIds;
        this.pendingManualAdvancePhaseIds = pendingManualAdvancePhaseIds;
        this.phaseProgress = phaseProgress;
        this.collectionData = collectionData;
        this.acceptedAtTick = acceptedAtTick;
        this.acceptedAtRealMs = acceptedAtRealMs;
        this.acceptedAtDayTime = acceptedAtDayTime;
    }

    public static QuestRuntimeData deserializeNBT(CompoundTag tag) {
        String questId = tag.getString("QuestId");

        QuestState state;
        try {
            state = QuestState.valueOf(tag.getString("State"));
        } catch (IllegalArgumentException e) {
            state = QuestState.ACTIVE;
        }

        long accepted = tag.getLong("AcceptedAt");
        long acceptedRealMs = tag.contains("AcceptedAtRealMs", Tag.TAG_LONG) ? tag.getLong("AcceptedAtRealMs") : 0L;
        long acceptedDayTime = tag.contains("AcceptedAtDayTime", Tag.TAG_LONG) ? tag.getLong("AcceptedAtDayTime") : 0L;

        ObjectOpenHashSet<String> active = new ObjectOpenHashSet<>();
        ObjectOpenHashSet<String> completed = new ObjectOpenHashSet<>();
        ObjectOpenHashSet<String> pendingManualAdvance = new ObjectOpenHashSet<>();
        Object2ObjectOpenHashMap<String, int[]> progress = new Object2ObjectOpenHashMap<>();

        if (tag.contains("ActivePhases", Tag.TAG_LIST)) {
            ListTag activeList = tag.getList("ActivePhases", Tag.TAG_STRING);
            for (int i = 0; i < activeList.size(); i++) {
                String pid = activeList.getString(i);
                if (pid != null && !pid.isEmpty()) active.add(pid);
            }
        }

        if (tag.contains("CompletedPhases", Tag.TAG_LIST)) {
            ListTag completedList = tag.getList("CompletedPhases", Tag.TAG_STRING);
            for (int i = 0; i < completedList.size(); i++) {
                String pid = completedList.getString(i);
                if (pid != null && !pid.isEmpty()) completed.add(pid);
            }
        }

        if (tag.contains("PendingManualAdvancePhases", Tag.TAG_LIST)) {
            ListTag pendingList = tag.getList("PendingManualAdvancePhases", Tag.TAG_STRING);
            for (int i = 0; i < pendingList.size(); i++) {
                String pid = pendingList.getString(i);
                if (pid != null && !pid.isEmpty()) pendingManualAdvance.add(pid);
            }
        }

        if (tag.contains("PhaseProgress", Tag.TAG_COMPOUND)) {
            CompoundTag progressTag = tag.getCompound("PhaseProgress");
            for (String phaseId : progressTag.getAllKeys()) {
                progress.put(phaseId, progressTag.getIntArray(phaseId));
            }
        }

        if (active.isEmpty() && completed.isEmpty() && progress.isEmpty()) {
            String legacyPhaseId = tag.getString("PhaseId");
            if (legacyPhaseId != null && !legacyPhaseId.isEmpty()) {
                active.add(legacyPhaseId);
                int[] legacyArr = tag.contains("Progress", Tag.TAG_INT_ARRAY)
                        ? tag.getIntArray("Progress")
                        : new int[0];
                progress.put(legacyPhaseId, Arrays.copyOf(legacyArr, legacyArr.length));
            }
        }

        for (String pid : active) {
            progress.computeIfAbsent(pid, k -> new int[0]);
        }

        if (active.isEmpty() && !progress.isEmpty()) {
            active.add(progress.keySet().iterator().next());
        }

        CollectionRuntimeData collectionData = null;
        if (tag.contains("CollectionData", Tag.TAG_COMPOUND)) {
            collectionData = CollectionRuntimeData.deserializeNBT(tag.getCompound("CollectionData"));
        }

        QuestRuntimeData result = new QuestRuntimeData(questId, state, active, completed, pendingManualAdvance, progress, collectionData, accepted, acceptedRealMs, acceptedDayTime);
        CompoundTag required = tag.getCompound("EffectiveRequiredCounts");
        for (String phaseId : required.getAllKeys()) result.setRequiredCounts(phaseId, required.getIntArray(phaseId));
        return result;
    }

    public static QuestRuntimeData readFromNetwork(FriendlyByteBuf buf) {
        String questId = buf.readUtf(256);
        QuestState state = buf.readEnum(QuestState.class);

        int activeSize = buf.readVarInt();
        ObjectOpenHashSet<String> active = new ObjectOpenHashSet<>();
        for (int i = 0; i < activeSize; i++) {
            active.add(buf.readUtf(256));
        }

        int completedSize = buf.readVarInt();
        ObjectOpenHashSet<String> completed = new ObjectOpenHashSet<>();
        for (int i = 0; i < completedSize; i++) {
            completed.add(buf.readUtf(256));
        }

        int pendingManualAdvanceSize = buf.readVarInt();
        ObjectOpenHashSet<String> pendingManualAdvance = new ObjectOpenHashSet<>();
        for (int i = 0; i < pendingManualAdvanceSize; i++) {
            pendingManualAdvance.add(buf.readUtf(256));
        }

        int progressSize = buf.readVarInt();
        Object2ObjectOpenHashMap<String, int[]> progress = new Object2ObjectOpenHashMap<>();
        for (int i = 0; i < progressSize; i++) {
            String phaseId = buf.readUtf(256);
            int len = buf.readVarInt();
            int[] arr = new int[len];
            for (int j = 0; j < len; j++) {
                arr[j] = buf.readVarInt();
            }
            progress.put(phaseId, arr);
        }

        boolean hasCollectionData = buf.readBoolean();
        CollectionRuntimeData collectionData = hasCollectionData ? CollectionRuntimeData.readFromNetwork(buf) : null;
        long accepted = buf.readLong();
        long acceptedRealMs = buf.readLong();
        long acceptedDayTime = buf.readLong();
        QuestRuntimeData result = new QuestRuntimeData(questId, state, active, completed, pendingManualAdvance, progress, collectionData, accepted, acceptedRealMs, acceptedDayTime);
        int requiredPhases = buf.readVarInt();
        if (requiredPhases < 0 || requiredPhases > progress.size()) throw new IllegalArgumentException("Invalid required-count phases");
        Set<String> seenRequiredPhases = new HashSet<>();
        for (int i = 0; i < requiredPhases; i++) {
            String phaseId = buf.readUtf(256);
            int length = buf.readVarInt();
            if (!seenRequiredPhases.add(phaseId) || length < 0 || !progress.containsKey(phaseId) || length != progress.get(phaseId).length)
                throw new IllegalArgumentException("Invalid required-count objectives");
            int[] required = new int[length];
            for (int j = 0; j < length; j++) {
                required[j] = buf.readVarInt();
                if (required[j] < 0) throw new IllegalArgumentException("Invalid required count");
            }
            result.setRequiredCounts(phaseId, required);
        }
        return result;
    }

    public String getQuestId() {
        return questId;
    }

    public QuestState getState() {
        return state;
    }

    public void setState(QuestState state) {
        this.state = Objects.requireNonNull(state);
        isDirty = true;
        phaseCompletionCache.clear();
    }

    public long getAcceptedAtTick() {
        return acceptedAtTick;
    }

    public long getAcceptedAtRealMs() {
        return acceptedAtRealMs;
    }

    public long getAcceptedAtDayTime() {
        return acceptedAtDayTime;
    }

    public Set<String> getActivePhaseIds() {
        return Set.copyOf(activePhaseIds);
    }

    public Set<String> getCompletedPhaseIds() {
        return Set.copyOf(completedPhaseIds);
    }

    public boolean isPhaseActive(String phaseId) {
        return activePhaseIds.contains(phaseId);
    }

    public boolean isPhaseCompleted(String phaseId) {
        return completedPhaseIds.contains(phaseId);
    }

    @Nullable
    public CollectionRuntimeData getCollectionData() {
        return collectionData;
    }

    public void setCollectionData(@Nullable CollectionRuntimeData collectionData) {
        this.collectionData = collectionData;
        isDirty = true;
    }

    public boolean hasCollectionData() {
        return collectionData != null;
    }

    public int getObjectiveCount(String phaseId) {
        int[] arr = phaseProgress.get(phaseId);
        return arr == null ? 0 : arr.length;
    }

    public int getObjectiveProgress(String phaseId, int index) {
        int[] arr = phaseProgress.get(phaseId);
        if (arr == null || index < 0 || index >= arr.length) return 0;
        return arr[index];
    }

    public int[] getAllProgress(String phaseId) {
        int[] arr = phaseProgress.get(phaseId);
        return arr == null ? new int[0] : Arrays.copyOf(arr, arr.length);
    }

    public int getRequiredCount(String phaseId, int index, int fallback) {
        int[] counts = effectiveRequiredCounts.get(phaseId);
        return counts != null && index >= 0 && index < counts.length && counts[index] > 0
                ? counts[index] : Math.max(1, fallback);
    }

    public boolean hasRequiredCount(String phaseId, int index) {
        int[] counts = effectiveRequiredCounts.get(phaseId);
        return counts != null && index >= 0 && index < counts.length && counts[index] > 0;
    }

    public boolean setRequiredCount(String phaseId, int index, int required) {
        int length = getObjectiveCount(phaseId);
        if (index < 0 || index >= length || required < 1) return false;
        int[] existing = effectiveRequiredCounts.get(phaseId);
        int[] counts = existing == null ? new int[length] : Arrays.copyOf(existing, length);
        counts[index] = required;
        return setRequiredCounts(phaseId, counts);
    }

    public boolean setRequiredCounts(String phaseId, int[] required) {
        if (required == null || !phaseProgress.containsKey(phaseId)) return false;
        int[] counts = Arrays.copyOf(required, getObjectiveCount(phaseId));
        for (int i = 0; i < counts.length; i++) counts[i] = Math.max(0, counts[i]);
        if (Arrays.equals(effectiveRequiredCounts.get(phaseId), counts)) return false;
        effectiveRequiredCounts.put(phaseId, counts);
        phaseCompletionCache.removeByte(phaseId);
        return true;
    }

    public int incrementProgress(String phaseId, int index, int amount, int clampMax) {
        int[] arr = phaseProgress.get(phaseId);
        if (arr == null || index < 0 || index >= arr.length) return 0;
        long next = (long) arr[index] + amount;
        if (clampMax > 0) next = Math.min(clampMax, next);
        arr[index] = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, next));
        isDirty = true;
        phaseCompletionCache.removeByte(phaseId);
        return arr[index];
    }

    public void setObjectiveProgress(String phaseId, int index, int value) {
        int[] arr = phaseProgress.get(phaseId);
        if (arr != null && index >= 0 && index < arr.length) {
            arr[index] = value;
            isDirty = true;
        }
    }

    public void activatePhase(String phaseId, int objectiveCount) {
        if (phaseId == null || phaseId.isEmpty()) return;
        if (!phaseProgress.containsKey(phaseId)) phaseProgress.put(phaseId, new int[Math.max(0, objectiveCount)]);
        if (activePhaseIds.add(phaseId)) {
            completedPhaseIds.remove(phaseId);
            pendingManualAdvancePhaseIds.remove(phaseId);
            isDirty = true;
            phaseCompletionCache.clear();
        }
    }

    public void completePhase(String phaseId) {
        if (activePhaseIds.remove(phaseId)) {
            completedPhaseIds.add(phaseId);
            pendingManualAdvancePhaseIds.remove(phaseId);
            isDirty = true;
            phaseCompletionCache.clear();
        }
    }

    /** 删除一个活跃阶段及其运行时缓存，不触发奖励或后续阶段。 */
    public boolean abandonPhase(String phaseId) {
        if (!activePhaseIds.remove(phaseId)) return false;
        pendingManualAdvancePhaseIds.remove(phaseId);
        phaseProgress.remove(phaseId);
        effectiveRequiredCounts.remove(phaseId);
        phaseCompletionCache.removeByte(phaseId);
        enterConditionCache.removeByte(phaseId);
        isDirty = true;
        return true;
    }
    public void markPhasePendingManualAdvance(String phaseId) {
        if (phaseId == null || phaseId.isEmpty()) return;
        if (activePhaseIds.contains(phaseId) && pendingManualAdvancePhaseIds.add(phaseId)) {
            isDirty = true;
        }
    }

    public void clearPhasePendingManualAdvance(String phaseId) {
        if (pendingManualAdvancePhaseIds.remove(phaseId)) {
            isDirty = true;
        }
    }

    public boolean isPhasePendingManualAdvance(String phaseId) {
        return pendingManualAdvancePhaseIds.contains(phaseId);
    }

    public Set<String> getPendingManualAdvancePhaseIds() {
        return Set.copyOf(pendingManualAdvancePhaseIds);
    }

    public String getCurrentPendingManualAdvancePhaseId() {
        for (String phaseId : activePhaseIds) {
            if (pendingManualAdvancePhaseIds.contains(phaseId)) return phaseId;
        }
        return "";
    }

    public boolean isDirty() {
        return isDirty || (collectionData != null && collectionData.isDirty());
    }

    public void clearDirty() {
        isDirty = false;
        if (collectionData != null) collectionData.clearDirty();
    }

    public boolean isPhaseCompletionCached(String phaseId) {
        return phaseCompletionCache.getByte(phaseId) != 0;
    }

    public void setPhaseCompletionCached(String phaseId, boolean satisfied) {
        phaseCompletionCache.put(phaseId, (byte) (satisfied ? 1 : 2));
    }

    public boolean isPhaseCompletionSatisfied(String phaseId) {
        return phaseCompletionCache.getByte(phaseId) == 1;
    }

    public void invalidatePhaseCache() {
        phaseCompletionCache.clear();
        enterConditionCache.clear();
    }

    public boolean isEnterConditionCached(String phaseId) {
        return enterConditionCache.getByte(phaseId) != 0;
    }

    public boolean canEnterPhaseCached(String phaseId) {
        return enterConditionCache.getByte(phaseId) == 1;
    }

    public void setEnterConditionCached(String phaseId, boolean canEnter) {
        enterConditionCache.put(phaseId, (byte) (canEnter ? 1 : 2));
    }

    public void invalidateEnterConditionCache() {
        enterConditionCache.clear();
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("QuestId", questId);
        tag.putInt("SchemaVersion", 4);
        tag.putString("State", state.name());
        tag.putLong("AcceptedAt", acceptedAtTick);
        tag.putLong("AcceptedAtRealMs", acceptedAtRealMs);
        tag.putLong("AcceptedAtDayTime", acceptedAtDayTime);

        ListTag activeList = new ListTag();
        for (String id : activePhaseIds) activeList.add(StringTag.valueOf(id));
        tag.put("ActivePhases", activeList);

        ListTag completedList = new ListTag();
        for (String id : completedPhaseIds) completedList.add(StringTag.valueOf(id));
        tag.put("CompletedPhases", completedList);

        ListTag pendingList = new ListTag();
        for (String id : pendingManualAdvancePhaseIds) pendingList.add(StringTag.valueOf(id));
        tag.put("PendingManualAdvancePhases", pendingList);

        CompoundTag progressTag = new CompoundTag();
        for (Map.Entry<String, int[]> e : phaseProgress.entrySet()) progressTag.putIntArray(e.getKey(), e.getValue());
        tag.put("PhaseProgress", progressTag);
        CompoundTag requiredTag = new CompoundTag();
        effectiveRequiredCounts.forEach((phaseId, counts) -> requiredTag.putIntArray(phaseId, Arrays.copyOf(counts, counts.length)));
        tag.put("EffectiveRequiredCounts", requiredTag);
        if (collectionData != null) tag.put("CollectionData", collectionData.serializeNBT());
        return tag;
    }

    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeUtf(questId);
        buf.writeEnum(state);

        buf.writeVarInt(activePhaseIds.size());
        for (String id : activePhaseIds) buf.writeUtf(id);

        buf.writeVarInt(completedPhaseIds.size());
        for (String id : completedPhaseIds) buf.writeUtf(id);

        buf.writeVarInt(pendingManualAdvancePhaseIds.size());
        for (String id : pendingManualAdvancePhaseIds) buf.writeUtf(id);

        buf.writeVarInt(phaseProgress.size());
        for (Map.Entry<String, int[]> e : phaseProgress.entrySet()) {
            buf.writeUtf(e.getKey());
            int[] arr = e.getValue();
            buf.writeVarInt(arr.length);
            for (int p : arr) buf.writeVarInt(p);
        }

        buf.writeBoolean(collectionData != null);
        if (collectionData != null) collectionData.writeToNetwork(buf);
        buf.writeLong(acceptedAtTick);
        buf.writeLong(acceptedAtRealMs);
        buf.writeLong(acceptedAtDayTime);
        buf.writeVarInt(effectiveRequiredCounts.size());
        for (Map.Entry<String, int[]> entry : effectiveRequiredCounts.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeVarInt(entry.getValue().length);
            for (int required : entry.getValue()) buf.writeVarInt(required);
        }
    }

    public QuestRuntimeData copy() {
        ObjectOpenHashSet<String> active = new ObjectOpenHashSet<>(activePhaseIds);
        ObjectOpenHashSet<String> completed = new ObjectOpenHashSet<>(completedPhaseIds);
        Object2ObjectOpenHashMap<String, int[]> progress = new Object2ObjectOpenHashMap<>();
        for (Object2ObjectOpenHashMap.Entry<String, int[]> e : phaseProgress.object2ObjectEntrySet())
            progress.put(e.getKey(), Arrays.copyOf(e.getValue(), e.getValue().length));
        CollectionRuntimeData collectionDataCopy = collectionData != null ? collectionData.copy() : null;
        QuestRuntimeData result = new QuestRuntimeData(questId, state, active, completed, new ObjectOpenHashSet<>(pendingManualAdvancePhaseIds), progress, collectionDataCopy, acceptedAtTick, acceptedAtRealMs, acceptedAtDayTime);
        effectiveRequiredCounts.forEach(result::setRequiredCounts);
        return result;
    }

    public String getCurrentPhaseId() {
        if (!activePhaseIds.isEmpty()) return activePhaseIds.iterator().next();
        if (!completedPhaseIds.isEmpty()) return completedPhaseIds.iterator().next();
        return "";
    }

    public void setCurrentPhaseId(String phaseId) {
        activePhaseIds.clear();
        completedPhaseIds.clear();
        phaseProgress.clear();
        effectiveRequiredCounts.clear();
        activatePhase(phaseId, 0);
        isDirty = true;
    }

    public int getObjectiveCount() {
        return getObjectiveCount(getCurrentPhaseId());
    }

    public int getObjectiveProgress(int index) {
        return getObjectiveProgress(getCurrentPhaseId(), index);
    }

    public int[] getAllProgress() {
        return getAllProgress(getCurrentPhaseId());
    }

    public int incrementProgress(int index, int amount, int clampMax) {
        return incrementProgress(getCurrentPhaseId(), index, amount, clampMax);
    }

    public void setObjectiveProgress(int index, int value) {
        setObjectiveProgress(getCurrentPhaseId(), index, value);
    }

    public void resetObjectives(int newCount) {
        String current = getCurrentPhaseId();
        if (current == null || current.isEmpty()) return;
        phaseProgress.put(current, new int[Math.max(0, newCount)]);
        effectiveRequiredCounts.remove(current);
        isDirty = true;
    }

    @Override
    public String toString() {
        return "QuestRuntimeData{" +
                "quest='" + questId + '\'' +
                ", state=" + state +
                ", activePhases=" + activePhaseIds +
                ", completedPhases=" + completedPhaseIds +
                ", hasCollectionData=" + (collectionData != null) +
                '}';
    }
}
