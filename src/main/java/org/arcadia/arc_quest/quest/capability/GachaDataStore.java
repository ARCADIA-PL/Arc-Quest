package org.arcadia.arc_quest.quest.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.arcadia.arc_quest.dialogue.runtime.ICooldownRecord;

import java.util.*;

/**
 * 抽奖系统的玩家数据存储。
 * <p>
 * 从 {@link QuestCapabilityImpl} 拆分，统一管理抽奖次数、保底计数、历史记录和冷却时间戳。
 * <p>
 * 冷却时间戳（{@code drawCooldowns}）从 {@code DialogueProgressStore} 迁移至此，
 * 使抽奖数据完全自治，不再依赖对话进度存储。
 */
public class GachaDataStore {

    private static final int MAX_HISTORY_SIZE = 50;

    private final Map<String, Integer> drawCounts = new HashMap<>();
    private final Map<String, Integer> pityCounters = new HashMap<>();
    private final Map<String, List<IQuestCapability.GachaDrawRecord>> drawHistories = new HashMap<>();
    /** 抽奖冷却时间戳，key=shopId，value=三时钟快照（realTime / gameTime / dayTime）。 */
    private final Map<String, CooldownEntry> drawCooldowns = new HashMap<>();

    // ════════════════════════════════════════
    //  抽奖次数
    // ════════════════════════════════════════

    public int getDrawCount(String shopId) { return drawCounts.getOrDefault(shopId, 0); }
    public void incrementDrawCount(String shopId) { drawCounts.merge(shopId, 1, Integer::sum); }
    public void resetDrawCount(String shopId) { drawCounts.remove(shopId); }

    // ════════════════════════════════════════
    //  保底计数
    // ════════════════════════════════════════

    public int getPityCounter(String shopId) { return pityCounters.getOrDefault(shopId, 0); }
    public void setPityCounter(String shopId, int count) {
        if (count <= 0) {
            pityCounters.remove(shopId);
        } else {
            pityCounters.put(shopId, count);
        }
    }

    // ════════════════════════════════════════
    //  历史记录
    // ════════════════════════════════════════

    public void addDrawHistory(String shopId, IQuestCapability.GachaDrawRecord record) {
        List<IQuestCapability.GachaDrawRecord> history =
                drawHistories.computeIfAbsent(shopId, k -> new ArrayList<>());
        history.add(record);
        if (history.size() > MAX_HISTORY_SIZE) {
            history.remove(0);
        }
    }

    public List<IQuestCapability.GachaDrawRecord> getDrawHistory(String shopId) {
        return Collections.unmodifiableList(
                drawHistories.getOrDefault(shopId, Collections.emptyList()));
    }

    public void clearDrawHistory(String shopId) { drawHistories.remove(shopId); }

    // ════════════════════════════════════════
    //  冷却时间戳（独立于 DialogueProgressStore）
    // ════════════════════════════════════════

    /**
     * 记录抽奖冷却时间戳（三时钟快照）。
     *
     * @param shopId   商店 ID
     * @param realTime 现实时间戳（毫秒）
     * @param gameTime 游戏总刻（单调）
     * @param dayTime  当天刻 [0, 24000]
     */
    public void recordDrawCooldown(String shopId, long realTime, long gameTime, long dayTime) {
        drawCooldowns.put(shopId, new CooldownEntry(realTime, gameTime, dayTime));
    }

    /**
     * 获取抽奖冷却时间戳。未记录时返回 {@link CooldownEntry#EMPTY}。
     */
    public CooldownEntry getDrawCooldown(String shopId) {
        return drawCooldowns.getOrDefault(shopId, CooldownEntry.EMPTY);
    }

    /**
     * 移除抽奖冷却时间戳（用于重置或时间回退清理）。
     */
    public void removeDrawCooldown(String shopId) {
        drawCooldowns.remove(shopId);
    }

    // ════════════════════════════════════════
    //  清空
    // ════════════════════════════════════════

    public void clear() {
        drawCounts.clear();
        pityCounters.clear();
        drawHistories.clear();
        drawCooldowns.clear();
    }

    // ════════════════════════════════════════
    //  序列化（新格式）
    // ════════════════════════════════════════

    public CompoundTag serialize() {
        CompoundTag root = new CompoundTag();

        CompoundTag countsTag = new CompoundTag();
        drawCounts.forEach(countsTag::putInt);
        root.put("DrawCounts", countsTag);

        CompoundTag pityTag = new CompoundTag();
        pityCounters.forEach(pityTag::putInt);
        root.put("PityCounters", pityTag);

        CompoundTag historiesTag = new CompoundTag();
        for (var shopEntry : drawHistories.entrySet()) {
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
            historiesTag.put(shopEntry.getKey(), historyList);
        }
        root.put("Histories", historiesTag);

        CompoundTag cooldownsTag = new CompoundTag();
        for (var entry : drawCooldowns.entrySet()) {
            CompoundTag ct = new CompoundTag();
            ct.putLong("r", entry.getValue().realTime());
            ct.putLong("g", entry.getValue().gameTime());
            ct.putLong("d", entry.getValue().dayTime());
            cooldownsTag.put(entry.getKey(), ct);
        }
        root.put("DrawCooldowns", cooldownsTag);

        return root;
    }

    public void deserialize(CompoundTag root) {
        drawCounts.clear();
        pityCounters.clear();
        drawHistories.clear();
        drawCooldowns.clear();

        CompoundTag countsTag = root.getCompound("DrawCounts");
        for (String shopId : countsTag.getAllKeys()) drawCounts.put(shopId, countsTag.getInt(shopId));

        CompoundTag pityTag = root.getCompound("PityCounters");
        for (String shopId : pityTag.getAllKeys()) pityCounters.put(shopId, pityTag.getInt(shopId));

        CompoundTag historiesTag = root.getCompound("Histories");
        loadHistories(historiesTag);

        if (root.contains("DrawCooldowns", Tag.TAG_COMPOUND)) {
            CompoundTag cooldownsTag = root.getCompound("DrawCooldowns");
            for (String shopId : cooldownsTag.getAllKeys()) {
                CompoundTag ct = cooldownsTag.getCompound(shopId);
                drawCooldowns.put(shopId, new CooldownEntry(
                        ct.getLong("r"), ct.getLong("g"),
                        ct.contains("d") ? ct.getLong("d") : -1L));
            }
        }
    }

    /**
     * 兼容旧存档格式（旧版本将抽奖数据分三个独立 NBT key 存储）。
     */
    public void deserializeLegacy(CompoundTag root) {
        drawCounts.clear();
        CompoundTag countsTag = root.getCompound("GachaDrawCounts");
        for (String shopId : countsTag.getAllKeys()) drawCounts.put(shopId, countsTag.getInt(shopId));

        pityCounters.clear();
        CompoundTag pityTag = root.getCompound("GachaPityCounters");
        for (String shopId : pityTag.getAllKeys()) pityCounters.put(shopId, pityTag.getInt(shopId));

        drawHistories.clear();
        loadHistories(root.getCompound("GachaDrawHistories"));
    }

    private void loadHistories(CompoundTag historiesTag) {
        for (String shopId : historiesTag.getAllKeys()) {
            ListTag historyList = historiesTag.getList(shopId, Tag.TAG_COMPOUND);
            List<IQuestCapability.GachaDrawRecord> history = new ArrayList<>();
            for (int i = 0; i < historyList.size(); i++) {
                CompoundTag tag = historyList.getCompound(i);
                history.add(new IQuestCapability.GachaDrawRecord(
                        tag.getString("itemId"), tag.getString("rarityName"),
                        tag.getInt("actualCount"), tag.getBoolean("pityTriggered"), tag.getLong("drawTime")));
            }
            drawHistories.put(shopId, history);
        }
    }

    // ════════════════════════════════════════
    //  CooldownEntry（三时钟快照，对标 DialogueProgressStore.Entry）
    // ════════════════════════════════════════

    /**
     * 抽奖冷却时间戳快照，包含现实时间、游戏总刻和当天刻三个维度。
     * <p>
     * 对标 {@link org.arcadia.arc_quest.dialogue.runtime.DialogueProgressStore.Entry}，
     * 但独立于对话进度存储，避免跨域耦合。
     */
    public record CooldownEntry(long realTime, long gameTime, long dayTime) implements ICooldownRecord {

        public static final CooldownEntry EMPTY = new CooldownEntry(0L, -1L, -1L);

        /** 是否有有效记录（realTime > 0 表示存在过记录）。 */
        public boolean exists() { return realTime > 0; }
    }
}
