package org.com.arc_quest.quest.network;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.com.arc_quest.client.util.GuiSoundManager;
import org.com.arc_quest.client.gui.quest.journal.QuestJournalScreen;
import org.com.arc_quest.client.gui.quest.QuestToastManager;
import org.com.arc_quest.quest.api.PhaseDefinition;
import org.com.arc_quest.quest.api.QuestDefinition;
import org.com.arc_quest.quest.api.QuestState;
import org.com.arc_quest.quest.capability.QuestRuntimeData;
import org.com.arc_quest.quest.registry.QuestRegistry;
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
 * <b>线程模型</b>：所有更新通过 {@code enqueueWork} 在客户端主线程执行，
 * 读取也在渲染线程（同一线程）进行，因此无需加锁。
 * <p>
 * <b>多任务支持</b>：使用 LinkedHashMap 存储所有活跃任务，支持同时追踪多个任务。
 */
public final class ClientQuestCache {

    public static final ClientQuestCache INSTANCE = new ClientQuestCache();
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 活跃任务（客户端镜像）
     * 使用 LinkedHashMap 保持插入顺序，支持多任务并发
     */
    private final Map<String, QuestRuntimeData> activeQuests = new LinkedHashMap<>();

    /**
     * 已完成任务 ID
     */
    private final Set<String> completedQuests = new LinkedHashSet<>();

    /**
     * 已失败任务 ID
     */
    private final Set<String> failedQuests = new LinkedHashSet<>();

    /**
     * 全局 Flags
     */
    private final Set<String> flags = new HashSet<>();

    /**
     * 全局 Variables
     */
    private final Map<String, Integer> variables = new HashMap<>();

    private ClientQuestCache() {
    }

    private boolean hasAppliedFullSync = false;

    // ═══════════════════════════════════════════════════════
    //  网络包调用的更新方法
    // ═══════════════════════════════════════════════════════

    /**
     * 全量同步（来自 {@link S2CSyncFullDataPacket}）。
     */
    public void applyFullSync(CompoundTag capData) {
        Set<String> oldFailed = new LinkedHashSet<>(failedQuests);

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

        if (hasAppliedFullSync) {
            for (String questId : failedQuests) {
                if (!oldFailed.contains(questId)) {
                    String name = getQuestDisplayName(questId);
                    QuestToastManager.show(QuestToastManager.ToastType.QUEST_FAILED, name);
                }
            }
        }

        hasAppliedFullSync = true;

        LOGGER.debug("[ClientCache] Full sync applied: {} active, {} completed, {} failed, {} flags",
                activeQuests.size(), completedQuests.size(), failedQuests.size(), flags.size());
        refreshJournalIfOpen();
    }

    /**
     * 单任务状态更新（来自 {@link S2CSyncQuestStatePacket}）。
     */
    public void updateQuest(QuestRuntimeData data) {
        String questId = data.getQuestId();
        QuestState oldState = null;
        String oldPhaseId = null;

        // 记录旧状态用于动画和音效触发
        if (activeQuests.containsKey(questId)) {
            QuestRuntimeData oldData = activeQuests.get(questId);
            oldState = oldData.getState();
            oldPhaseId = oldData.getCurrentPhaseId();
        } else if (completedQuests.contains(questId)) {
            oldState = QuestState.COMPLETED;
        } else if (failedQuests.contains(questId)) {
            oldState = QuestState.FAILED;
        }

        switch (data.getState()) {
            case ACTIVE -> {
                activeQuests.put(questId, data);
                completedQuests.remove(questId);
                failedQuests.remove(questId);

                // 触发动画钩子：接取任务
                if (oldState == null) {
                    onQuestAccepted(questId);
                }

                // 触发 Phase 开始音效（如果是新阶段）
                if (oldPhaseId != null && !oldPhaseId.equals(data.getCurrentPhaseId())) {
                    onPhaseStarted(questId, data.getCurrentPhaseId());
                }
            }
            case COMPLETED -> {
                activeQuests.remove(questId);
                completedQuests.add(questId);
                failedQuests.remove(questId);

                // 触发动画钩子：完成任务
                if (oldState != QuestState.COMPLETED) {
                    onQuestCompleted(questId);
                }
            }
            case FAILED -> {
                activeQuests.remove(questId);
                failedQuests.add(questId);

                // 触发动画钩子：任务失败
                if (oldState != QuestState.FAILED) {
                    onQuestFailed(questId);
                }
            }
            default -> {
                // 处理阶段切换（即使状态没变，阶段也可能变了）
                if (activeQuests.containsKey(questId)) {
                    String curPhase = activeQuests.get(questId).getCurrentPhaseId();
                    if (!curPhase.equals(data.getCurrentPhaseId())) {
                        onPhaseStarted(questId, data.getCurrentPhaseId());
                    }
                }
                activeQuests.put(questId, data);
            }
        }

        LOGGER.debug("[ClientCache] Quest updated: {} → {}", questId, data.getState());
        refreshJournalIfOpen();
    }

    /**
     * 单目标进度更新（来自 {@link S2CSyncObjectivePacket}）。
     * <p>
     * 【时序安全优化】使用深拷贝替换策略，避免UI层在读取过程中被网络包中断导致数据不一致。
     */
    public void updateObjectiveProgress(String questId, int objIndex, int newProgress) {
        QuestRuntimeData oldData = activeQuests.get(questId);
        if (oldData == null) {
            LOGGER.warn("[ClientCache] Received objective update for unknown quest: {}", questId);
            return;
        }

        // 边界检查
        if (objIndex < 0) {
            LOGGER.warn("[ClientCache] Invalid objective index: {} for quest: {}", objIndex, questId);
            return;
        }

        int oldProgress = oldData.getObjectiveProgress(objIndex);
        
        // 防止进度回退（除非服务端明确允许）
        if (newProgress < oldProgress) {
            LOGGER.debug("[ClientCache] Objective progress decreased: {}#{} {}→{}", questId, objIndex, oldProgress, newProgress);
        }
        
        // 【修复】创建深拷贝并替换，确保原子性更新
        QuestRuntimeData newData = oldData.copy();
        newData.setObjectiveProgress(objIndex, newProgress);
        activeQuests.put(questId, newData);

        // 触发动画钩子：目标进度更新
        if (newProgress > oldProgress) {
            onObjectiveProgressed(questId, objIndex, oldProgress, newProgress);
        }

        LOGGER.debug("[ClientCache] Objective updated: {}#{}={}", questId, objIndex, newProgress);
    }

    public void updateObjectiveProgress(String questId, String phaseId, int objIndex, int newProgress) {
        QuestRuntimeData oldData = activeQuests.get(questId);
        if (oldData == null) {
            LOGGER.warn("[ClientCache] Received objective update for unknown quest: {}", questId);
            return;
        }

        if (objIndex < 0) {
            LOGGER.warn("[ClientCache] Invalid objective index: {} for quest: {}", objIndex, questId);
            return;
        }

        int oldProgress = oldData.getObjectiveProgress(phaseId, objIndex);

        if (newProgress < oldProgress) {
            LOGGER.debug("[ClientCache] Objective progress decreased: {}/{}#{} {}→{}", questId, phaseId, objIndex, oldProgress, newProgress);
        }

        QuestRuntimeData newData = oldData.copy();
        newData.setObjectiveProgress(phaseId, objIndex, newProgress);
        activeQuests.put(questId, newData);

        if (newProgress > oldProgress) {
            onObjectiveProgressed(questId, objIndex, oldProgress, newProgress);
        }

        LOGGER.debug("[ClientCache] Objective updated: {}/{}#{}={}", questId, phaseId, objIndex, newProgress);
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

    /**
     * 获取活跃任务数据（可能为 null）。
     */
    @Nullable
    public QuestRuntimeData getActiveQuest(String questId) {
        return activeQuests.get(questId);
    }

    /**
     * 获取所有活跃任务（不可变视图）。
     */
    public Map<String, QuestRuntimeData> getAllActiveQuests() {
        return Collections.unmodifiableMap(activeQuests);
    }

    /**
     * 任务是否正在进行。
     */
    public boolean isQuestActive(String questId) {
        return activeQuests.containsKey(questId);
    }

    /**
     * 任务是否已完成。
     */
    public boolean isQuestCompleted(String questId) {
        return completedQuests.contains(questId);
    }

    /**
     * 任务是否已失败。
     */
    public boolean isQuestFailed(String questId) {
        return failedQuests.contains(questId);
    }

    /**
     * 获取已完成任务列表。
     */
    public Set<String> getCompletedQuests() {
        return Collections.unmodifiableSet(completedQuests);
    }

    /**
     * 获取已失败任务列表。
     */
    public Set<String> getFailedQuests() {
        return Collections.unmodifiableSet(failedQuests);
    }

    /**
     * 是否有某个全局 Flag。
     */
    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    /**
     * 获取全局变量值。
     */
    public int getVariable(String key) {
        return variables.getOrDefault(key, 0);
    }

    /**
     * 获取所有 Flags。
     */
    public Set<String> getAllFlags() {
        return Collections.unmodifiableSet(flags);
    }

    /**
     * 获取所有 Variables。
     */
    public Map<String, Integer> getAllVariables() {
        return Collections.unmodifiableMap(variables);
    }

    /**
     * 清空所有缓存（断开连接时调用）。
     */
    public void clear() {
        activeQuests.clear();
        completedQuests.clear();
        failedQuests.clear();
        flags.clear();
        variables.clear();
        hasAppliedFullSync = false;
        LOGGER.info("[ClientCache] Cache cleared.");
    }

    // ═══════════════════════════════════════════════════════
    //  增强查询 API
    // ═══════════════════════════════════════════════════════

    /**
     * 获取任务的当前阶段定义。
     *
     * @param questId 任务 ID
     * @return 阶段定义，如果任务不存在或阶段无效则返回 null
     */
    @Nullable
    public PhaseDefinition getCurrentPhase(String questId) {
        QuestRuntimeData data = activeQuests.get(questId);
        if (data == null) return null;

        ResourceLocation rl = ResourceLocation.tryParse(questId);
        if (rl == null) return null;

        QuestDefinition def = QuestRegistry.get(rl);
        if (def == null) return null;

        for (String phaseId : data.getActivePhaseIds()) {
            PhaseDefinition p = def.getPhase(phaseId);
            if (p != null) return p;
        }
        return null;
    }

    public Set<String> getActivePhaseIds(String questId) {
        QuestRuntimeData data = activeQuests.get(questId);
        if (data == null) return Collections.emptySet();
        return data.getActivePhaseIds();
    }

    /**
     * 获取目标的剩余进度。
     *
     * @param questId  任务 ID
     * @param objIndex 目标索引
     * @return 剩余进度，如果任务或目标不存在则返回 -1
     */
    public int getObjectiveRemaining(String questId, int objIndex) {
        QuestRuntimeData data = activeQuests.get(questId);
        if (data == null) return -1;

        ResourceLocation rl = ResourceLocation.tryParse(questId);
        if (rl == null) return -1;

        QuestDefinition def = QuestRegistry.get(rl);
        if (def == null) return -1;

        PhaseDefinition phase = def.getPhase(data.getCurrentPhaseId());
        if (phase == null || objIndex < 0 || objIndex >= phase.getObjectives().size()) {
            return -1;
        }

        int current = data.getObjectiveProgress(objIndex);
        int required = phase.getObjectives().get(objIndex).getRequiredCount();
        return Math.max(0, required - current);
    }

    /**
     * 获取任务的整体进度百分比（0-100）。
     *
     * @param questId 任务 ID
     * @return 进度百分比，如果任务不存在则返回 -1
     */
    public int getQuestProgressPercent(String questId) {
        QuestRuntimeData data = activeQuests.get(questId);
        if (data == null) return -1;

        ResourceLocation rl = ResourceLocation.tryParse(questId);
        if (rl == null) return -1;

        QuestDefinition def = QuestRegistry.get(rl);
        if (def == null) return -1;

        PhaseDefinition phase = def.getPhase(data.getCurrentPhaseId());
        if (phase == null || phase.getObjectives().isEmpty()) return 0;

        int totalRequired = 0;
        int totalCurrent = 0;

        for (int i = 0; i < phase.getObjectives().size(); i++) {
            int required = phase.getObjectives().get(i).getRequiredCount();
            int current = Math.min(data.getObjectiveProgress(i), required);
            totalRequired += required;
            totalCurrent += current;
        }

        if (totalRequired == 0) return 100;
        return (int) ((totalCurrent * 100.0) / totalRequired);
    }

    /**
     * 按分类获取活跃任务。
     *
     * @param category 任务分类
     * @return 匹配的任务 ID 列表
     */
    public List<String> getActiveQuestsByCategory(String category) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, QuestRuntimeData> entry : activeQuests.entrySet()) {
            ResourceLocation rl = ResourceLocation.tryParse(entry.getKey());
            if (rl != null) {
                QuestDefinition def = QuestRegistry.get(rl);
                if (def != null && category.equals(def.getCategory().getId())) {
                    result.add(entry.getKey());
                }
            }
        }
        return result;
    }

    /**
     * 获取任务的主题色（带默认值）。
     *
     * @param questId      任务 ID
     * @param defaultColor 默认颜色（如果未配置则返回此值）
     * @return 主题色 ARGB 值
     */
    public int getQuestThemeColor(String questId, int defaultColor) {
        ResourceLocation rl = ResourceLocation.tryParse(questId);
        if (rl == null) return defaultColor;

        QuestDefinition def = QuestRegistry.get(rl);
        if (def == null) return defaultColor;

        int themeColor = def.getThemeColor();
        return (themeColor != 0xFFFFFFFF) ? themeColor : defaultColor;
    }

    /**
     * 获取任务的显示名称。
     *
     * @param questId 任务 ID
     * @return 显示名称，如果任务不存在则返回 questId
     */
    public String getQuestDisplayName(String questId) {
        ResourceLocation rl = ResourceLocation.tryParse(questId);
        if (rl == null) return questId;

        QuestDefinition def = QuestRegistry.get(rl);
        return def != null ? def.getDisplayName().getString() : questId;
    }

    /**
     * 获取阶段的可读名称（优先使用 displayName）。
     *
     * @param questId 任务 ID
     * @param phaseId 阶段 ID
     * @return 阶段名称，如果无效则返回 phaseId
     */
    public String getPhaseDisplayName(String questId, String phaseId) {
        ResourceLocation rl = ResourceLocation.tryParse(questId);
        if (rl == null) return phaseId;

        QuestDefinition def = QuestRegistry.get(rl);
        if (def == null) return phaseId;

        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null) return phaseId;

        String displayName = phase.getDisplayName() != null ? phase.getDisplayName().getString() : "";
        return !displayName.isEmpty() ? displayName : phaseId;
    }

    /**
     * 解析追踪任务（自动选择第一个活跃任务作为后备）。
     * <p>
     * 这是 HUD 渲染常用的辅助方法，封装了追踪任务的 fallback 逻辑。
     *
     * @param trackedQuestId 当前追踪的任务 ID（可能为 null）
     * @return 追踪任务的运行时数据，如果没有活跃任务则返回 null
     */
    @Nullable
    public QuestRuntimeData resolveTrackedQuest(@Nullable String trackedQuestId) {
        // 1. 尝试获取指定的追踪任务
        if (trackedQuestId != null) {
            QuestRuntimeData data = activeQuests.get(trackedQuestId);
            if (data != null) return data;
        }

        // 2. 后备：返回第一个活跃任务（LinkedHashMap 保证插入顺序，即最早激活的任务）
        if (!activeQuests.isEmpty()) {
            return activeQuests.values().iterator().next();
        }

        return null;
    }

    /**
     * 获取已完成任务的 ResourceLocation 集合（用于条件检查）。
     *
     * @return 已完成任务的 RL 集合
     */
    public Set<ResourceLocation> getCompletedQuestsAsRL() {
        Set<ResourceLocation> result = new HashSet<>();
        for (String id : completedQuests) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) {
                result.add(rl);
            }
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════
    //  动画事件钩子（供外部 UI 引擎对接）
    // ═══════════════════════════════════════════════════════

    /**
     * 阶段开始时触发。
     */
    private void onPhaseStarted(String questId, String phaseId) {
        ResourceLocation rl = ResourceLocation.tryParse(questId);
        if (rl == null) {
            LOGGER.warn("[AnimationHook] Invalid quest ID format: {}", questId);
            return;
        }

        QuestDefinition def = QuestRegistry.get(rl);
        if (def == null) {
            LOGGER.warn("[AnimationHook] Quest definition not found: {}", questId);
            return;
        }

        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase != null) {
            GuiSoundManager.play(phase.getPhaseStartSound());
            LOGGER.info("[AnimationHook] Phase started: {}#{}", questId, phaseId);
        } else {
            LOGGER.warn("[AnimationHook] Phase not found: {}#{}", questId, phaseId);
        }
    }

    /**
     * 任务被接受时触发。
     */
    private void onQuestAccepted(String questId) {
        ResourceLocation rl = ResourceLocation.tryParse(questId);
        if (rl != null) {
            QuestDefinition def = QuestRegistry.get(rl);
            if (def != null) {
                GuiSoundManager.play(def.getChapterStartSound());
                LOGGER.info("[AnimationHook] Quest accepted: {}", questId);
            } else {
                LOGGER.warn("[AnimationHook] Quest definition not found for accepted quest: {}", questId);
            }
        } else {
            LOGGER.warn("[AnimationHook] Invalid quest ID format: {}", questId);
        }
    }

    /**
     * 任务完成时触发。
     */
    private void onQuestCompleted(String questId) {
        ResourceLocation rl = ResourceLocation.tryParse(questId);
        if (rl != null) {
            QuestDefinition def = QuestRegistry.get(rl);
            if (def != null) {
                GuiSoundManager.play(def.getChapterCompleteSound());
                LOGGER.info("[AnimationHook] Quest completed: {}", questId);
            } else {
                LOGGER.warn("[AnimationHook] Quest definition not found for completed quest: {}", questId);
            }
        } else {
            LOGGER.warn("[AnimationHook] Invalid quest ID format: {}", questId);
        }
    }

    /**
     * 任务失败时触发。
     */
    private void onQuestFailed(String questId) {
        ResourceLocation rl = ResourceLocation.tryParse(questId);
        if (rl != null) {
            QuestDefinition def = QuestRegistry.get(rl);
            if (def != null) {
                GuiSoundManager.play(def.getChapterFailSound());
                LOGGER.info("[AnimationHook] Quest failed: {}", questId);
            } else {
                LOGGER.warn("[AnimationHook] Quest definition not found for failed quest: {}", questId);
            }
        } else {
            LOGGER.warn("[AnimationHook] Invalid quest ID format: {}", questId);
        }
    }

    /**
     * 目标进度更新时触发（检测 Phase 完成）。
     */
    private void onObjectiveProgressed(String questId, int objIndex, int oldProgress, int newProgress) {
        ResourceLocation rl = ResourceLocation.tryParse(questId);
        if (rl == null) return;

        QuestDefinition def = QuestRegistry.get(rl);
        if (def == null) return;

        QuestRuntimeData data = activeQuests.get(questId);
        if (data == null) return;

        String currentPhaseId = data.getCurrentPhaseId();
        PhaseDefinition phase = def.getPhase(currentPhaseId);
        if (phase == null) return;

        // 检查当前阶段的所有目标是否都已达成
        boolean allDone = true;
        for (int i = 0; i < phase.getObjectives().size(); i++) {
            if (data.getObjectiveProgress(i) < phase.getObjectives().get(i).getRequiredCount()) {
                allDone = false;
                break;
            }
        }

        if (allDone) {
            GuiSoundManager.play(phase.getPhaseCompleteSound());
        }

        LOGGER.debug("[AnimationHook] Objective progressed: {}#{} {}→{}", questId, objIndex, oldProgress, newProgress);
    }

    private void refreshJournalIfOpen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof QuestJournalScreen journalScreen) {
            journalScreen.rebuildEntries();
        }
    }
}
