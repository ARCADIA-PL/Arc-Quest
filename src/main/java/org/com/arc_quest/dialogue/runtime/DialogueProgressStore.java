package org.com.arc_quest.dialogue.runtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.dialogue.api.CooldownType;

import java.util.HashMap;
import java.util.Map;

/**
 * 对话进度统一存储。
 * <p>
 * <b>v2.2 时间系统修正</b>:
 * <ul>
 *   <li>明确 getDayTime() 返回单调递增的原始值（非 [0,23999] 循环）</li>
 *   <li>GAME_TICK 冷却使用"双时钟守卫"：周期比较 + gameTime安全网 + 倒退检测</li>
 *   <li>正确处理 /time set 导致的 dayTime 倒退</li>
 * </ul>
 *
 * <h3>时间字段语义</h3>
 * <pre>
 * Entry.realTime  = System.currentTimeMillis()   → SECONDS / GAME_DAY 冷却
 * Entry.gameTime  = Level.getGameTime()           → 单调递增（不受/time set影响），双时钟守卫
 * Entry.dayTime   = Level.getDayTime()（原始值）  → GAME_TICK 周期比较（自然递增，/time set可倒退）
 * </pre>
 *
 * <h3>getDayTime() 行为澄清</h3>
 * <pre>
 * getDayTime() 在以下情况下单调递增：
 *   - 自然时间流逝（每tick +1）
 *   - /time add N（增加N）
 * getDayTime() 在以下情况下可能减小：
 *   - /time set X（直接设为X，如果X &lt; 当前值则减小）
 * getDayTime() % 24000 才是日内时间 [0, 23999]
 * </pre>
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

    /**
     * 统一冷却查询（简化的 5 参数版本）。
     * <p>
     * 替代原来的 9 参数方法。TimeSnapshot 封装三个时间值。
     *
     * @param key         ProgressKey（自动定位正确的 Map）
     * @param cooldownType 冷却类型
     * @param cooldownValue 秒数/tick数
     * @param resetTick    重置刻
     * @param ts          时间快照（realTime, gameTime, dayTime）
     */
    public boolean isOnCooldown(ProgressKey key, CooldownType cooldownType,
                                int cooldownValue, int resetTick, TimeSnapshot ts) {
        Entry entry;
        if (key.index() >= 0) {
            entry = choiceSelections.getOrDefault(key.toKeyString(), Entry.EMPTY);
        } else {
            // 根据 key 格式判断是节点还是对话
            entry = nodeVisits.containsKey(key.toKeyString())
                    ? nodeVisits.get(key.toKeyString())
                    : dialogueVisits.getOrDefault(key.toKeyString(), Entry.EMPTY);
        }
        return isOnCooldown(entry, cooldownType, cooldownValue, resetTick,
                ts.realTime(), ts.gameTime(), ts.dayTime());
    }

    // ═══════════════════════════════════════════════
    //  冷却判断
    // ═══════════════════════════════════════════════

    /**
     * 通用冷却检测。
     * <p>
     * GAME_TICK 使用"双时钟守卫"算法：
     * <ol>
     *   <li>gameTime 安全网：物理上已过24000+刻 → 一定过期</li>
     *   <li>dayTime 倒退检测：/time set 导致 dayTime 减小 → 视为过期</li>
     *   <li>周期编号比较：基于 dayTime 原始值的 floorDiv 周期，不同周期 → 过期</li>
     * </ol>
     *
     * @param entry         历史记录
     * @param cooldownType  冷却类型
     * @param cooldownValue 秒数（SECONDS 用）
     * @param resetTick     重置刻（GAME_TICK 用，0~23999）
     * @param nowRealTime   当前 System.currentTimeMillis()
     * @param nowGameTime   当前 Level.getGameTime()
     * @param nowDayTime    当前 Level.getDayTime()（原始累加值）
     * @return true = 仍在冷却中
     */
    public boolean isOnCooldown(Entry entry, CooldownType cooldownType,
                                int cooldownValue, int resetTick,
                                long nowRealTime, long nowGameTime, long nowDayTime) {
        if (!entry.exists()) return false;
        if (cooldownType == CooldownType.NONE) return false;

        return switch (cooldownType) {
            case NONE -> false;

            case SECONDS -> {
                long cooldownMs = cooldownValue * 1000L;
                boolean onCooldown = (nowRealTime - entry.realTime()) < cooldownMs;
                Arc_quest.LOGGER.debug("[Cooldown-SECONDS] elapsed={}ms, cooldown={}ms, onCooldown={}",
                        nowRealTime - entry.realTime(), cooldownMs, onCooldown);
                yield onCooldown;
            }

            case GAME_DAY -> {
                long lastDay = entry.realTime() / (24 * 60 * 60 * 1000L);
                long nowDay = nowRealTime / (24 * 60 * 60 * 1000L);
                boolean onCooldown = lastDay == nowDay;
                Arc_quest.LOGGER.debug("[Cooldown-GAME_DAY] lastDay={}, nowDay={}, onCooldown={}",
                        lastDay, nowDay, onCooldown);
                yield onCooldown;
            }

            case GAME_TICK -> {
                long lastRawDayTime = entry.dayTime();
                long lastGameTime = entry.gameTime();

                // ─── 层1：旧数据兼容 ───
                if (lastRawDayTime < 0 || lastGameTime < 0) {
                    Arc_quest.LOGGER.info(
                            "[Cooldown-GAME_TICK] Legacy entry (dayTime={}, gameTime={}), treating as expired",
                            lastRawDayTime, lastGameTime);
                    yield false;
                }

                // ─── 层2：gameTime 安全网 ───
                // getGameTime() 绝对单调递增，不受任何命令影响
                // 如果物理上已过 24000 刻（= 1个游戏日的真实tick数），
                // 则无论 dayTime 怎么被 /time set 修改，冷却一定已过期
                long gameTimeElapsed = nowGameTime - lastGameTime;
                if (gameTimeElapsed >= 24000) {
                    Arc_quest.LOGGER.info(
                            "[Cooldown-GAME_TICK] gameTime guard: elapsed={} >= 24000 → EXPIRED",
                            gameTimeElapsed);
                    yield false;
                }

                // ─── 层3：dayTime 倒退检测 ───
                // getDayTime() 在自然流逝和 /time add 下单调递增
                // 只有 /time set 会使其减小
                // 如果检测到 dayTime 倒退（且 gameTime 确实前进了），
                // 视为管理员干预，冷却过期并清除记录
                if (nowDayTime < lastRawDayTime && gameTimeElapsed > 0) {
                    Arc_quest.LOGGER.warn(
                            "[Cooldown-GAME_TICK] ⚠️ dayTime regression detected: " +
                                    "lastDayTime={}, nowDayTime={}, gameTimeElapsed={} → EXPIRED " +
                                    "(probable /time set backward, clearing cooldown record)",
                            lastRawDayTime, nowDayTime, gameTimeElapsed);
                    // 注意：这里不自动清除记录，而是让上层决定是否需要清理
                    // 避免频繁/time set导致记录丢失
                    yield false;
                }

                // ─── 层4：周期编号比较（核心算法）───
                // 此时 dayTime 没有倒退，gameTime 不足24000
                // 使用 dayTime 的原始累加值计算周期编号
                // 周期: 以 resetTick 为起点，每 24000 tick 一个周期
                long recordedPeriod = Math.floorDiv(lastRawDayTime - resetTick, 24000);
                long currentPeriod = Math.floorDiv(nowDayTime - resetTick, 24000);
                boolean samePeriod = (recordedPeriod == currentPeriod);

                Arc_quest.LOGGER.info(
                        "[Cooldown-GAME_TICK] lastDayTime={}, nowDayTime={}, resetTick={}, " +
                                "gameTimeElapsed={}, recordedPeriod={}, currentPeriod={}, " +
                                "samePeriod={} → onCooldown={}",
                        lastRawDayTime, nowDayTime, resetTick,
                        gameTimeElapsed, recordedPeriod, currentPeriod,
                        samePeriod, samePeriod);

                yield samePeriod;
            }
        };
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
     *
     * @param nowGameTime 当前 getGameTime()
     * @param nowDayTime  当前 getDayTime() 原始值
     * @return 剩余 tick 数，0 = 已可用
     */
    public int getGameTickCooldownRemainingTicks(Entry entry, int resetTick,
                                                 long nowGameTime, long nowDayTime) {
        if (!entry.exists() || entry.dayTime() < 0 || entry.gameTime() < 0) return 0;

        // 如果双时钟守卫判定为过期，直接返回0
        long gameTimeElapsed = nowGameTime - entry.gameTime();
        if (gameTimeElapsed >= 24000) return 0;
        if (nowDayTime < entry.dayTime() && gameTimeElapsed > 0) return 0;

        long recordedPeriod = Math.floorDiv(entry.dayTime() - resetTick, 24000);
        long currentPeriod = Math.floorDiv(nowDayTime - resetTick, 24000);
        if (recordedPeriod != currentPeriod) return 0;

        // 同周期内，计算距离下一个 resetTick 的 tick 数
        long currentDayTick = ((nowDayTime % 24000) + 24000) % 24000;
        long resetTickNorm = ((long) resetTick % 24000 + 24000) % 24000;

        if (currentDayTick >= resetTickNorm) {
            return (int) (24000 - currentDayTick + resetTickNorm);
        } else {
            return (int) (resetTickNorm - currentDayTick);
        }
    }

    /**
     * 清除指定 key 的冷却记录（用于时间回退后的清理）。
     *
     * @param key ProgressKey
     */
    public void clearCooldownRecord(ProgressKey key) {
        String keyStr = key.toKeyString();
        
        if (key.index() >= 0) {
            // 选项冷却
            choiceSelections.remove(keyStr);
            Arc_quest.LOGGER.warn("[Cooldown-Clear] ✅ Removed choice cooldown: {}", keyStr);
        } else {
            // 节点或对话冷却
            if (nodeVisits.containsKey(keyStr)) {
                nodeVisits.remove(keyStr);
                Arc_quest.LOGGER.warn("[Cooldown-Clear] ✅ Removed node cooldown: {}", keyStr);
            } else if (dialogueVisits.containsKey(keyStr)) {
                dialogueVisits.remove(keyStr);
                Arc_quest.LOGGER.warn("[Cooldown-Clear] ✅ Removed dialogue cooldown: {}", keyStr);
            }
        }
        
        //设置脏标记，确保下次保存时同步到NBT
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
                    System.currentTimeMillis(),
                    player.level().getGameTime(),
                    player.level().getDayTime()
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