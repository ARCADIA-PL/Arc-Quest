package org.arcadia.arc_quest.quest.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.arcadia.arc_quest.dialogue.runtime.ICooldownRecord;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

import java.util.*;

/**
 * 抽奖系统的玩家数据存储。
 * <p>
 * 统一管理抽奖次数、保底计数、历史记录和冷却时间戳。
 * <p>
 * 冷却时间戳（{@code drawCooldowns}）从 {@code DialogueProgressStore} 迁移至此，
 * 使抽奖数据完全自治，不再依赖对话进度存储。
 */
public class GachaDataStore {

    private static final int MAX_HISTORY_SIZE = 50;

    private final Map<String, Integer> drawCounts = new HashMap<>();
    private final Map<String, Integer> pityCounters = new HashMap<>();
    private final Map<String, List<ArcQuestPlayer.GachaDrawRecord>> drawHistories = new HashMap<>();
    /**
     * 抽奖冷却时间戳，key=shopId，value=三时钟快照（realTime / gameTime / dayTime）。
     */
    private final Map<String, CooldownEntry> drawCooldowns = new HashMap<>();
    private boolean dirty;

    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }

    public void copyFrom(GachaDataStore source) {
        java.util.Objects.requireNonNull(source, "source");
        if (source == this) return;
        drawCounts.clear();
        drawCounts.putAll(source.drawCounts);
        pityCounters.clear();
        pityCounters.putAll(source.pityCounters);
        drawHistories.clear();
        source.drawHistories.forEach((id, history) -> drawHistories.put(id, new ArrayList<>(history)));
        drawCooldowns.clear();
        drawCooldowns.putAll(source.drawCooldowns);
        dirty = source.dirty;
    }

    public int getDrawCount(String shopId) {
        return drawCounts.getOrDefault(shopId, 0);
    }

    public void incrementDrawCount(String shopId) {
        drawCounts.merge(shopId, 1, Integer::sum);
        dirty = true;
    }

    public void resetDrawCount(String shopId) {
        drawCounts.remove(shopId);
        dirty = true;
    }

    public int getPityCounter(String shopId) {
        return pityCounters.getOrDefault(shopId, 0);
    }

    public void setPityCounter(String shopId, int count) {
        if (count <= 0) {
            pityCounters.remove(shopId);
        } else {
            pityCounters.put(shopId, count);
        }
        dirty = true;
    }

    public void addDrawHistory(String shopId, ArcQuestPlayer.GachaDrawRecord record) {
        List<ArcQuestPlayer.GachaDrawRecord> history =
                drawHistories.computeIfAbsent(shopId, k -> new ArrayList<>());
        history.add(record);
        if (history.size() > MAX_HISTORY_SIZE) {
            history.remove(0);
        }
        dirty = true;
    }

    public List<ArcQuestPlayer.GachaDrawRecord> getDrawHistory(String shopId) {
        return Collections.unmodifiableList(
                drawHistories.getOrDefault(shopId, Collections.emptyList()));
    }

    public void clearDrawHistory(String shopId) {
        drawHistories.remove(shopId);
        dirty = true;
    }

    public void recordDrawCooldown(String shopId, long realTime, long gameTime, long dayTime) {
        drawCooldowns.put(shopId, new CooldownEntry(realTime, gameTime, dayTime));
        dirty = true;
    }

    public CooldownEntry getDrawCooldown(String shopId) {
        return drawCooldowns.getOrDefault(shopId, CooldownEntry.EMPTY);
    }

    public void removeDrawCooldown(String shopId) {
        if (drawCooldowns.remove(shopId) != null) {
            dirty = true;
        }
    }

    public void clear() {
        drawCounts.clear();
        pityCounters.clear();
        drawHistories.clear();
        drawCooldowns.clear();
        dirty = true;
    }

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
            for (ArcQuestPlayer.GachaDrawRecord record : shopEntry.getValue()) {
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
        dirty = false;
    }

    public void deserializeLegacy(CompoundTag root) {
        drawCounts.clear();
        CompoundTag countsTag = root.getCompound("GachaDrawCounts");
        for (String shopId : countsTag.getAllKeys()) drawCounts.put(shopId, countsTag.getInt(shopId));

        pityCounters.clear();
        CompoundTag pityTag = root.getCompound("GachaPityCounters");
        for (String shopId : pityTag.getAllKeys()) pityCounters.put(shopId, pityTag.getInt(shopId));

        drawHistories.clear();
        loadHistories(root.getCompound("GachaDrawHistories"));

        drawCooldowns.clear();
        if (root.contains("DialogueProgress", Tag.TAG_COMPOUND)) {
            CompoundTag dlgProgress = root.getCompound("DialogueProgress");
            if (dlgProgress.contains("Gacha", Tag.TAG_COMPOUND)) {
                CompoundTag gachaTag = dlgProgress.getCompound("Gacha");
                for (String shopId : gachaTag.getAllKeys()) {
                    CompoundTag ct = gachaTag.getCompound(shopId);
                    drawCooldowns.put(shopId, new CooldownEntry(
                            ct.getLong("r"), ct.getLong("g"),
                            ct.contains("d") ? ct.getLong("d") : -1L));
                }
            }
        }
        dirty = true;
    }

    private void loadHistories(CompoundTag historiesTag) {
        for (String shopId : historiesTag.getAllKeys()) {
            ListTag historyList = historiesTag.getList(shopId, Tag.TAG_COMPOUND);
            List<ArcQuestPlayer.GachaDrawRecord> history = new ArrayList<>();
            for (int i = 0; i < historyList.size(); i++) {
                CompoundTag recordTag = historyList.getCompound(i);
                history.add(new ArcQuestPlayer.GachaDrawRecord(
                        recordTag.getString("itemId"),
                        recordTag.getString("rarityName"),
                        recordTag.getInt("actualCount"),
                        recordTag.getBoolean("pityTriggered"),
                        recordTag.getLong("drawTime")
                ));
            }
            drawHistories.put(shopId, history);
        }
    }

    public record CooldownEntry(long realTime, long gameTime, long dayTime) implements ICooldownRecord {
        public static final CooldownEntry EMPTY = new CooldownEntry(0L, -1L, -1L);

        @Override
        public boolean exists() {
            return realTime > 0;
        }
    }
}
