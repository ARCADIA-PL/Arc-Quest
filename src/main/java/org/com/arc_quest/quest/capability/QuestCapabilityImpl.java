package org.com.arc_quest.quest.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.com.arc_quest.dialogue.runtime.DialogueProgressStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;

/**
 * IQuestCapability 的标准实现。
 * <p>
 * <b>v2 变更</b>: 对话历史从 6 个 Map 合并为 {@link DialogueProgressStore}。
 * <b>v3 变更</b>: 引入 {@link NbtVersionManager} 统一管理版本号,支持链式迁移。
 */
public class QuestCapabilityImpl implements IQuestCapability {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(QuestCapabilityImpl.class);
    
    /**
     * NBT 版本管理器
     */
    private static final NbtVersionManager VERSION_MANAGER = new NbtVersionManager(
        "arc_quest:player_data",
        3,  // 当前版本
        LOGGER
    );
    
    static {
        // v0 → v1: 添加 Flags 字段
        VERSION_MANAGER.addMigration(0, 1, tag -> {
            if (!tag.contains("Flags", Tag.TAG_LIST)) {
                tag.put("Flags", new ListTag());
            }
        });
        
        // v1 → v2: 添加 DialogueProgress
        VERSION_MANAGER.addMigration(1, 2, tag -> {
            // 如果存在旧的 NodeVisitHistory 等字段,迁移到 DialogueProgress
            if (tag.contains("NodeVisitHistory", Tag.TAG_COMPOUND)) {
                CompoundTag dialogueProgress = new CompoundTag();
                // 迁移逻辑由 DialogueProgressStore.migrateFromLegacy 处理
                // 这里只是标记需要迁移,实际在 deserializeNBT 中处理
                tag.putInt("_needs_dialogue_migration", 1);
            } else if (!tag.contains("DialogueProgress", Tag.TAG_COMPOUND)) {
                tag.put("DialogueProgress", new CompoundTag());
            }
        });
        
        // v2 → v3: 改用 _ArcQuestVer
        VERSION_MANAGER.addMigration(2, 3, tag -> {
            // 复制旧的 _version 到 _ArcQuestVer
            if (tag.contains("_version", Tag.TAG_INT)) {
                int oldVersion = tag.getInt("_version");
                tag.putInt("_ArcQuestVer", Math.max(oldVersion, 3));
                tag.remove("_version");
            } else {
                tag.putInt("_ArcQuestVer", 3);
            }
            // 清理临时标记
            tag.remove("_needs_dialogue_migration");
        });
    }

    private final Map<String, QuestRuntimeData> activeQuests = new LinkedHashMap<>();
    private final Set<String> completedQuests = new LinkedHashSet<>();
    private final Set<String> failedQuests = new LinkedHashSet<>();
    private final Set<String> flags = new HashSet<>();
    private final Map<String, Integer> variables = new HashMap<>();

    /**
     * 统一对话进度存储
     */
    private final DialogueProgressStore dialogueProgress = new DialogueProgressStore();

    private boolean isDirty = false;

    // ═══════════════════════════════════════════════
    //  ⭐ v2 新增
    // ═══════════════════════════════════════════════

    @Override
    public DialogueProgressStore getDialogueProgress() {
        return dialogueProgress;
    }

    // ═══════════════════════════════════════════════
    // 任务管理
    // ═══════════════════════════════════════════════

    @Override
    public void addActiveQuest(QuestRuntimeData data) {
        Objects.requireNonNull(data);
        activeQuests.put(data.getQuestId(), data);
        isDirty = true;
    }

    @Override
    public void removeActiveQuest(String questId) {
        activeQuests.remove(questId);
        isDirty = true;
    }

    @Override
    public void markCompleted(String questId) {
        activeQuests.remove(questId);
        completedQuests.add(questId);
        failedQuests.remove(questId);
        isDirty = true;
    }

    @Override
    public void markFailed(String questId) {
        activeQuests.remove(questId);
        failedQuests.add(questId);
        isDirty = true;
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

    // ═══════════════════════════════════════════════
    // Flag
    // ═══════════════════════════════════════════════

    @Override
    public void setFlag(String flag) {
        flags.add(flag);
        isDirty = true;
    }

    @Override
    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    @Override
    public void removeFlag(String flag) {
        flags.remove(flag);
        isDirty = true;
    }

    @Override
    public Set<String> getAllFlags() {
        return Collections.unmodifiableSet(flags);
    }

    // ═══════════════════════════════════════════════
    // Variable
    // ═══════════════════════════════════════════════

    @Override
    public int getVariable(String key) {
        return variables.getOrDefault(key, 0);
    }

    @Override
    public void setVariable(String key, int value) {
        variables.put(key, value);
        isDirty = true;
    }

    @Override
    public void incrementVariable(String key, int amount) {
        variables.merge(key, amount, Integer::sum);
        isDirty = true;
    }

    @Override
    public Map<String, Integer> getAllVariables() {
        return Collections.unmodifiableMap(variables);
    }

    // ═══════════════════════════════════════════════
    //  序列化
    // ═══════════════════════════════════════════════

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag root = new CompoundTag();

        // 任务
        ListTag activeList = new ListTag();
        for (QuestRuntimeData data : activeQuests.values()) {
            activeList.add(data.serializeNBT());
        }
        root.put("ActiveQuests", activeList);

        ListTag completedList = new ListTag();
        for (String id : completedQuests) completedList.add(StringTag.valueOf(id));
        root.put("CompletedQuests", completedList);

        ListTag failedList = new ListTag();
        for (String id : failedQuests) failedList.add(StringTag.valueOf(id));
        root.put("FailedQuests", failedList);

        // Flag
        ListTag flagList = new ListTag();
        for (String f : flags) flagList.add(StringTag.valueOf(f));
        root.put("Flags", flagList);

        // Variable
        CompoundTag varsTag = new CompoundTag();
        for (var e : variables.entrySet()) varsTag.putInt(e.getKey(), e.getValue());
        root.put("Variables", varsTag);

        // 对话进度
        root.put("DialogueProgress", dialogueProgress.serialize());

        // 设置版本号
        VERSION_MANAGER.setInitialVersion(root);

        return root;
    }

    @Override
    public void deserializeNBT(CompoundTag root) {
        // 执行版本迁移
        VERSION_MANAGER.migrate(root);
        
        activeQuests.clear();
        completedQuests.clear();
        failedQuests.clear();
        flags.clear();
        variables.clear();

        // 任务
        ListTag activeList = root.getList("ActiveQuests", Tag.TAG_COMPOUND);
        for (int i = 0; i < activeList.size(); i++) {
            QuestRuntimeData data = QuestRuntimeData.deserializeNBT(activeList.getCompound(i));
            activeQuests.put(data.getQuestId(), data);
        }

        ListTag completedList = root.getList("CompletedQuests", Tag.TAG_STRING);
        for (int i = 0; i < completedList.size(); i++) completedQuests.add(completedList.getString(i));

        ListTag failedList = root.getList("FailedQuests", Tag.TAG_STRING);
        for (int i = 0; i < failedList.size(); i++) failedQuests.add(failedList.getString(i));

        ListTag flagList = root.getList("Flags", Tag.TAG_STRING);
        for (int i = 0; i < flagList.size(); i++) flags.add(flagList.getString(i));

        CompoundTag varsTag = root.getCompound("Variables");
        for (String key : varsTag.getAllKeys()) variables.put(key, varsTag.getInt(key));

        // 对话进度 —— 自动检测新旧格式
        if (root.contains("DialogueProgress", Tag.TAG_COMPOUND)) {
            // 新格式：直接反序列化
            dialogueProgress.deserialize(root.getCompound("DialogueProgress"));
        } else if (root.contains("NodeVisitHistory", Tag.TAG_COMPOUND)) {
            // 旧格式：迁移
            dialogueProgress.migrateFromLegacy(root);
        }
    }

    // ═══════════════════════════════════════════════
    //  其他
    // ═══════════════════════════════════════════════

    @Override
    public void copyFrom(IQuestCapability other) {
        this.deserializeNBT(other.serializeNBT());
    }

    @Override
    public void clearAllData() {
        activeQuests.clear();
        completedQuests.clear();
        failedQuests.clear();
        flags.clear();
        variables.clear();
        dialogueProgress.clear();
        isDirty = true;
    }

    public boolean isDirty() {
        if (isDirty) return true;
        if (dialogueProgress.isDirty()) return true;
        for (QuestRuntimeData data : activeQuests.values()) {
            if (data.isDirty()) return true;
        }
        return false;
    }

    public void clearDirty() {
        isDirty = false;
        dialogueProgress.clearDirty();
        for (QuestRuntimeData data : activeQuests.values()) {
            data.clearDirty();
        }
    }
}