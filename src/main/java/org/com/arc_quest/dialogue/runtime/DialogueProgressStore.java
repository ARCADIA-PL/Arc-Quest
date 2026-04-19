package org.com.arc_quest.dialogue.runtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.dialogue.api.CooldownType;
import org.com.arc_quest.dialogue.util.TimeSanitizer;

import java.util.HashMap;
import java.util.Map;

/**
 * 对话进度统一存储。
 */
public class DialogueProgressStore {

    // ═══════════════════════════════════════════════
    //  存储
    // ═══════════════════════════════════════════════

    private final Map<String, Entry> nodeVisits = new HashMap<>();
    private final Map<String, Entry> choiceSelections = new HashMap<>();
    private final Map<String, Entry> dialogueVisits = new HashMap<>();
    private boolean dirty = false;

    // ═══════════════════════════════════════════════
    //  Key 构建
    // ═══════════════════════════════════════════════

    public static String nodeKey(String namespace, String nodeId) {
        return namespace + ":" + nodeId;
    }

    public static String choiceKey(String namespace, String nodeId, int choiceIndex) {
        return namespace + ":" + nodeId + ":" + choiceIndex;
    }

    public static String dialogueKey(String namespace, String dialogueId) {
        return namespace + ":" + dialogueId;
    }

    // ═══════════════════════════════════════════════
    // 写入
    // ═══════════════════════════════════════════════

    /**
     * 使用 ProgressKey 记录节点访问。
     * 替代 recordNodeVisit(String, String, long, long, long)。
     */
    public void recordNodeVisit(ProgressKey key, long realTime, long gameTime, long dayTime) {
        nodeVisits.put(key.toKeyString(), new Entry(realTime, gameTime, dayTime));
        dirty = true;
    }

    /**
     * 使用 ProgressKey 记录选项选择。
     */
    public void recordChoiceSelection(ProgressKey key, long realTime, long gameTime, long dayTime) {
        choiceSelections.put(key.toKeyString(), new Entry(realTime, gameTime, dayTime));
        dirty = true;
    }

    /**
     * 使用 ProgressKey 记录对话访问。
     */
    public void recordDialogueVisit(ProgressKey key, long realTime, long gameTime, long dayTime) {
        dialogueVisits.put(key.toKeyString(), new Entry(realTime, gameTime, dayTime));
        dirty = true;
    }

    // ═══════════════════════════════════════════════
    //  写入
    // ═══════════════════════════════════════════════

    /**
     * 记录节点访问。
     *
     * @param realTime System.currentTimeMillis()
     * @param gameTime Level.getGameTime() — 单调递增，不受 /time set 影响
     * @param dayTime  Level.getDayTime() — 原始累加值，/time set 可使其减小
     */
    public void recordNodeVisit(String namespace, String nodeId,
                                long realTime, long gameTime, long dayTime) {
        nodeVisits.put(nodeKey(namespace, nodeId), new Entry(realTime, gameTime, dayTime));
        dirty = true;
    }

    public void recordChoiceSelection(String namespace, String nodeId, int choiceIndex,
                                      long realTime, long gameTime, long dayTime) {
        choiceSelections.put(choiceKey(namespace, nodeId, choiceIndex),
                new Entry(realTime, gameTime, dayTime));
        dirty = true;
    }

    public void recordDialogueVisit(String namespace, String dialogueId,
                                    long realTime, long gameTime, long dayTime) {
        dialogueVisits.put(dialogueKey(namespace, dialogueId),
                new Entry(realTime, gameTime, dayTime));
        dirty = true;
    }

    // ═══════════════════════════════════════════════
    //  查询
    // ═══════════════════════════════════════════════

    public Entry getNodeVisit(String namespace, String nodeId) {
        return nodeVisits.getOrDefault(nodeKey(namespace, nodeId), Entry.EMPTY);
    }

    public boolean hasVisitedNode(String namespace, String nodeId) {
        return getNodeVisit(namespace, nodeId).exists();
    }

    public Entry getChoiceSelection(String namespace, String nodeId, int choiceIndex) {
        return choiceSelections.getOrDefault(
                choiceKey(namespace, nodeId, choiceIndex), Entry.EMPTY);
    }

    public Entry getChoiceEntry(String namespace, String nodeId, int choiceIndex) {
        return getChoiceSelection(namespace, nodeId, choiceIndex);
    }

    public boolean hasSelectedChoice(String namespace, String nodeId, int choiceIndex) {
        return getChoiceSelection(namespace, nodeId, choiceIndex).exists();
    }

    public Entry getDialogueVisit(String namespace, String dialogueId) {
        return dialogueVisits.getOrDefault(dialogueKey(namespace, dialogueId), Entry.EMPTY);
    }

    public boolean hasCompletedDialogue(String namespace, String dialogueId) {
        return getDialogueVisit(namespace, dialogueId).exists();
    }

    // ═══════════════════════════════════════════════
    // 查询
    // ═══════════════════════════════════════════════

    /**
     * 使用 ProgressKey 查询节点访问。
     */
    public Entry getNodeVisit(ProgressKey key) {
        return nodeVisits.getOrDefault(key.toKeyString(), Entry.EMPTY);
    }

    public boolean hasVisitedNode(ProgressKey key) {
        return getNodeVisit(key).exists();
    }

    public Entry getChoiceSelection(ProgressKey key) {
        return choiceSelections.getOrDefault(key.toKeyString(), Entry.EMPTY);
    }

    public boolean hasSelectedChoice(ProgressKey key) {
        return getChoiceSelection(key).exists();
    }

    public Entry getDialogueVisit(ProgressKey key) {
        return dialogueVisits.getOrDefault(key.toKeyString(), Entry.EMPTY);
    }

    public boolean hasCompletedDialogue(ProgressKey key) {
        return getDialogueVisit(key).exists();
    }

    // ═══════════════════════════════════════════════
    //  冷却判断（委托给 DialogueCooldownManager）
    // ═══════════════════════════════════════════════

    /**
     * 统一冷却查询（简化的 5 参数版本）。
     *
     * @param key         ProgressKey（自动定位正确的 Map）
     * @param cooldownType 冷却类型
     * @param cooldownValue 秒数/tick数
     * @param resetTick    重置刻
     * @param ts          时间快照（realTime, gameTime, dayTime）
     */
    public boolean isOnCooldown(ProgressKey key, CooldownType cooldownType,
                                int cooldownValue, int resetTick, TimeSnapshot ts) {
        return UnifiedCooldownManager.isOnCooldown(this, key, cooldownType, cooldownValue, resetTick, ts);
    }

    /**
     * 通用冷却检测。
     */
    public boolean isOnCooldown(Entry entry, CooldownType cooldownType,
                                int cooldownValue, int resetTick,
                                long nowRealTime, long nowGameTime, long nowDayTime) {
        return UnifiedCooldownManager.isOnCooldown(entry, cooldownType, cooldownValue, resetTick,
                nowRealTime, nowGameTime, nowDayTime);
    }

    /**
     * 节点冷却查询。
     */
    public boolean isNodeOnCooldown(String namespace, String nodeId,
                                    CooldownType type, int cooldownValue, int resetTick,
                                    long nowRealTime, long nowGameTime, long nowDayTime) {
        Entry entry = getNodeVisit(namespace, nodeId);
        return isOnCooldown(entry, type, cooldownValue, resetTick,
                nowRealTime, nowGameTime, nowDayTime);
    }

    /**
     * 选项冷却查询。
     */
    public boolean isChoiceOnCooldown(String namespace, String nodeId, int choiceIndex,
                                      CooldownType type, int cooldownValue, int resetTick,
                                      long nowRealTime, long nowGameTime, long nowDayTime) {
        Entry entry = getChoiceSelection(namespace, nodeId, choiceIndex);
        return isOnCooldown(entry, type, cooldownValue, resetTick,
                nowRealTime, nowGameTime, nowDayTime);
    }

    /**
     * 对话树冷却查询。
     */
    public boolean isDialogueOnCooldown(String namespace, String dialogueId,
                                        CooldownType type, int cooldownValue, int resetTick,
                                        long nowRealTime, long nowGameTime, long nowDayTime) {
        Entry entry = getDialogueVisit(namespace, dialogueId);
        return isOnCooldown(entry, type, cooldownValue, resetTick,
                nowRealTime, nowGameTime, nowDayTime);
    }

    /**
     * 计算 GAME_TICK 冷却剩余时间（tick 数，用于客户端显示）。
     */
    public int getGameTickCooldownRemainingTicks(Entry entry, int resetTick,
                                                 long nowGameTime, long nowDayTime) {
        return UnifiedCooldownManager.getGameTickCooldownRemainingTicks(entry, resetTick, nowGameTime, nowDayTime);
    }

    /**
     * 清除指定 key 的冷却记录（用于时间回退后的清理）。
     *
     * @param key ProgressKey
     */
    public void clearCooldownRecord(ProgressKey key) {
        String keyStr = key.toKeyString();
        
        if (keyStr.startsWith("trade:")) {
            choiceSelections.remove(keyStr);
            Arc_quest.LOGGER.info("[Cooldown-Clear]  Removed trade cooldown: {}", keyStr);
        } else if (key.index() >= 0) {
            choiceSelections.remove(keyStr);
            Arc_quest.LOGGER.warn("[Cooldown-Clear]  Removed choice cooldown: {}", keyStr);
        } else {
            if (nodeVisits.containsKey(keyStr)) {
                nodeVisits.remove(keyStr);
                Arc_quest.LOGGER.warn("[Cooldown-Clear]  Removed node cooldown: {}", keyStr);
            } else if (dialogueVisits.containsKey(keyStr)) {
                dialogueVisits.remove(keyStr);
                Arc_quest.LOGGER.warn("[Cooldown-Clear]  Removed dialogue cooldown: {}", keyStr);
            }
        }
        
        dirty = true;
        Arc_quest.LOGGER.debug("[Cooldown-Clear] Dirty flag set, will save on next tick");
    }

    // ═══════════════════════════════════════════════
    //  序列化 / 反序列化
    // ═══════════════════════════════════════════════

    public CompoundTag serialize() {
        CompoundTag root = new CompoundTag();
        root.put("Nodes", serializeMap(nodeVisits));
        root.put("Choices", serializeMap(choiceSelections));
        root.put("Dialogues", serializeMap(dialogueVisits));
        return root;
    }

    public void deserialize(CompoundTag root) {
        nodeVisits.clear();
        choiceSelections.clear();
        dialogueVisits.clear();

        if (root.contains("Nodes", Tag.TAG_COMPOUND)) {
            deserializeMap(root.getCompound("Nodes"), nodeVisits);
        }
        if (root.contains("Choices", Tag.TAG_COMPOUND)) {
            deserializeMap(root.getCompound("Choices"), choiceSelections);
        }
        if (root.contains("Dialogues", Tag.TAG_COMPOUND)) {
            deserializeMap(root.getCompound("Dialogues"), dialogueVisits);
        }
    }

    public void migrateFromLegacy(CompoundTag root) {
        migrateLegacyPair(root, "NodeVisitHistory", "NodeVisitGameTime", nodeVisits);
        migrateLegacyPair(root, "ChoiceSelectionHistory", "ChoiceSelectionGameTime", choiceSelections);
        migrateLegacyPair(root, "DialogueHistory", "DialogueGameTime", dialogueVisits);
        dirty = true;
    }

    private void migrateLegacyPair(CompoundTag root,
                                   String realTimeKey, String gameTimeKey,
                                   Map<String, Entry> target) {
        if (!root.contains(realTimeKey, Tag.TAG_COMPOUND)) return;
        CompoundTag rt = root.getCompound(realTimeKey);
        CompoundTag gt = root.contains(gameTimeKey, Tag.TAG_COMPOUND)
                ? root.getCompound(gameTimeKey) : new CompoundTag();
        for (String key : rt.getAllKeys()) {
            long realTime = rt.getLong(key);
            long gameTime = gt.contains(key) ? gt.getLong(key) : -1L;
            target.put(key, new Entry(realTime, gameTime, -1L));
        }
    }

    private static CompoundTag serializeMap(Map<String, Entry> map) {
        CompoundTag tag = new CompoundTag();
        for (var e : map.entrySet()) {
            tag.put(e.getKey(), e.getValue().toTag());
        }
        return tag;
    }

    private static void deserializeMap(CompoundTag tag, Map<String, Entry> target) {
        for (String key : tag.getAllKeys()) {
            if (tag.contains(key, Tag.TAG_COMPOUND)) {
                target.put(key, Entry.fromTag(tag.getCompound(key)));
            }
        }
    }

    // ═══════════════════════════════════════════════
    //  脏标记 / 清理
    // ═══════════════════════════════════════════════

    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }

    public void clear() {
        nodeVisits.clear();
        choiceSelections.clear();
        dialogueVisits.clear();
        dirty = true;
    }

    public void choiceSelections_legacy_put(String legacyKey, long realTime) {
        choiceSelections.put(legacyKey, new Entry(realTime, -1L, -1L));
        dirty = true;
    }

    public long choiceSelections_legacy_get(String legacyKey) {
        Entry e = choiceSelections.get(legacyKey);
        return e != null ? e.realTime() : 0L;
    }

    /**
     * 三时钟快照。
     * <p>
     * 从 DialogueSession 的 private record 提升为 public，
     * 供 DialogueProgressStore 和 DialogueEvalContext 共用。
     */
    public record TimeSnapshot(long realTime, long gameTime, long dayTime) {
        /**
         * 从 ServerPlayer 一次性采样。
         */
        public static TimeSnapshot capture(ServerPlayer player) {
            return new TimeSnapshot(
                    TimeSanitizer.getCurrentRealTime(),
                    TimeSanitizer.getCurrentGameTime(player),
                    TimeSanitizer.getCurrentDayTime(player)
            );
        }
    }

    // ═══════════════════════════════════════════════
    //  Entry record
    // ═══════════════════════════════════════════════

    /**
     * 单条进度记录。
     *
     * @param realTime System.currentTimeMillis()
     * @param gameTime Level.getGameTime()（绝对单调递增，不受任何命令影响）
     * @param dayTime  Level.getDayTime()（原始累加值，自然递增，/time set 可使其减小）
     */
    public record Entry(long realTime, long gameTime, long dayTime) {

        public static final Entry EMPTY = new Entry(0L, -1L, -1L);

        public boolean exists() {
            return realTime > 0;
        }

        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("r", realTime);
            tag.putLong("g", gameTime);
            tag.putLong("d", dayTime);
            return tag;
        }

        static Entry fromTag(CompoundTag tag) {
            return new Entry(
                    tag.getLong("r"),
                    tag.getLong("g"),
                    tag.contains("d") ? tag.getLong("d") : -1L
            );
        }
    }
}