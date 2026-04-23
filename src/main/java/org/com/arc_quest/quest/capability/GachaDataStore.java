package org.com.arc_quest.quest.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 抽奖系统的玩家数据存储。
 * <p>
 * 从 {@link QuestCapabilityImpl} 拆分，统一管理抽奖次数、保底计数和历史记录。
 */
public class GachaDataStore {

    private static final int MAX_HISTORY_SIZE = 50;

    private final Map<String, Integer> drawCounts = new HashMap<>();
    private final Map<String, Integer> pityCounters = new HashMap<>();
    private final Map<String, List<IQuestCapability.GachaDrawRecord>> drawHistories = new HashMap<>();

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

    public void clear() {
        drawCounts.clear();
        pityCounters.clear();
        drawHistories.clear();
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

        return root;
    }

    public void deserialize(CompoundTag root) {
        drawCounts.clear();
        pityCounters.clear();
        drawHistories.clear();

        CompoundTag countsTag = root.getCompound("DrawCounts");
        for (String shopId : countsTag.getAllKeys()) drawCounts.put(shopId, countsTag.getInt(shopId));

        CompoundTag pityTag = root.getCompound("PityCounters");
        for (String shopId : pityTag.getAllKeys()) pityCounters.put(shopId, pityTag.getInt(shopId));

        CompoundTag historiesTag = root.getCompound("Histories");
        loadHistories(historiesTag);
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
}
