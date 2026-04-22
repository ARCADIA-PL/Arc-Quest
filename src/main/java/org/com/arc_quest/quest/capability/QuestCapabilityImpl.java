package org.com.arc_quest.quest.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.com.arc_quest.dialogue.api.CooldownType;
import org.com.arc_quest.dialogue.runtime.DialogueProgressStore;
import org.com.arc_quest.dialogue.runtime.ProgressKey;
import org.com.arc_quest.dialogue.runtime.UnifiedCooldownManager;
import org.com.arc_quest.dialogue.util.TimeSanitizer;
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
        3,
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
            if (tag.contains("NodeVisitHistory", Tag.TAG_COMPOUND)) {
                CompoundTag dialogueProgress = new CompoundTag();
                tag.putInt("_needs_dialogue_migration", 1);
            } else if (!tag.contains("DialogueProgress", Tag.TAG_COMPOUND)) {
                tag.put("DialogueProgress", new CompoundTag());
            }
        });
        
        // v2 → v3改用 _ArcQuestVer
        VERSION_MANAGER.addMigration(2, 3, tag -> {
            if (tag.contains("_version", Tag.TAG_INT)) {
                int oldVersion = tag.getInt("_version");
                tag.putInt("_ArcQuestVer", Math.max(oldVersion, 3));
                tag.remove("_version");
            } else {
                tag.putInt("_ArcQuestVer", 3);
            }
            tag.remove("_needs_dialogue_migration");
        });
    }

    private final Map<String, QuestRuntimeData> activeQuests = new LinkedHashMap<>();
    private final Set<String> completedQuests = new LinkedHashSet<>();
    private final Set<String> failedQuests = new LinkedHashSet<>();
    private final Set<String> flags = new HashSet<>();
    private final Map<String, Integer> variables = new HashMap<>();

    /**
     * 统一对话进度存储（包含节点访问、选项选择、对话访问）。
     *  扩展：也用于存储交易冷却数据
     */
    private final DialogueProgressStore dialogueProgress = new DialogueProgressStore();

    /**
     * 交易数据：shopId -> entryId -> purchaseCount
     */
    private final Map<String, Map<String, Integer>> tradePurchases = new HashMap<>();
    
    /**
     * 抽奖数据：shopId -> drawCount
     */
    private final Map<String, Integer> gachaDrawCounts = new HashMap<>();
    
    /**
     * 抽奖保底计数：shopId -> pityCounter
     */
    private final Map<String, Integer> gachaPityCounters = new HashMap<>();
    
    /**
     * 抽奖历史记录：shopId -> List<GachaDrawRecord>
     */
    private final Map<String, List<IQuestCapability.GachaDrawRecord>> gachaDrawHistories = new HashMap<>();

    private boolean isDirty = false;

    @Override
    public DialogueProgressStore getDialogueProgress() {
        return dialogueProgress;
    }

    // ════════════════════════════════════════
    //  交易数据管理
    // ════════════════════════════════════════

    @Override
    public synchronized int getTradePurchaseCount(String shopId, String entryId) {
        return tradePurchases.computeIfAbsent(shopId, k -> new HashMap<>())
                .getOrDefault(entryId, 0);
    }

    @Override
    public synchronized void incrementTradePurchase(String shopId, String entryId) {
        Map<String, Integer> shopData = tradePurchases.computeIfAbsent(shopId, k -> new HashMap<>());
        shopData.merge(entryId, 1, Integer::sum);
        isDirty = true;
    }
    
    // ════════════════════════════════════════
    //  抽奖系统 API
    // ════════════════════════════════════════
    
    @Override
    public synchronized int getGachaDrawCount(String shopId) {
        return gachaDrawCounts.getOrDefault(shopId, 0);
    }
    
    @Override
    public synchronized void incrementGachaDrawCount(String shopId) {
        gachaDrawCounts.merge(shopId, 1, Integer::sum);
        isDirty = true;
    }
    
    @Override
    public synchronized void resetGachaDrawCount(String shopId) {
        gachaDrawCounts.remove(shopId);
        isDirty = true;
    }
    
    @Override
    public synchronized int getGachaPityCounter(String shopId) {
        return gachaPityCounters.getOrDefault(shopId, 0);
    }
    
    @Override
    public synchronized void setGachaPityCounter(String shopId, int count) {
        if (count <= 0) {
            gachaPityCounters.remove(shopId);
        } else {
            gachaPityCounters.put(shopId, count);
        }
        isDirty = true;
    }
    
    @Override
    public synchronized void addGachaDrawHistory(String shopId, String itemId, String rarityName,
                                                  int actualCount, boolean pityTriggered, long drawTime) {
        List<IQuestCapability.GachaDrawRecord> history =
            gachaDrawHistories.computeIfAbsent(shopId, k -> new ArrayList<>());
        
        history.add(new IQuestCapability.GachaDrawRecord(itemId, rarityName, actualCount, pityTriggered, drawTime));
        
        // 限制历史记录大小（最多50条）
        if (history.size() > 50) {
            history.remove(0);
        }
        
        isDirty = true;
    }
    
    @Override
    public synchronized List<IQuestCapability.GachaDrawRecord> getGachaDrawHistory(String shopId) {
        return Collections.unmodifiableList(
            gachaDrawHistories.getOrDefault(shopId, Collections.emptyList())
        );
    }

    @Override
    public long getTradeLastPurchaseTime(String shopId, String entryId) {
        ProgressKey key = ProgressKey.ofTrade(shopId, entryId);
        DialogueProgressStore.Entry entry = 
                dialogueProgress.getChoiceSelection(key);
        
        LOGGER.debug("[Trade-Cooldown] getTradeLastPurchaseTime: shop={}, entry={}, key={}, exists={}, realTime={}",
                shopId, entryId, key.toKeyString(), entry.exists(), entry.exists() ? entry.realTime() : 0);
        
        return entry.exists() ? entry.realTime() : 0L;
    }

    @Override
    @Deprecated
    public void recordTradePurchaseTime(String shopId, String entryId) {
        LOGGER.warn("[QuestCap] Deprecated method called: recordTradePurchaseTime without gameTime/dayTime");
    }

    @Override
    public synchronized void recordTradePurchaseTime(String shopId, String entryId, long gameTime, long dayTime) {
        ProgressKey key = ProgressKey.ofTrade(shopId, entryId);
        long realTime = TimeSanitizer.getCurrentRealTime();
        dialogueProgress.recordChoiceSelection(key, realTime, gameTime, dayTime);
        isDirty = true;
        
        LOGGER.info("[Trade-Cooldown]  Recorded purchase time: shop={}, entry={}, key={}, realTime={}, gameTime={}, dayTime={}",
                shopId, entryId, key.toKeyString(), realTime, gameTime, dayTime);
    }

    @Override
    public boolean isTradeOnCooldown(String shopId, String entryId,
                                     CooldownType cooldownType,
                                     int cooldownValue, int resetTick,
                                     long nowRealTime, long nowGameTime, long nowDayTime) {
        ProgressKey key = ProgressKey.ofTrade(shopId, entryId);
        DialogueProgressStore.Entry entry = 
                dialogueProgress.getChoiceSelection(key);
        
        LOGGER.debug("[Trade-Cooldown] isTradeOnCooldown: shop={}, entry={}, key={}, type={}, cooldownValue={}, resetTick={}",
                shopId, entryId, key.toKeyString(), cooldownType, cooldownValue, resetTick);
        LOGGER.debug("[Trade-Cooldown] Current time: realTime={}, gameTime={}, dayTime={}",
                nowRealTime, nowGameTime, nowDayTime);
        
        if (!entry.exists()) {
            LOGGER.debug("[Trade-Cooldown]  Entry does not exist → NOT on cooldown");
            return false;
        }
        
        if (cooldownType == CooldownType.NONE) {
            LOGGER.debug("[Trade-Cooldown]  CooldownType is NONE → NOT on cooldown");
            return false;
        }
        
        LOGGER.debug("[Trade-Cooldown] Entry data: realTime={}, gameTime={}, dayTime={}",
                entry.realTime(), entry.gameTime(), entry.dayTime());

        boolean result = UnifiedCooldownManager.isOnCooldown(
                entry, cooldownType, cooldownValue, resetTick,
                nowRealTime, nowGameTime, nowDayTime
        );
        
        LOGGER.info("[Trade-Cooldown] 🎯 Final result: onCooldown={}, entry={}, type={}",
                result, entryId, cooldownType);
        
        return result;
    }

    @Override
    public void resetTradePurchaseCount(String shopId, String entryId) {
        Map<String, Integer> shopData = tradePurchases.get(shopId);
        if (shopData != null) {
            shopData.remove(entryId);
            isDirty = true;
        }
        
        ProgressKey key = ProgressKey.ofTrade(shopId, entryId);
        dialogueProgress.clearCooldownRecord(key);
        
        LOGGER.info("[QuestCap] Reset purchase count and cooldown for shop={}, entry={}", shopId, entryId);
    }

    // ═══════════════════════════════════════════════
    // 任务管理
    // ═══════════════════════════════════════════════

    @Override
    public synchronized void addActiveQuest(QuestRuntimeData data) {
        Objects.requireNonNull(data);
        activeQuests.put(data.getQuestId(), data);
        isDirty = true;
    }

    @Override
    public synchronized void removeActiveQuest(String questId) {
        activeQuests.remove(questId);
        isDirty = true;
    }

    @Override
    public synchronized void markCompleted(String questId) {
        activeQuests.remove(questId);
        completedQuests.add(questId);
        failedQuests.remove(questId);
        isDirty = true;
    }

    @Override
    public synchronized void markFailed(String questId) {
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

        ListTag flagList = new ListTag();
        for (String f : flags) flagList.add(StringTag.valueOf(f));
        root.put("Flags", flagList);

        CompoundTag varsTag = new CompoundTag();
        for (var e : variables.entrySet()) varsTag.putInt(e.getKey(), e.getValue());
        root.put("Variables", varsTag);

        root.put("DialogueProgress", dialogueProgress.serialize());

        CompoundTag tradePurchasesTag = new CompoundTag();
        for (var shopEntry : tradePurchases.entrySet()) {
            CompoundTag shopTag = new CompoundTag();
            for (var entryData : shopEntry.getValue().entrySet()) {
                shopTag.putInt(entryData.getKey(), entryData.getValue());
            }
            tradePurchasesTag.put(shopEntry.getKey(), shopTag);
        }
        root.put("TradePurchases", tradePurchasesTag);
        
        // 序列化抽奖数据
        CompoundTag gachaDrawCountsTag = new CompoundTag();
        for (var entry : gachaDrawCounts.entrySet()) {
            gachaDrawCountsTag.putInt(entry.getKey(), entry.getValue());
        }
        root.put("GachaDrawCounts", gachaDrawCountsTag);
        
        CompoundTag gachaPityCountersTag = new CompoundTag();
        for (var entry : gachaPityCounters.entrySet()) {
            gachaPityCountersTag.putInt(entry.getKey(), entry.getValue());
        }
        root.put("GachaPityCounters", gachaPityCountersTag);
        
        // 序列化抽奖历史记录
        CompoundTag gachaHistoriesTag = new CompoundTag();
        for (var shopEntry : gachaDrawHistories.entrySet()) {
            ListTag historyList = new ListTag();
            for (IQuestCapability.GachaDrawRecord record : shopEntry.getValue()) {
                CompoundTag recordTag = new CompoundTag();
                recordTag.putString("itemId", record.itemId());
                recordTag.putString("rarityName", record.rarityName());
                recordTag.putInt("actualCount", record.actualCount());
                recordTag.putBoolean("pityTriggered", record.pityTriggered());
                recordTag.putLong("drawTime", record.drawTime());
                historyList.add(recordTag);
            }
            gachaHistoriesTag.put(shopEntry.getKey(), historyList);
        }
        root.put("GachaDrawHistories", gachaHistoriesTag);

        VERSION_MANAGER.setInitialVersion(root);

        return root;
    }

    @Override
    public void deserializeNBT(CompoundTag root) {
        VERSION_MANAGER.migrate(root);
        
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
        for (int i = 0; i < completedList.size(); i++) completedQuests.add(completedList.getString(i));

        ListTag failedList = root.getList("FailedQuests", Tag.TAG_STRING);
        for (int i = 0; i < failedList.size(); i++) failedQuests.add(failedList.getString(i));

        ListTag flagList = root.getList("Flags", Tag.TAG_STRING);
        for (int i = 0; i < flagList.size(); i++) flags.add(flagList.getString(i));

        CompoundTag varsTag = root.getCompound("Variables");
        for (String key : varsTag.getAllKeys()) variables.put(key, varsTag.getInt(key));

        if (root.contains("DialogueProgress", Tag.TAG_COMPOUND)) {
            dialogueProgress.deserialize(root.getCompound("DialogueProgress"));
        } else if (root.contains("NodeVisitHistory", Tag.TAG_COMPOUND)) {
            dialogueProgress.migrateFromLegacy(root);
        }

        tradePurchases.clear();
        CompoundTag tradePurchasesTag = root.getCompound("TradePurchases");
        for (String shopId : tradePurchasesTag.getAllKeys()) {
            CompoundTag shopTag = tradePurchasesTag.getCompound(shopId);
            Map<String, Integer> shopData = new HashMap<>();
            for (String entryId : shopTag.getAllKeys()) {
                shopData.put(entryId, shopTag.getInt(entryId));
            }
            tradePurchases.put(shopId, shopData);
        }
        
        // 反序列化抽奖数据
        gachaDrawCounts.clear();
        CompoundTag gachaDrawCountsTag = root.getCompound("GachaDrawCounts");
        for (String shopId : gachaDrawCountsTag.getAllKeys()) {
            gachaDrawCounts.put(shopId, gachaDrawCountsTag.getInt(shopId));
        }
        
        gachaPityCounters.clear();
        CompoundTag gachaPityCountersTag = root.getCompound("GachaPityCounters");
        for (String shopId : gachaPityCountersTag.getAllKeys()) {
            gachaPityCounters.put(shopId, gachaPityCountersTag.getInt(shopId));
        }
        
        // 反序列化抽奖历史记录
        gachaDrawHistories.clear();
        CompoundTag gachaHistoriesTag = root.getCompound("GachaDrawHistories");
        for (String shopId : gachaHistoriesTag.getAllKeys()) {
            ListTag historyList = gachaHistoriesTag.getList(shopId, Tag.TAG_COMPOUND);
            List<IQuestCapability.GachaDrawRecord> history = new ArrayList<>();
            for (int i = 0; i < historyList.size(); i++) {
                CompoundTag recordTag = historyList.getCompound(i);
                IQuestCapability.GachaDrawRecord record = new IQuestCapability.GachaDrawRecord(
                    recordTag.getString("itemId"),
                    recordTag.getString("rarityName"),
                    recordTag.getInt("actualCount"),
                    recordTag.getBoolean("pityTriggered"),
                    recordTag.getLong("drawTime")
                );
                history.add(record);
            }
            gachaDrawHistories.put(shopId, history);
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
        tradePurchases.clear();
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