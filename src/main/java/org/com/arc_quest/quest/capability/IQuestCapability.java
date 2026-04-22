package org.com.arc_quest.quest.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.com.arc_quest.dialogue.api.CooldownType;
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

    /**
     * 返回已完成任务的 ResourceLocation 集合。
     * <p>
     * 消除调用方重复的 {@code getCompletedQuests().stream().map(ResourceLocation::parse).collect(...)} 模板代码。
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
     * <p>
     * 替代之前散落在接口中的 recordNodeVisit、hasVisitedNode、
     * recordChoiceSelection、getLastDialogueTime 等 20+ 个方法。
     *
     * @return 对话进度存储实例
     */
    DialogueProgressStore getDialogueProgress();

    /**
     * 获取交易冷却存储（复用 DialogueProgressStore）。
     * <p>
     * 交易系统使用 ProgressKey.ofTrade() 创建 key，
     * 然后调用 getDialogueProgress().recordChoiceSelection() 等方法。
     *
     * @return 统一的进度存储实例（与 getDialogueProgress() 相同）
     */
    default DialogueProgressStore getTradeCooldownStore() {
        return getDialogueProgress();
    }

    // ════════════════════════════════════════
    //  交易数据管理
    // ════════════════════════════════════════

    /**
     * 获取某商店某商品的购买次数。
     *
     * @param shopId 商店 ID
     * @param entryId 商品 ID
     * @return 购买次数
     */
    int getTradePurchaseCount(String shopId, String entryId);

    /**
     * 增加购买次数。
     *
     * @param shopId 商店 ID
     * @param entryId 商品 ID
     */
    void incrementTradePurchase(String shopId, String entryId);

    /**
     * 获取某抽奖商店的总抽奖次数。
     *
     * @param shopId 商店 ID
     * @return 总抽奖次数
     */
    int getGachaDrawCount(String shopId);
    
    /**
     * 增加抽奖次数。
     *
     * @param shopId 商店 ID
     */
    void incrementGachaDrawCount(String shopId);
    
    /**
     * 重置抽奖次数。
     *
     * @param shopId 商店 ID
     */
    void resetGachaDrawCount(String shopId);

    /**
     * 获取某商店某商品的上次购买时间（毫秒）。
     *
     * @param shopId 商店 ID
     * @param entryId 商品 ID
     * @return 上次购买时间戳，未购买过返回 0
     */
    long getTradeLastPurchaseTime(String shopId, String entryId);

    /**
     * 记录购买时间（旧版本，仅保存现实时间）。
     *
     * @param shopId 商店 ID
     * @param entryId 商品 ID
     * @deprecated 使用 {@link #recordTradePurchaseTime(String, String, long, long)}
     */
    @Deprecated
    void recordTradePurchaseTime(String shopId, String entryId);

    /**
     * 记录购买时间（新版本，保存完整时间快照）。
     *
     * @param shopId    商店 ID
     * @param entryId   商品 ID
     * @param gameTime  游戏总刻数
     * @param dayTime   游戏日内刻数
     */
    void recordTradePurchaseTime(String shopId, String entryId, long gameTime, long dayTime);

    /**
     * 检查交易项是否在冷却中（统一冷却 API）。
     *
     * @param shopId         商店 ID
     * @param entryId        商品 ID
     * @param cooldownType   冷却类型
     * @param cooldownValue  冷却值（秒/tick）
     * @param resetTick      重置刻（仅 GAME_TICK 有效）
     * @param nowRealTime    当前真实时间
     * @param nowGameTime    当前游戏总刻数
     * @param nowDayTime     当前游戏日内刻数
     * @return true = 仍在冷却中
     */
    boolean isTradeOnCooldown(String shopId, String entryId,
                              CooldownType cooldownType,
                              int cooldownValue, int resetTick,
                              long nowRealTime, long nowGameTime, long nowDayTime);

    /**
     * 重置交易项的购买计数。
     *
     * @param shopId  商店 ID
     * @param entryId 商品 ID
     */
    void resetTradePurchaseCount(String shopId, String entryId);

    default void clearAllData() {
        List<String> activeIds = new ArrayList<>(getAllActiveQuests().keySet());
        for (String questId : activeIds) {
            removeActiveQuest(questId);
        }
        getDialogueProgress().clear();
    }
}
