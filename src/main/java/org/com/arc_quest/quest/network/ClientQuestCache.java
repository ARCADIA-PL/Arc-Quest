package org.com.arc_quest.quest.network;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.com.arc_quest.quest.api.QuestState;
import org.com.arc_quest.quest.capability.QuestRuntimeData;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 客户端任务数据镜像缓存。
 * <p>
 * <b>重要</b>：此类仅在客户端存在有效数据，由 S2C 网络包更新。
 * GUI 渲染代码应从此处读取数据，而非直接访问 Capability（客户端 Capability 在 SP 模式可用，
 * 但在 MP 模式下必须通过网络同步）。
 * <p>
 * <b>线程安全</b>：所有更新通过 {@code enqueueWork} 在客户端主线程执行，
 * 读取也在渲染线程（同一线程）进行，因此无需加锁。
 */
public final class ClientQuestCache {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ClientQuestCache INSTANCE = new ClientQuestCache();

    /** 活跃任务（客户端镜像） */
    private final Map<String, QuestRuntimeData> activeQuests = new LinkedHashMap<>();

    /** 已完成任务 ID */
    private final Set<String> completedQuests = new LinkedHashSet<>();

    /** 已失败任务 ID */
    private final Set<String> failedQuests = new LinkedHashSet<>();

    /** 全局 Flags */
    private final Set<String> flags = new HashSet<>();

    /** 全局 Variables */
    private final Map<String, Integer> variables = new HashMap<>();

    private ClientQuestCache() {}

    // ═══════════════════════════════════════════════════════
    //  网络包调用的更新方法
    // ═══════════════════════════════════════════════════════

    /**
     * 全量同步（来自 {@link S2CSyncFullDataPacket}）。
     */
    public void applyFullSync(CompoundTag capData) {
        activeQuests.clear();
        completedQuests.clear();
        failedQuests.clear();
        flags.clear();
        variables.clear();

        // 活跃任务
        ListTag activeList = capData.getList("ActiveQuests", Tag.TAG_COMPOUND);
        for (int i = 0; i < activeList.size(); i++) {
            QuestRuntimeData data = QuestRuntimeData.deserializeNBT(activeList.getCompound(i));
            activeQuests.put(data.getQuestId(), data);
        }

        // 已完成
        ListTag completedList = capData.getList("CompletedQuests", Tag.TAG_STRING);
        for (int i = 0; i < completedList.size(); i++) {
            completedQuests.add(completedList.getString(i));
        }

        // 已失败
        ListTag failedList = capData.getList("FailedQuests", Tag.TAG_STRING);
        for (int i = 0; i < failedList.size(); i++) {
            failedQuests.add(failedList.getString(i));
        }

        // Flags
        ListTag flagList = capData.getList("Flags", Tag.TAG_STRING);
        for (int i = 0; i < flagList.size(); i++) {
            flags.add(flagList.getString(i));
        }

        // Variables
        CompoundTag varsTag = capData.getCompound("Variables");
        for (String key : varsTag.getAllKeys()) {
            variables.put(key, varsTag.getInt(key));
        }

        LOGGER.debug("[ClientCache] Full sync applied: {} active, {} completed, {} flags",
                activeQuests.size(), completedQuests.size(), flags.size());
    }

    /**
     * 单任务状态更新（来自 {@link S2CSyncQuestStatePacket}）。
     */
    public void updateQuest(QuestRuntimeData data) {
        String questId = data.getQuestId();

        switch (data.getState()) {
            case ACTIVE -> {
                activeQuests.put(questId, data);
                completedQuests.remove(questId);
                failedQuests.remove(questId);
            }
            case COMPLETED -> {
                activeQuests.remove(questId);
                completedQuests.add(questId);
                failedQuests.remove(questId);
            }
            case FAILED -> {
                activeQuests.remove(questId);
                failedQuests.add(questId);
            }
            default -> activeQuests.put(questId, data);
        }

        LOGGER.debug("[ClientCache] Quest updated: {} → {}", questId, data.getState());
    }

    /**
     * 单目标进度更新（来自 {@link S2CSyncObjectivePacket}）。
     */
    public void updateObjectiveProgress(String questId, int objIndex, int newProgress) {
        QuestRuntimeData data = activeQuests.get(questId);
        if (data == null) {
            LOGGER.warn("[ClientCache] Received objective update for unknown quest: {}", questId);
            return;
        }
        data.setObjectiveProgress(objIndex, newProgress);

        LOGGER.debug("[ClientCache] Objective updated: {}#{}={}", questId, objIndex, newProgress);
    }

    /**
     * Flags / Variables 更新（来自 {@link S2CSyncFlagsVarsPacket}）。
     */
    public void updateFlagsAndVars(Set<String> newFlags, Map<String, Integer> newVars) {
        flags.clear();
        flags.addAll(newFlags);
        variables.clear();
        variables.putAll(newVars);

        LOGGER.debug("[ClientCache] Flags/Vars updated: {} flags, {} vars",
                flags.size(), variables.size());
    }

    // ═══════════════════════════════════════════════════════
    //  GUI 读取接口（只读）
    // ═══════════════════════════════════════════════════════

    /** 获取活跃任务数据（可能为 null）。 */
    @Nullable
    public QuestRuntimeData getActiveQuest(String questId) {
        return activeQuests.get(questId);
    }

    /** 获取所有活跃任务（不可变视图）。 */
    public Map<String, QuestRuntimeData> getAllActiveQuests() {
        return Collections.unmodifiableMap(activeQuests);
    }

    /** 任务是否正在进行。 */
    public boolean isQuestActive(String questId) {
        return activeQuests.containsKey(questId);
    }

    /** 任务是否已完成。 */
    public boolean isQuestCompleted(String questId) {
        return completedQuests.contains(questId);
    }

    /** 任务是否已失败。 */
    public boolean isQuestFailed(String questId) {
        return failedQuests.contains(questId);
    }

    /** 获取已完成任务列表。 */
    public Set<String> getCompletedQuests() {
        return Collections.unmodifiableSet(completedQuests);
    }

    /** 获取已失败任务列表。 */
    public Set<String> getFailedQuests() {
        return Collections.unmodifiableSet(failedQuests);
    }

    /** 是否有某个全局 Flag。 */
    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    /** 获取全局变量值。 */
    public int getVariable(String key) {
        return variables.getOrDefault(key, 0);
    }

    /** 获取所有 Flags。 */
    public Set<String> getAllFlags() {
        return Collections.unmodifiableSet(flags);
    }

    /** 获取所有 Variables。 */
    public Map<String, Integer> getAllVariables() {
        return Collections.unmodifiableMap(variables);
    }

    /** 清空所有缓存（断开连接时调用）。 */
    public void clear() {
        activeQuests.clear();
        completedQuests.clear();
        failedQuests.clear();
        flags.clear();
        variables.clear();
        LOGGER.debug("[ClientCache] Cache cleared.");
    }
}