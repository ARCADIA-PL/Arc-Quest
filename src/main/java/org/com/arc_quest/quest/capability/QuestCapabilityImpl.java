package org.com.arc_quest.quest.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.*;

/**
 * {@link IQuestCapability} 的标准实现。
 * <p>
 * 全部数据以 Java 集合形式持有，序列化到 NBT（存盘）/FriendlyByteBuf（网络）。
 */
public class QuestCapabilityImpl implements IQuestCapability {

    /**
     * 活跃任务：questId → 运行时数据
     */
    private final Map<String, QuestRuntimeData> activeQuests = new LinkedHashMap<>();

    /**
     * 已完成的任务 ID
     */
    private final Set<String> completedQuests = new LinkedHashSet<>();

    /**
     * 已失败的任务 ID
     */
    private final Set<String> failedQuests = new LinkedHashSet<>();

    /**
     * 全局标记
     */
    private final Set<String> flags = new HashSet<>();

    /**
     * 全局变量
     */
    private final Map<String, Integer> variables = new HashMap<>();

    // ═══════════════════════════════════════════════════════
    //  任务生命周期
    // ═══════════════════════════════════════════════════════

    @Override
    public void addActiveQuest(QuestRuntimeData data) {
        Objects.requireNonNull(data);
        activeQuests.put(data.getQuestId(), data);
    }

    @Override
    public void removeActiveQuest(String questId) {
        activeQuests.remove(questId);
    }

    @Override
    public void markCompleted(String questId) {
        activeQuests.remove(questId);
        completedQuests.add(questId);
        failedQuests.remove(questId); // 安全起见
    }

    @Override
    public void markFailed(String questId) {
        activeQuests.remove(questId);
        failedQuests.add(questId);
    }

    @Nullable
    @Override
    public QuestRuntimeData getActiveQuest(String questId) {
        return activeQuests.get(questId);
    }

    @Override
    public Map<String, QuestRuntimeData> getAllActiveQuests() {
        return Collections.unmodifiableMap(activeQuests);
    }

    @Override
    public Set<String> getCompletedQuests() {
        return Collections.unmodifiableSet(completedQuests);
    }

    @Override
    public Set<String> getFailedQuests() {
        return Collections.unmodifiableSet(failedQuests);
    }

    @Override
    public boolean isQuestActive(String questId) {
        return activeQuests.containsKey(questId);
    }

    @Override
    public boolean isQuestCompleted(String questId) {
        return completedQuests.contains(questId);
    }

    @Override
    public boolean isQuestFailed(String questId) {
        return failedQuests.contains(questId);
    }

    // ═══════════════════════════════════════════════════════
    //  Flag 系统
    // ═══════════════════════════════════════════════════════

    @Override
    public void setFlag(String flag) {
        flags.add(flag);
    }

    @Override
    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    @Override
    public void removeFlag(String flag) {
        flags.remove(flag);
    }

    @Override
    public Set<String> getAllFlags() {
        return Collections.unmodifiableSet(flags);
    }

    // ═══════════════════════════════════════════════════════
    //  Variable 系统
    // ═══════════════════════════════════════════════════════

    @Override
    public int getVariable(String key) {
        return variables.getOrDefault(key, 0);
    }

    @Override
    public void setVariable(String key, int value) {
        variables.put(key, value);
    }

    @Override
    public void incrementVariable(String key, int amount) {
        variables.merge(key, amount, Integer::sum);
    }

    @Override
    public Map<String, Integer> getAllVariables() {
        return Collections.unmodifiableMap(variables);
    }

    // ═══════════════════════════════════════════════════════
    //  NBT 序列化
    // ═══════════════════════════════════════════════════════

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag root = new CompoundTag();

        // 活跃任务
        ListTag activeList = new ListTag();
        for (QuestRuntimeData data : activeQuests.values()) {
            activeList.add(data.serializeNBT());
        }
        root.put("ActiveQuests", activeList);

        // 已完成
        ListTag completedList = new ListTag();
        for (String id : completedQuests) {
            completedList.add(StringTag.valueOf(id));
        }
        root.put("CompletedQuests", completedList);

        // 已失败
        ListTag failedList = new ListTag();
        for (String id : failedQuests) {
            failedList.add(StringTag.valueOf(id));
        }
        root.put("FailedQuests", failedList);

        // Flags
        ListTag flagList = new ListTag();
        for (String f : flags) {
            flagList.add(StringTag.valueOf(f));
        }
        root.put("Flags", flagList);

        // Variables
        CompoundTag varsTag = new CompoundTag();
        for (Map.Entry<String, Integer> e : variables.entrySet()) {
            varsTag.putInt(e.getKey(), e.getValue());
        }
        root.put("Variables", varsTag);

        return root;
    }

    @Override
    public void deserializeNBT(CompoundTag root) {
        activeQuests.clear();
        completedQuests.clear();
        failedQuests.clear();
        flags.clear();
        variables.clear();

        // 活跃任务
        ListTag activeList = root.getList("ActiveQuests", Tag.TAG_COMPOUND);
        for (int i = 0; i < activeList.size(); i++) {
            QuestRuntimeData data = QuestRuntimeData.deserializeNBT(activeList.getCompound(i));
            activeQuests.put(data.getQuestId(), data);
        }

        // 已完成
        ListTag completedList = root.getList("CompletedQuests", Tag.TAG_STRING);
        for (int i = 0; i < completedList.size(); i++) {
            completedQuests.add(completedList.getString(i));
        }

        // 已失败
        ListTag failedList = root.getList("FailedQuests", Tag.TAG_STRING);
        for (int i = 0; i < failedList.size(); i++) {
            failedQuests.add(failedList.getString(i));
        }

        // Flags
        ListTag flagList = root.getList("Flags", Tag.TAG_STRING);
        for (int i = 0; i < flagList.size(); i++) {
            flags.add(flagList.getString(i));
        }

        // Variables
        CompoundTag varsTag = root.getCompound("Variables");
        for (String key : varsTag.getAllKeys()) {
            variables.put(key, varsTag.getInt(key));
        }
    }

    // ═══════════════════════════════════════════════════════
    //  深拷贝（死亡克隆）
    // ═══════════════════════════════════════════════════════

    @Override
    public void copyFrom(IQuestCapability other) {
        // 最简洁的方式：序列化 → 反序列化
        this.deserializeNBT(other.serializeNBT());
    }

    @Override
    public void clearAllData() {
        activeQuests.clear();
        completedQuests.clear();
        failedQuests.clear();
        flags.clear();
        variables.clear();
    }
}