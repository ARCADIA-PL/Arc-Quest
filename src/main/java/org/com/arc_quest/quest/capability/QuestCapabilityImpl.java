package org.com.arc_quest.quest.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.*;

/**
 * IQuestCapability 的标准实现。
 */
public class QuestCapabilityImpl implements IQuestCapability {


    private final Map<String, QuestRuntimeData> activeQuests = new LinkedHashMap<>();


    private final Set<String> completedQuests = new LinkedHashSet<>();


    private final Set<String> failedQuests = new LinkedHashSet<>();


    private final Set<String> flags = new HashSet<>();


    private final Map<String, Integer> variables = new HashMap<>();
    
    // P2优化：脏标记，用于延迟保存
    private boolean isDirty = false;
    
    // [新增] 对话历史记录
    private final Map<String, Long> dialogueHistory = new HashMap<>();      // dialogueId -> timestamp
    private final Map<String, Long> nodeVisitHistory = new HashMap<>();     // nodeId -> timestamp
    private final Map<String, Long> choiceSelectionHistory = new HashMap<>(); // choiceKey -> timestamp



    @Override
    public void addActiveQuest(QuestRuntimeData data) {
        Objects.requireNonNull(data);
        activeQuests.put(data.getQuestId(), data);
        this.isDirty = true; // 标记为脏
    }

    @Override
    public void removeActiveQuest(String questId) {
        activeQuests.remove(questId);
        this.isDirty = true; // 标记为脏
    }

    @Override
    public void markCompleted(String questId) {
        activeQuests.remove(questId);
        completedQuests.add(questId);
        failedQuests.remove(questId); // 安全起见
        this.isDirty = true; // 标记为脏
    }

    @Override
    public void markFailed(String questId) {
        activeQuests.remove(questId);
        failedQuests.add(questId);
        this.isDirty = true; // 标记为脏
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



    @Override
    public void setFlag(String flag) {
        flags.add(flag);
        this.isDirty = true; // 标记为脏
    }

    @Override
    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    @Override
    public void removeFlag(String flag) {
        flags.remove(flag);
        this.isDirty = true; // 标记为脏
    }

    @Override
    public Set<String> getAllFlags() {
        return Collections.unmodifiableSet(flags);
    }



    @Override
    public int getVariable(String key) {
        return variables.getOrDefault(key, 0);
    }

    @Override
    public void setVariable(String key, int value) {
        variables.put(key, value);
        this.isDirty = true; // 标记为脏
    }

    @Override
    public void incrementVariable(String key, int amount) {
        variables.merge(key, amount, Integer::sum);
        this.isDirty = true; // 标记为脏
    }

    @Override
    public Map<String, Integer> getAllVariables() {
        return Collections.unmodifiableMap(variables);
    }



    @Override
    public CompoundTag serializeNBT() {
        CompoundTag root = new CompoundTag();

        ListTag activeList = new ListTag();
        for (QuestRuntimeData data : activeQuests.values()) {
            activeList.add(data.serializeNBT());
        }
        root.put("ActiveQuests", activeList);

        ListTag completedList = new ListTag();
        for (String id : completedQuests) {
            completedList.add(StringTag.valueOf(id));
        }
        root.put("CompletedQuests", completedList);

        ListTag failedList = new ListTag();
        for (String id : failedQuests) {
            failedList.add(StringTag.valueOf(id));
        }
        root.put("FailedQuests", failedList);

        ListTag flagList = new ListTag();
        for (String f : flags) {
            flagList.add(StringTag.valueOf(f));
        }
        root.put("Flags", flagList);

        CompoundTag varsTag = new CompoundTag();
        for (Map.Entry<String, Integer> e : variables.entrySet()) {
            varsTag.putInt(e.getKey(), e.getValue());
        }
        root.put("Variables", varsTag);

        // [新增] 序列化对话历史
        CompoundTag dialogueHistoryTag = new CompoundTag();
        for (Map.Entry<String, Long> e : dialogueHistory.entrySet()) {
            dialogueHistoryTag.putLong(e.getKey(), e.getValue());
        }
        root.put("DialogueHistory", dialogueHistoryTag);

        CompoundTag nodeVisitHistoryTag = new CompoundTag();
        for (Map.Entry<String, Long> e : nodeVisitHistory.entrySet()) {
            nodeVisitHistoryTag.putLong(e.getKey(), e.getValue());
        }
        root.put("NodeVisitHistory", nodeVisitHistoryTag);

        CompoundTag choiceSelectionHistoryTag = new CompoundTag();
        for (Map.Entry<String, Long> e : choiceSelectionHistory.entrySet()) {
            choiceSelectionHistoryTag.putLong(e.getKey(), e.getValue());
        }
        root.put("ChoiceSelectionHistory", choiceSelectionHistoryTag);

        return root;
    }

    @Override
    public void deserializeNBT(CompoundTag root) {
        activeQuests.clear();
        completedQuests.clear();
        failedQuests.clear();
        flags.clear();
        variables.clear();

        ListTag activeList = root.getList("ActiveQuests", Tag.TAG_COMPOUND);
        for (int i = 0; i < activeList.size(); i++) {
            QuestRuntimeData data = QuestRuntimeData.deserializeNBT(activeList.getCompound(i));
            activeQuests.put(data.getQuestId(), data);
        }

        ListTag completedList = root.getList("CompletedQuests", Tag.TAG_STRING);
        for (int i = 0; i < completedList.size(); i++) {
            completedQuests.add(completedList.getString(i));
        }

        ListTag failedList = root.getList("FailedQuests", Tag.TAG_STRING);
        for (int i = 0; i < failedList.size(); i++) {
            failedQuests.add(failedList.getString(i));
        }

        ListTag flagList = root.getList("Flags", Tag.TAG_STRING);
        for (int i = 0; i < flagList.size(); i++) {
            flags.add(flagList.getString(i));
        }

        CompoundTag varsTag = root.getCompound("Variables");
        for (String key : varsTag.getAllKeys()) {
            variables.put(key, varsTag.getInt(key));
        }

        // [新增] 反序列化对话历史
        if (root.contains("DialogueHistory", Tag.TAG_COMPOUND)) {
            CompoundTag dialogueHistoryTag = root.getCompound("DialogueHistory");
            for (String key : dialogueHistoryTag.getAllKeys()) {
                dialogueHistory.put(key, dialogueHistoryTag.getLong(key));
            }
        }

        if (root.contains("NodeVisitHistory", Tag.TAG_COMPOUND)) {
            CompoundTag nodeVisitHistoryTag = root.getCompound("NodeVisitHistory");
            for (String key : nodeVisitHistoryTag.getAllKeys()) {
                nodeVisitHistory.put(key, nodeVisitHistoryTag.getLong(key));
            }
        }

        if (root.contains("ChoiceSelectionHistory", Tag.TAG_COMPOUND)) {
            CompoundTag choiceSelectionHistoryTag = root.getCompound("ChoiceSelectionHistory");
            for (String key : choiceSelectionHistoryTag.getAllKeys()) {
                choiceSelectionHistory.put(key, choiceSelectionHistoryTag.getLong(key));
            }
        }
    }



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
        dialogueHistory.clear();
        nodeVisitHistory.clear();
        choiceSelectionHistory.clear();
        this.isDirty = true; // 标记为脏
    }
    

    

    public boolean isDirty() {
        if (this.isDirty) return true;
        

        for (QuestRuntimeData data : activeQuests.values()) {
            if (data.isDirty()) return true;
        }
        
        return false;
    }
    

    public void clearDirty() {
        this.isDirty = false;
        for (QuestRuntimeData data : activeQuests.values()) {
            data.clearDirty();
        }
    }
    
    // ═══════════════════════════════════════════════════════
    //  对话历史记录实现
    // ═══════════════════════════════════════════════════════

    @Override
    public void recordDialogueTime(String dialogueId, long timestamp) {
        dialogueHistory.put(dialogueId, timestamp);
        this.isDirty = true;
    }

    @Override
    public long getLastDialogueTime(String dialogueId) {
        return dialogueHistory.getOrDefault(dialogueId, 0L);
    }

    @Override
    public boolean hasCompletedDialogue(String dialogueId) {
        return dialogueHistory.containsKey(dialogueId);
    }

    @Override
    public void markDialogueCompleted(String dialogueId) {
        dialogueHistory.put(dialogueId, System.currentTimeMillis());
        this.isDirty = true;
    }

    @Override
    public void recordNodeVisit(String nodeId, long timestamp) {
        nodeVisitHistory.put(nodeId, timestamp);
        this.isDirty = true;
    }

    @Override
    public long getLastNodeVisit(String nodeId) {
        return nodeVisitHistory.getOrDefault(nodeId, 0L);
    }

    @Override
    public boolean hasVisitedNode(String nodeId) {
        return nodeVisitHistory.containsKey(nodeId);
    }

    @Override
    public void recordChoiceSelection(String choiceKey, long timestamp) {
        choiceSelectionHistory.put(choiceKey, timestamp);
        this.isDirty = true;
    }

    @Override
    public long getLastChoiceSelection(String choiceKey) {
        return choiceSelectionHistory.getOrDefault(choiceKey, 0L);
    }

    @Override
    public boolean hasSelectedChoice(String choiceKey) {
        return choiceSelectionHistory.containsKey(choiceKey);
    }
}