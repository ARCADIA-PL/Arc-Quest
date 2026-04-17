package org.com.arc_quest.quest.capability;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
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

    default java.util.List<String> getCompletedQuestIds() {
        return new java.util.ArrayList<>(getCompletedQuests());
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


    default void clearAllData() {

        java.util.List<String> activeIds = new java.util.ArrayList<>(getAllActiveQuests().keySet());
        for (String questId : activeIds) {
            removeActiveQuest(questId);
        }
    }
}
