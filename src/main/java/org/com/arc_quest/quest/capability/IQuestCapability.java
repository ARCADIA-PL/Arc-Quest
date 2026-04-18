package org.com.arc_quest.quest.capability;

import net.minecraft.nbt.CompoundTag;
import org.com.arc_quest.dialogue.runtime.DialogueProgressStore;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 玩家任务数据 Capability 接口。
 * <p>
 * <b>v2 变更</b>: 对话历史统一通过 {@link #getDialogueProgress()} 访问，
 * 旧的 {@code recordNodeVisit / hasVisitedNode} 等方法标记为 {@code @Deprecated}。
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

    Set<String> getFailedQuests();

    boolean isQuestActive(String questId);

    boolean isQuestCompleted(String questId);

    boolean isQuestFailed(String questId);

    default List<String> getCompletedQuestIds() {
        return new ArrayList<>(getCompletedQuests());
    }

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

    /**
     * 获取对话进度统一存储。
     * <p>
     * 替代之前散落在接口中的 recordNodeVisit、hasVisitedNode、
     * recordChoiceSelection、getLastDialogueTime 等 20+ 个方法。
     *
     * @return 对话进度存储实例
     */
    DialogueProgressStore getDialogueProgress();

    default void clearAllData() {
        List<String> activeIds = new ArrayList<>(getAllActiveQuests().keySet());
        for (String questId : activeIds) {
            removeActiveQuest(questId);
        }
        getDialogueProgress().clear();
    }
}