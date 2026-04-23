package org.com.arc_quest.quest.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.com.arc_quest.dialogue.runtime.DialogueProgressStore;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 玩家任务数据 Capability 接口。
 * <p>
 * <b>v2 变更</b>: 对话历史统一通过 {@link #getDialogueProgress()} 访问。
 * <b>v5 变更</b>: 交易数据统一通过 {@link #getTradeDataStore()} 访问。
 * <b>v6 变更</b>: 删除旧版 8 个交易 default 方法，调用方直接使用 {@link #getTradeDataStore()}。
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

    /**
     * 返回已完成任务的 ResourceLocation 集合。
     */
    default Set<ResourceLocation> getCompletedQuestLocations() {
        return getCompletedQuests().stream()
                .map(ResourceLocation::parse)
                .collect(Collectors.toUnmodifiableSet());
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
     */
    DialogueProgressStore getDialogueProgress();

    /**
     * 获取抽奖数据存储（抽奖次数、保底计数、历史记录、冷却时间戳）。
     */
    GachaDataStore getGachaDataStore();

    /**
     * 获取交易数据存储（购买次数 + 冷却时间戳）。
     */
    TradeDataStore getTradeDataStore();

    // ════════════════════════════════════════
    //  抽奖系统 API
    // ════════════════════════════════════════

    int getGachaDrawCount(String shopId);

    void incrementGachaDrawCount(String shopId);

    void resetGachaDrawCount(String shopId);

    int getGachaPityCounter(String shopId);

    void setGachaPityCounter(String shopId, int count);

    void addGachaDrawHistory(String shopId, String itemId, String rarityName,
                             int actualCount, boolean pityTriggered, long drawTime);

    List<GachaDrawRecord> getGachaDrawHistory(String shopId);

    void clearGachaDrawHistory(String shopId);

    default void clearAllData() {
        List<String> activeIds = new ArrayList<>(getAllActiveQuests().keySet());
        for (String questId : activeIds) {
            removeActiveQuest(questId);
        }
        getDialogueProgress().clear();
    }

    /**
     * 抽奖历史记录（内部记录类）。
     */
    record GachaDrawRecord(
        String itemId,
        String rarityName,
        int actualCount,
        boolean pityTriggered,
        long drawTime
    ) {}
}
