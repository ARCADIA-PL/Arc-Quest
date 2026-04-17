package org.com.arc_quest.quest.capability;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 玩家任务数据 Capability 接口。
 */
public interface IQuestCapability {


    void addActiveQuest(QuestRuntimeData data);

    void removeActiveQuest(String questId);

    void markCompleted(String questId);

    void markFailed(String questId);

    @Nullable
    QuestRuntimeData getActiveQuest(String questId);

    Map<String, QuestRuntimeData> getAllActiveQuests();

    Set<String> getCompletedQuests();

    default List<String> getCompletedQuestIds() {
        return new ArrayList<>(getCompletedQuests());
    }

    Set<String> getFailedQuests();

    boolean isQuestActive(String questId);

    boolean isQuestCompleted(String questId);

    boolean isQuestFailed(String questId);


    void setFlag(String flag);

    boolean hasFlag(String flag);

    void removeFlag(String flag);

    Set<String> getAllFlags();


    int getVariable(String key);

    void setVariable(String key, int value);

    void incrementVariable(String key, int amount);

    Map<String, Integer> getAllVariables();


    CompoundTag serializeNBT();

    void deserializeNBT(CompoundTag tag);


    void copyFrom(IQuestCapability other);


    // ═══════════════════════════════════════════════════════
    //  对话历史记录（用于冷却和一次性对话）
    // ═══════════════════════════════════════════════════════

    /**
     * 记录对话访问时间。
     *
     * @param dialogueId 对话树ID
     * @param timestamp  时间戳（毫秒）
     */
    void recordDialogueTime(String dialogueId, long timestamp);

    /**
     * 获取上次对话时间。
     *
     * @param dialogueId 对话树ID
     * @return 时间戳，0 = 从未访问
     */
    long getLastDialogueTime(String dialogueId);

    /**
     * 检查是否完成过某对话（用于一次性对话）。
     *
     * @param dialogueId 对话树ID
     * @return true = 已完成过
     */
    boolean hasCompletedDialogue(String dialogueId);

    /**
     * 标记对话为已完成（用于一次性对话）。
     *
     * @param dialogueId 对话树ID
     */
    void markDialogueCompleted(String dialogueId);

    /**
     * 记录节点访问时间。
     *
     * @param nodeId    节点ID
     * @param timestamp 时间戳（毫秒）
     */
    void recordNodeVisit(String nodeId, long timestamp);

    /**
     * 获取上次节点访问时间。
     *
     * @param nodeId 节点ID
     * @return 时间戳，0 = 从未访问
     */
    long getLastNodeVisit(String nodeId);

    /**
     * 检查节点是否已访问（用于一次性节点）。
     *
     * @param nodeId 节点ID
     * @return true = 已访问
     */
    boolean hasVisitedNode(String nodeId);

    /**
     * 记录选项选择时间。
     *
     * @param choiceKey 选项键（nodeId:choiceIndex）
     * @param timestamp 时间戳（毫秒）
     */
    void recordChoiceSelection(String choiceKey, long timestamp);

    /**
     * 获取上次选项选择时间。
     *
     * @param choiceKey 选项键
     * @return 时间戳，0 = 从未选择
     */
    long getLastChoiceSelection(String choiceKey);

    /**
     * 检查选项是否已选择（用于一次性选项）。
     *
     * @param choiceKey 选项键
     * @return true = 已选择
     */
    boolean hasSelectedChoice(String choiceKey);


    default void clearAllData() {
        List<String> activeIds = new ArrayList<>(getAllActiveQuests().keySet());
        for (String questId : activeIds) {
            removeActiveQuest(questId);
        }
    }
}
