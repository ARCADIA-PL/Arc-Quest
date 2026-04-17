package org.com.arc_quest.quest.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.com.arc_quest.quest.api.QuestState;

import java.util.*;

/**
 * 单个任务的运行时可变数据。
 * 存储在 Capability 的 Map<String, QuestRuntimeData> 中。
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code questId}          — 引用 QuestDefinition 的唯一键</li>
 *   <li>{@code state}            — 当前状态 (IN_PROGRESS / COMPLETED / FAILED)</li>
 *   <li>{@code currentPhaseId}   — 当前所处阶段 ID</li>
 *   <li>{@code objectiveProgress}— 当前阶段每个目标的已完成数量</li>
 *   <li>{@code acceptedAtTick}   — 接受任务时的游戏刻</li>
 *   <li>{@code localFlags}       — 任务级局部标记（不影响全局 Flag）</li>
 * </ul>
 */
public final class QuestRuntimeData {

    private final String questId;
    private final Set<String> localFlags;
    private final List<String> completedPhases; // 已完成的 phase 历史
    private QuestState state;
    private String currentPhaseId;
    private int[] objectiveProgress;
    private final long acceptedAtTick;
    
    // P2优化：脏标记，用于I/O防抖
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
        this.localFlags = new HashSet<>();
        this.completedPhases = new ArrayList<>();
    }

    /**
     * 反序列化专用
     */
    private QuestRuntimeData(String questId,
                             QuestState state,
                             String currentPhaseId,
                             int[] objectiveProgress,
                             long acceptedAtTick,
                             Set<String> localFlags,
                             List<String> completedPhases) {
        this.questId = questId;
        this.state = state;
        this.currentPhaseId = currentPhaseId;
        this.objectiveProgress = objectiveProgress;
        this.acceptedAtTick = acceptedAtTick;
        this.localFlags = localFlags;
        this.completedPhases = completedPhases;
    }

    // ── Getters ───────────────────────────────────────────

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

        Set<String> flags = new HashSet<>();
        ListTag flagList = tag.getList("LocalFlags", Tag.TAG_STRING);
        for (int i = 0; i < flagList.size(); i++) {
            flags.add(flagList.getString(i));
        }

        List<String> completedPhases = new ArrayList<>();
        ListTag phaseList = tag.getList("CompletedPhases", Tag.TAG_STRING);
        for (int i = 0; i < phaseList.size(); i++) {
            completedPhases.add(phaseList.getString(i));
        }

        return new QuestRuntimeData(questId, state, phaseId,
                Arrays.copyOf(progress, progress.length), accepted, flags, completedPhases);
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
        int flagCount = buf.readVarInt();
        Set<String> flags = new HashSet<>(flagCount);
        for (int i = 0; i < flagCount; i++) {
            flags.add(buf.readUtf(256));
        }
        
        int phaseCount = buf.readVarInt();
        List<String> completedPhases = new ArrayList<>(phaseCount);
        for (int i = 0; i < phaseCount; i++) {
            completedPhases.add(buf.readUtf(256));
        }
        
        return new QuestRuntimeData(questId, state, phaseId, progress, accepted, flags, completedPhases);
    }

    public String getQuestId() {
        return questId;
    }

    public QuestState getState() {
        return state;
    }

    public void setState(QuestState state) {
        this.state = Objects.requireNonNull(state);
    }

    public String getCurrentPhaseId() {
        return currentPhaseId;
    }

    public void setCurrentPhaseId(String phaseId) {
        // 切换 phase 时，记录旧的 phase 为已完成
        if (!this.currentPhaseId.equals(phaseId)) {
            if (!completedPhases.contains(this.currentPhaseId)) {
                completedPhases.add(this.currentPhaseId);
            }
        }
        this.currentPhaseId = Objects.requireNonNull(phaseId);
    }

    public int getObjectiveCount() {
        return objectiveProgress.length;
    }

    // ── Setters ───────────────────────────────────────────

    public long getAcceptedAtTick() {
        return acceptedAtTick;
    }

    public Set<String> getLocalFlags() {
        return Collections.unmodifiableSet(localFlags);
    }

    public int getObjectiveProgress(int index) {
        if (index < 0 || index >= objectiveProgress.length) return 0;
        return objectiveProgress[index];
    }

    public int[] getAllProgress() {
        return Arrays.copyOf(objectiveProgress, objectiveProgress.length);
    }

    /**
     * 增加目标进度，返回增加后的值。
     * 不会超过 {@code clampMax}（若 clampMax <= 0 则不做上限限制）。
     * 
     * P2优化：设置脏标记，延迟同步和保存
     */
    public int incrementProgress(int index, int amount, int clampMax) {
        if (index < 0 || index >= objectiveProgress.length) return 0;
        objectiveProgress[index] += amount;
        if (clampMax > 0 && objectiveProgress[index] > clampMax) {
            objectiveProgress[index] = clampMax;
        }
        this.isDirty = true; // 标记为脏数据
        return objectiveProgress[index];
    }

    public void setObjectiveProgress(int index, int value) {
        if (index >= 0 && index < objectiveProgress.length) {
            objectiveProgress[index] = value;
            this.isDirty = true; // 标记为脏数据
        }
    }

    /**
     * 重置目标进度数组（切换阶段时调用）。
     */
    public void resetObjectives(int newCount) {
        this.objectiveProgress = new int[newCount];
    }

    public void addLocalFlag(String flag) {
        localFlags.add(flag);
    }

    // ── NBT 序列化 ────────────────────────────────────────

    public boolean hasLocalFlag(String flag) {
        return localFlags.contains(flag);
    }

    public void removeLocalFlag(String flag) {
        localFlags.remove(flag);
    }

    public List<String> getCompletedPhases() {
        return Collections.unmodifiableList(completedPhases);
    }

    // ════════════════════════════════════════
    //  P2优化：脏标记管理
    // ════════════════════════════════════════

    /**
     * 检查数据是否为脏（需要保存/同步）。
     */
    public boolean isDirty() {
        return isDirty;
    }

    /**
     * 重置脏标记（在保存/同步后调用）。
     */
    public void clearDirty() {
        this.isDirty = false;
    }

    // ── 网络序列化 ────────────────────────────────────────

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("QuestId", questId);
        tag.putString("State", state.name());
        tag.putString("PhaseId", currentPhaseId);
        tag.putIntArray("Progress", objectiveProgress);
        tag.putLong("AcceptedAt", acceptedAtTick);

        ListTag flagList = new ListTag();
        for (String f : localFlags) {
            flagList.add(StringTag.valueOf(f));
        }
        tag.put("LocalFlags", flagList);

        ListTag phaseList = new ListTag();
        for (String p : completedPhases) {
            phaseList.add(StringTag.valueOf(p));
        }
        tag.put("CompletedPhases", phaseList);

        return tag;
    }

    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeUtf(questId);
        buf.writeEnum(state);
        buf.writeUtf(currentPhaseId);
        buf.writeVarInt(objectiveProgress.length);
        for (int p : objectiveProgress) {
            buf.writeVarInt(p);
        }
        buf.writeLong(acceptedAtTick);
        buf.writeVarInt(localFlags.size());
        for (String f : localFlags) {
            buf.writeUtf(f);
        }
        buf.writeVarInt(completedPhases.size());
        for (String p : completedPhases) {
            buf.writeUtf(p);
        }
    }

    // ── 深拷贝 ────────────────────────────────────────────

    public QuestRuntimeData copy() {
        return new QuestRuntimeData(
                questId, state, currentPhaseId,
                Arrays.copyOf(objectiveProgress, objectiveProgress.length),
                acceptedAtTick,
                new HashSet<>(localFlags),
                new ArrayList<>(completedPhases));
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