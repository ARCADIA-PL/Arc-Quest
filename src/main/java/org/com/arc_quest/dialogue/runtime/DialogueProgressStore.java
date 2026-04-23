package org.com.arc_quest.dialogue.runtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.dialogue.api.CooldownType;
import org.com.arc_quest.dialogue.util.TimeSanitizer;

import java.util.HashMap;
import java.util.Map;

/**
 * 对话进度统一存储。
 * <p>
 * 内部使用单一 {@code Map<String, Entry>} 存储所有进度数据，并通过
 * {@code keyTypes} 记录每条记录的 {@link ProgressKey.KeyType}，使序列化时能正确分区。
 */
public class DialogueProgressStore {

    private final Map<String, Entry> store = new HashMap<>();
    /** 记录每个 key 对应的语义类型，用于 serialize 分区（写入时同步维护）。 */
    private final Map<String, ProgressKey.KeyType> keyTypes = new HashMap<>();
    private boolean dirty = false;

    // ── Key 构建 ──────────────────────────────────────────

    public static String nodeKey(String namespace, String nodeId) {
        return namespace + ":" + nodeId;
    }

    public static String choiceKey(String namespace, String nodeId, int choiceIndex) {
        return namespace + ":" + nodeId + ":" + choiceIndex;
    }

    public static String dialogueKey(String namespace, String dialogueId) {
        return namespace + ":" + dialogueId;
    }

    // ── 写入（ProgressKey 版本） ──────────────────────────

    public void recordNodeVisit(ProgressKey key, long realTime, long gameTime, long dayTime) {
        String k = key.toKeyString();
        store.put(k, new Entry(realTime, gameTime, dayTime));
        keyTypes.put(k, key.keyType());
        dirty = true;
    }

    public void recordChoiceSelection(ProgressKey key, long realTime, long gameTime, long dayTime) {
        String k = key.toKeyString();
        store.put(k, new Entry(realTime, gameTime, dayTime));
        keyTypes.put(k, key.keyType());
        dirty = true;
    }

    public void recordDialogueVisit(ProgressKey key, long realTime, long gameTime, long dayTime) {
        String k = key.toKeyString();
        store.put(k, new Entry(realTime, gameTime, dayTime));
        keyTypes.put(k, key.keyType());
        dirty = true;
    }

    // ── 写入（String 版本，向后兼容） ────────────────────

    public void recordNodeVisit(String namespace, String nodeId, long realTime, long gameTime, long dayTime) {
        store.put(nodeKey(namespace, nodeId), new Entry(realTime, gameTime, dayTime));
        dirty = true;
    }

    public void recordChoiceSelection(String namespace, String nodeId, int choiceIndex, long realTime, long gameTime, long dayTime) {
        store.put(choiceKey(namespace, nodeId, choiceIndex), new Entry(realTime, gameTime, dayTime));
        dirty = true;
    }

    public void recordDialogueVisit(String namespace, String dialogueId, long realTime, long gameTime, long dayTime) {
        store.put(dialogueKey(namespace, dialogueId), new Entry(realTime, gameTime, dayTime));
        dirty = true;
    }

    // ── 查询（String 版本） ───────────────────────────────

    public Entry getNodeVisit(String namespace, String nodeId) {
        return store.getOrDefault(nodeKey(namespace, nodeId), Entry.EMPTY);
    }

    public boolean hasVisitedNode(String namespace, String nodeId) {
        return getNodeVisit(namespace, nodeId).exists();
    }

    public Entry getChoiceSelection(String namespace, String nodeId, int choiceIndex) {
        return store.getOrDefault(choiceKey(namespace, nodeId, choiceIndex), Entry.EMPTY);
    }

    public Entry getChoiceEntry(String namespace, String nodeId, int choiceIndex) {
        return getChoiceSelection(namespace, nodeId, choiceIndex);
    }

    public boolean hasSelectedChoice(String namespace, String nodeId, int choiceIndex) {
        return getChoiceSelection(namespace, nodeId, choiceIndex).exists();
    }

    public Entry getDialogueVisit(String namespace, String dialogueId) {
        return store.getOrDefault(dialogueKey(namespace, dialogueId), Entry.EMPTY);
    }

    public boolean hasCompletedDialogue(String namespace, String dialogueId) {
        return getDialogueVisit(namespace, dialogueId).exists();
    }

    // ── 查询（ProgressKey 版本） ──────────────────────────

    public Entry getNodeVisit(ProgressKey key) {
        return store.getOrDefault(key.toKeyString(), Entry.EMPTY);
    }

    public boolean hasVisitedNode(ProgressKey key) {
        return getNodeVisit(key).exists();
    }

    public Entry getChoiceSelection(ProgressKey key) {
        return store.getOrDefault(key.toKeyString(), Entry.EMPTY);
    }

    public boolean hasSelectedChoice(ProgressKey key) {
        return getChoiceSelection(key).exists();
    }

    public Entry getDialogueVisit(ProgressKey key) {
        return store.getOrDefault(key.toKeyString(), Entry.EMPTY);
    }

    public boolean hasCompletedDialogue(ProgressKey key) {
        return getDialogueVisit(key).exists();
    }

    /**
     * 统一查询入口（单 Map 后直接按 key 查找）。
     */
    public Entry getEntry(ProgressKey key) {
        return store.getOrDefault(key.toKeyString(), Entry.EMPTY);
    }

    // ── 冷却判断 ──────────────────────────────────────────

    public boolean isOnCooldown(ProgressKey key, CooldownType cooldownType, int cooldownValue, int resetTick, TimeSnapshot ts) {
        return UnifiedCooldownManager.isOnCooldown(this, key, cooldownType, cooldownValue, resetTick, ts);
    }

    public boolean isOnCooldown(Entry entry, CooldownType cooldownType, int cooldownValue, int resetTick, long nowRealTime, long nowGameTime, long nowDayTime) {
        return UnifiedCooldownManager.isOnCooldown(entry, cooldownType, cooldownValue, resetTick, nowRealTime, nowGameTime, nowDayTime);
    }

    public boolean isNodeOnCooldown(String namespace, String nodeId, CooldownType type, int cooldownValue, int resetTick, long nowRealTime, long nowGameTime, long nowDayTime) {
        return isOnCooldown(getNodeVisit(namespace, nodeId), type, cooldownValue, resetTick, nowRealTime, nowGameTime, nowDayTime);
    }

    public boolean isChoiceOnCooldown(String namespace, String nodeId, int choiceIndex, CooldownType type, int cooldownValue, int resetTick, long nowRealTime, long nowGameTime, long nowDayTime) {
        return isOnCooldown(getChoiceSelection(namespace, nodeId, choiceIndex), type, cooldownValue, resetTick, nowRealTime, nowGameTime, nowDayTime);
    }

    public boolean isDialogueOnCooldown(String namespace, String dialogueId, CooldownType type, int cooldownValue, int resetTick, long nowRealTime, long nowGameTime, long nowDayTime) {
        return isOnCooldown(getDialogueVisit(namespace, dialogueId), type, cooldownValue, resetTick, nowRealTime, nowGameTime, nowDayTime);
    }

    public int getGameTickCooldownRemainingTicks(Entry entry, int resetTick, long nowGameTime, long nowDayTime) {
        return UnifiedCooldownManager.getGameTickCooldownRemainingTicks(entry, resetTick, nowGameTime, nowDayTime);
    }

    /**
     * 清除指定 key 的冷却记录（用于时间回退后的清理）。
     */
    public void clearCooldownRecord(ProgressKey key) {
        String k = key.toKeyString();
        store.remove(k);
        keyTypes.remove(k);
        dirty = true;
    }

    /**
     * 移除指定 key 的进度记录（彻底删除，用于重置操作）。
     */
    public void removeRecord(ProgressKey key) {
        String k = key.toKeyString();
        store.remove(k);
        keyTypes.remove(k);
        dirty = true;
    }

    // ── 序列化（分区 NBT 格式，保持旧存档兼容） ──────────

    public CompoundTag serialize() {
        CompoundTag root = new CompoundTag();
        CompoundTag nodesTag     = new CompoundTag();
        CompoundTag choicesTag   = new CompoundTag();
        CompoundTag dialoguesTag = new CompoundTag();
        CompoundTag tradeTag     = new CompoundTag();

        for (var e : store.entrySet()) {
            String k = e.getKey();
            CompoundTag entryTag = e.getValue().toTag();
            ProgressKey.KeyType type = keyTypes.get(k);

            if (type != null) {
                // 有精确类型信息时，按 KeyType 分区
                switch (type) {
                    case NODE     -> nodesTag.put(k, entryTag);
                    case DIALOGUE -> dialoguesTag.put(k, entryTag);
                    case CHOICE   -> choicesTag.put(k, entryTag);
                    case TRADE    -> tradeTag.put(k, entryTag);
                }
            } else {
                // 旧记录（通过 String 版本写入、无 KeyType 信息），退回字符串格式推断
                if (k.startsWith("trade:")) {
                    tradeTag.put(k, entryTag);
                } else {
                    int last = k.lastIndexOf(':');
                    boolean isChoice = last > 0 && isNumeric(k.substring(last + 1));
                    if (isChoice) {
                        choicesTag.put(k, entryTag);
                    } else {
                        nodesTag.put(k, entryTag);
                    }
                }
            }
        }

        root.put("Nodes", nodesTag);
        root.put("Choices", choicesTag);
        root.put("Dialogues", dialoguesTag);
        root.put("Trade", tradeTag);
        return root;
    }

    public void deserialize(CompoundTag root) {
        store.clear();
        keyTypes.clear();
        if (root.contains("Nodes",     Tag.TAG_COMPOUND)) loadMap(root.getCompound("Nodes"),     ProgressKey.KeyType.NODE);
        if (root.contains("Choices",   Tag.TAG_COMPOUND)) loadMap(root.getCompound("Choices"),   ProgressKey.KeyType.CHOICE);
        if (root.contains("Dialogues", Tag.TAG_COMPOUND)) loadMap(root.getCompound("Dialogues"), ProgressKey.KeyType.DIALOGUE);
        if (root.contains("Trade",     Tag.TAG_COMPOUND)) loadMap(root.getCompound("Trade"),     ProgressKey.KeyType.TRADE);
    }

    private void loadMap(CompoundTag tag, ProgressKey.KeyType type) {
        for (String key : tag.getAllKeys()) {
            if (tag.contains(key, Tag.TAG_COMPOUND)) {
                store.put(key, Entry.fromTag(tag.getCompound(key)));
                keyTypes.put(key, type);
            }
        }
    }

    public void migrateFromLegacy(CompoundTag root) {
        migrateLegacyPair(root, "NodeVisitHistory",       "NodeVisitGameTime");
        migrateLegacyPair(root, "ChoiceSelectionHistory", "ChoiceSelectionGameTime");
        migrateLegacyPair(root, "DialogueHistory",        "DialogueGameTime");
        dirty = true;
    }

    private void migrateLegacyPair(CompoundTag root, String realTimeKey, String gameTimeKey) {
        if (!root.contains(realTimeKey, Tag.TAG_COMPOUND)) return;
        CompoundTag rt = root.getCompound(realTimeKey);
        CompoundTag gt = root.contains(gameTimeKey, Tag.TAG_COMPOUND) ? root.getCompound(gameTimeKey) : new CompoundTag();
        for (String key : rt.getAllKeys()) {
            long realTime = rt.getLong(key);
            long gameTime = gt.contains(key) ? gt.getLong(key) : -1L;
            store.put(key, new Entry(realTime, gameTime, -1L));
        }
    }

    private static boolean isNumeric(String s) {
        if (s.isEmpty()) return false;
        for (char c : s.toCharArray()) {
            if (!Character.isDigit(c)) return false;
        }
        return true;
    }

    // ── 脏标记 ───────────────────────────────────────────

    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }

    public void clear() {
        store.clear();
        keyTypes.clear();
        dirty = true;
    }

    public void choiceSelections_legacy_put(String legacyKey, long realTime) {
        store.put(legacyKey, new Entry(realTime, -1L, -1L));
        dirty = true;
    }

    public long choiceSelections_legacy_get(String legacyKey) {
        Entry e = store.get(legacyKey);
        return e != null ? e.realTime() : 0L;
    }

    // ── TimeSnapshot ─────────────────────────────────────

    public record TimeSnapshot(long realTime, long gameTime, long dayTime) {
        public static TimeSnapshot capture(ServerPlayer player) {
            return new TimeSnapshot(
                    TimeSanitizer.getCurrentRealTime(),
                    TimeSanitizer.getCurrentGameTime(player),
                    TimeSanitizer.getCurrentDayTime(player)
            );
        }
    }

    // ── Entry ─────────────────────────────────────────────

    public record Entry(long realTime, long gameTime, long dayTime) implements ICooldownRecord {

        public static final Entry EMPTY = new Entry(0L, -1L, -1L);

        public boolean exists() { return realTime > 0; }

        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("r", realTime);
            tag.putLong("g", gameTime);
            tag.putLong("d", dayTime);
            return tag;
        }

        static Entry fromTag(CompoundTag tag) {
            return new Entry(tag.getLong("r"), tag.getLong("g"),
                    tag.contains("d") ? tag.getLong("d") : -1L);
        }
    }
}
