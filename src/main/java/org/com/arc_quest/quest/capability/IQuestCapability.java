package org.com.arc_quest.quest.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.com.arc_quest.dialogue.api.CooldownType;
import org.com.arc_quest.dialogue.runtime.DialogueProgressStore;
import org.com.arc_quest.dialogue.runtime.UnifiedCooldownManager;
import org.com.arc_quest.dialogue.util.TimeSanitizer;

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
 * <b>v5 变更</b>: 交易数据统一通过 {@link #getTradeDataStore()} 访问，旧的 8 个交易方法
 * 改为 {@code default} 委托实现（保留向后兼容，不删除）。
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
     * <p>
     * 替代旧有的 8 个分散交易方法，使交易数据完全自治。
     */
    TradeDataStore getTradeDataStore();

    /**
     * @deprecated 使用 {@link #getTradeDataStore()} + {@link #getDialogueProgress()}。
     */
    @Deprecated
    default DialogueProgressStore getTradeCooldownStore() {
        return getDialogueProgress();
    }

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

    // ════════════════════════════════════════
    //  交易数据管理（向后兼容 default 方法）
    // ════════════════════════════════════════

    /**
     * @deprecated 使用 {@code getTradeDataStore().getPurchaseCount(shopId, entryId)}
     */
    @Deprecated
    default int getTradePurchaseCount(String shopId, String entryId) {
        return getTradeDataStore().getPurchaseCount(shopId, entryId);
    }

    /**
     * @deprecated 使用 {@code getTradeDataStore().incrementPurchase(shopId, entryId)}
     */
    @Deprecated
    default void incrementTradePurchase(String shopId, String entryId) {
        getTradeDataStore().incrementPurchase(shopId, entryId);
    }

    /**
     * @deprecated 使用 {@code getTradeDataStore().getCooldown(shopId, entryId).realTime()}
     */
    @Deprecated
    default long getTradeLastPurchaseTime(String shopId, String entryId) {
        return getTradeDataStore().getCooldown(shopId, entryId).realTime();
    }

    /**
     * @deprecated 使用 {@link #recordTradePurchaseTime(String, String, long, long)}
     */
    @Deprecated
    default void recordTradePurchaseTime(String shopId, String entryId) {
        // 无法提供完整三时钟，仅记录真实时间（历史兼容）
        long realTime = TimeSanitizer.getCurrentRealTime();
        getTradeDataStore().recordCooldown(shopId, entryId, realTime, -1L, -1L);
    }

    /**
     * @deprecated 使用 {@code getTradeDataStore().recordCooldown(shopId, entryId, ...)}
     */
    @Deprecated
    default void recordTradePurchaseTime(String shopId, String entryId, long gameTime, long dayTime) {
        long realTime = TimeSanitizer.getCurrentRealTime();
        getTradeDataStore().recordCooldown(shopId, entryId, realTime, gameTime, dayTime);
    }

    /**
     * @deprecated 使用 {@link UnifiedCooldownManager} + {@code getTradeDataStore().getCooldown()}
     */
    @Deprecated
    default boolean isTradeOnCooldown(String shopId, String entryId,
                                      CooldownType cooldownType,
                                      int cooldownValue, int resetTick,
                                      long nowRealTime, long nowGameTime, long nowDayTime) {
        TradeDataStore.TradeCooldownEntry record = getTradeDataStore().getCooldown(shopId, entryId);
        if (!record.exists() || cooldownType == CooldownType.NONE) return false;
        return UnifiedCooldownManager.isOnCooldown(record, cooldownType, cooldownValue, resetTick,
                nowRealTime, nowGameTime, nowDayTime);
    }

    /**
     * @deprecated 使用 {@code getTradeDataStore().resetEntry(shopId, entryId)}
     */
    @Deprecated
    default void resetTradePurchaseCount(String shopId, String entryId) {
        getTradeDataStore().resetEntry(shopId, entryId);
    }

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
