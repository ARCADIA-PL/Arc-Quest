package org.com.arc_quest.quest.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.com.arc_quest.quest.api.QuestState;

import java.util.Arrays;
import java.util.Objects;

/**
 * 任务运行时数据，存储在 Capability 中。
 */
public final class QuestRuntimeData {

    private final String questId;
    private final long acceptedAtTick;
    private QuestState state;
    private String currentPhaseId;
    private int[] objectiveProgress;
    private boolean isDirty = false;

    // ── 构造 ──────────────────────────────────────────────

    public QuestRuntimeData(String questId,
                            String initialPhaseId,
                            int objectiveCount,
                            long acceptedAtTick) {
        this.questId = Objects.requireNonNull(questId);
        this.state = QuestState.ACTIVE;
        this.currentPhaseId = Objects.requireNonNull(initialPhaseId);
        this.objectiveProgress = new int[objectiveCount];
        this.acceptedAtTick = acceptedAtTick;
    }

    private QuestRuntimeData(String questId,
                             QuestState state,
                             String currentPhaseId,
                             int[] objectiveProgress,
                             long acceptedAtTick) {
        this.questId = questId;
        this.state = state;
        this.currentPhaseId = currentPhaseId;
        this.objectiveProgress = objectiveProgress;
        this.acceptedAtTick = acceptedAtTick;
    }

    public static QuestRuntimeData deserializeNBT(CompoundTag tag) {
        String questId = tag.getString("QuestId");
        QuestState state;
        try {
            state = QuestState.valueOf(tag.getString("State"));
        } catch (IllegalArgumentException e) {
            state = QuestState.ACTIVE;
        }
        String phaseId = tag.getString("PhaseId");
        int[] progress = tag.getIntArray("Progress");
        long accepted = tag.getLong("AcceptedAt");

        return new QuestRuntimeData(questId, state, phaseId,
                Arrays.copyOf(progress, progress.length), accepted);
    }

    public static QuestRuntimeData readFromNetwork(FriendlyByteBuf buf) {
        String questId = buf.readUtf(256);
        QuestState state = buf.readEnum(QuestState.class);
        String phaseId = buf.readUtf(256);
        int len = buf.readVarInt();
        int[] progress = new int[len];
        for (int i = 0; i < len; i++) {
            progress[i] = buf.readVarInt();
        }
        long accepted = buf.readLong();
        return new QuestRuntimeData(questId, state, phaseId, progress, accepted);
    }

    public String getQuestId()          { return questId; }
    public QuestState getState()        { return state; }
    public String getCurrentPhaseId()   { return currentPhaseId; }
    public int getObjectiveCount()      { return objectiveProgress.length; }
    public long getAcceptedAtTick()     { return acceptedAtTick; }

    public void setState(QuestState state) {
        this.state = Objects.requireNonNull(state);
    }

    public void setCurrentPhaseId(String phaseId) {
        this.currentPhaseId = Objects.requireNonNull(phaseId);
    }

    public int getObjectiveProgress(int index) {
        if (index < 0 || index >= objectiveProgress.length) return 0;
        return objectiveProgress[index];
    }

    public int[] getAllProgress() {
        return Arrays.copyOf(objectiveProgress, objectiveProgress.length);
    }

    /**
     * 增加目标进度，返回增加后的值。不会超过 {@code clampMax}（若 <= 0 则无上限）。
     */
    public int incrementProgress(int index, int amount, int clampMax) {
        if (index < 0 || index >= objectiveProgress.length) return 0;
        objectiveProgress[index] += amount;
        if (clampMax > 0 && objectiveProgress[index] > clampMax) {
            objectiveProgress[index] = clampMax;
        }
        this.isDirty = true;
        return objectiveProgress[index];
    }

    public void setObjectiveProgress(int index, int value) {
        if (index >= 0 && index < objectiveProgress.length) {
            objectiveProgress[index] = value;
            this.isDirty = true;
        }
    }

    /**
     * 重置目标进度数组（切换阶段时调用）。
     */
    public void resetObjectives(int newCount) {
        this.objectiveProgress = new int[newCount];
    }

    // ════════════════════════════════════════
    //  脏标记管理
    // ════════════════════════════════════════

    public boolean isDirty()  { return isDirty; }
    public void clearDirty()  { this.isDirty = false; }

    // ── NBT 序列化 ────────────────────────────────────────

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("QuestId", questId);
        tag.putString("State", state.name());
        tag.putString("PhaseId", currentPhaseId);
        tag.putIntArray("Progress", objectiveProgress);
        tag.putLong("AcceptedAt", acceptedAtTick);
        return tag;
    }

    // ── 网络序列化 ────────────────────────────────────────

    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeUtf(questId);
        buf.writeEnum(state);
        buf.writeUtf(currentPhaseId);
        buf.writeVarInt(objectiveProgress.length);
        for (int p : objectiveProgress) {
            buf.writeVarInt(p);
        }
        buf.writeLong(acceptedAtTick);
    }

    // ── 深拷贝 ────────────────────────────────────────────

    public QuestRuntimeData copy() {
        return new QuestRuntimeData(
                questId, state, currentPhaseId,
                Arrays.copyOf(objectiveProgress, objectiveProgress.length),
                acceptedAtTick);
    }

    @Override
    public String toString() {
        return "QuestRuntimeData{" +
                "quest='" + questId + '\'' +
                ", state=" + state +
                ", phase='" + currentPhaseId + '\'' +
                ", progress=" + Arrays.toString(objectiveProgress) +
                '}';
    }
}
