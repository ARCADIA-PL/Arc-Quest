package org.arcadia.arc_quest.quest.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
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
    private final LinkedHashSet<String> activePhaseIds;
    /**
     * 已完成阶段
     */
    private final LinkedHashSet<String> completedPhaseIds;
    /**
     * 每个阶段的目标进度
     */
    private final LinkedHashMap<String, int[]> phaseProgress;
    @Nullable
    private CollectionRuntimeData collectionData;
    private QuestState state;
    private boolean isDirty = false;

    public QuestRuntimeData(String questId,
                            String initialPhaseId,
                            int objectiveCount,
                            long acceptedAtTick,
                            long acceptedAtRealMs,
                            long acceptedAtDayTime) {
        this.questId = Objects.requireNonNull(questId);
        this.state = QuestState.ACTIVE;
        this.acceptedAtTick = acceptedAtTick;
        this.acceptedAtRealMs = acceptedAtRealMs;
        this.acceptedAtDayTime = acceptedAtDayTime;

        this.activePhaseIds = new LinkedHashSet<>();
        this.completedPhaseIds = new LinkedHashSet<>();
        this.phaseProgress = new LinkedHashMap<>();
        this.collectionData = null;

        this.activePhaseIds.add(Objects.requireNonNull(initialPhaseId));
        this.phaseProgress.put(initialPhaseId, new int[Math.max(0, objectiveCount)]);
    }

    private QuestRuntimeData(String questId,
                             QuestState state,
                             LinkedHashSet<String> activePhaseIds,
                             LinkedHashSet<String> completedPhaseIds,
                             LinkedHashMap<String, int[]> phaseProgress,
                             @Nullable CollectionRuntimeData collectionData,
                             long acceptedAtTick,
                             long acceptedAtRealMs,
                             long acceptedAtDayTime) {
        this.questId = questId;
        this.state = state;
        this.activePhaseIds = activePhaseIds;
        this.completedPhaseIds = completedPhaseIds;
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

        LinkedHashSet<String> active = new LinkedHashSet<>();
        LinkedHashSet<String> completed = new LinkedHashSet<>();
        LinkedHashMap<String, int[]> progress = new LinkedHashMap<>();

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

        return new QuestRuntimeData(questId, state, active, completed, progress, collectionData, accepted, acceptedRealMs, acceptedDayTime);
    }

    public static QuestRuntimeData readFromNetwork(FriendlyByteBuf buf) {
        String questId = buf.readUtf(256);
        QuestState state = buf.readEnum(QuestState.class);

        int activeSize = buf.readVarInt();
        LinkedHashSet<String> active = new LinkedHashSet<>();
        for (int i = 0; i < activeSize; i++) {
            active.add(buf.readUtf(256));
        }

        int completedSize = buf.readVarInt();
        LinkedHashSet<String> completed = new LinkedHashSet<>();
        for (int i = 0; i < completedSize; i++) {
            completed.add(buf.readUtf(256));
        }

        int progressSize = buf.readVarInt();
        LinkedHashMap<String, int[]> progress = new LinkedHashMap<>();
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
        return new QuestRuntimeData(questId, state, active, completed, progress, collectionData, accepted, acceptedRealMs, acceptedDayTime);
    }

    public String getQuestId() {
        return questId;
    }

    public QuestState getState() {
        return state;
    }

    public void setState(QuestState state) {
        this.state = Objects.requireNonNull(state);
        this.isDirty = true;
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
        this.isDirty = true;
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

    public int incrementProgress(String phaseId, int index, int amount, int clampMax) {
        int[] arr = phaseProgress.get(phaseId);
        if (arr == null || index < 0 || index >= arr.length) return 0;
        arr[index] += amount;
        if (clampMax > 0 && arr[index] > clampMax) arr[index] = clampMax;
        isDirty = true;
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
            isDirty = true;
        }
    }

    public void completePhase(String phaseId) {
        if (activePhaseIds.remove(phaseId)) {
            completedPhaseIds.add(phaseId);
            isDirty = true;
        }
    }

    public boolean isDirty() {
        return isDirty || (collectionData != null && collectionData.isDirty());
    }

    public void clearDirty() {
        this.isDirty = false;
        if (collectionData != null) collectionData.clearDirty();
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("QuestId", questId);
        tag.putInt("SchemaVersion", 3);
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

        CompoundTag progressTag = new CompoundTag();
        for (Map.Entry<String, int[]> e : phaseProgress.entrySet()) progressTag.putIntArray(e.getKey(), e.getValue());
        tag.put("PhaseProgress", progressTag);
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
    }

    public QuestRuntimeData copy() {
        LinkedHashSet<String> active = new LinkedHashSet<>(activePhaseIds);
        LinkedHashSet<String> completed = new LinkedHashSet<>(completedPhaseIds);
        LinkedHashMap<String, int[]> progress = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> e : phaseProgress.entrySet())
            progress.put(e.getKey(), Arrays.copyOf(e.getValue(), e.getValue().length));
        CollectionRuntimeData collectionDataCopy = collectionData != null ? collectionData.copy() : null;
        return new QuestRuntimeData(questId, state, active, completed, progress, collectionDataCopy, acceptedAtTick, acceptedAtRealMs, acceptedAtDayTime);
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
