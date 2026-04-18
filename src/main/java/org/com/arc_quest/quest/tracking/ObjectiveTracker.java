package org.com.arc_quest.quest.tracking;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.resources.ResourceLocation;
import org.com.arc_quest.quest.api.ObjectiveEntry;
import org.com.arc_quest.quest.api.PhaseDefinition;
import org.com.arc_quest.quest.api.QuestDefinition;
import org.com.arc_quest.quest.registry.QuestRegistry;
import org.slf4j.Logger;

import java.util.*;

/**
 * 核心目标追踪索引。
 * <p>
 * 内部维护 {@code Map<ObjectiveKey, Set<TrackedObjective>>}，
 * 保证任何 Forge 事件只需一次 HashMap.get() 即可找到所有相关目标——O(1) 查找。
 */
public final class ObjectiveTracker {

    /**
     * 单例
     */
    public static final ObjectiveTracker INSTANCE = new ObjectiveTracker();
    private static final Logger LOGGER = LogUtils.getLogger();
    /**
     * 核心索引：ObjectiveKey → 该 key 下所有正在追踪的目标句柄
     * <p>
     * 例如：Key(KILL, minecraft:zombie) → { PlayerA的任务1目标0, PlayerB的任务3目标1, ... }
     */
    private final Object2ObjectOpenHashMap<ObjectiveKey, ObjectOpenHashSet<TrackedObjective>> index = new Object2ObjectOpenHashMap<>();

    /**
     * 反向索引：playerId → 该玩家所有正在追踪的句柄（用于快速移除）
     */
    private final Object2ObjectOpenHashMap<UUID, ObjectOpenHashSet<TrackedObjective>> byPlayer = new Object2ObjectOpenHashMap<>();

    private ObjectiveTracker() {
    }

    // ════════════════════════════════════════
    //  注册 / 注销
    // ════════════════════════════════════════

    /**
     * 当任务变为 ACTIVE 且进入某阶段时，注册该阶段的所有目标。
     *
     * @param playerId 玩家 UUID
     * @param questId  任务 ID
     * @param phaseId  阶段 ID
     */
    public void registerPhase(UUID playerId, ResourceLocation questId, String phaseId) {
        QuestDefinition quest = QuestRegistry.get(questId);
        if (quest == null) {
            LOGGER.warn("[ObjTracker] Unknown quest: {}", questId);
            return;
        }
        PhaseDefinition phase = quest.getPhase(phaseId);
        if (phase == null) {
            LOGGER.warn("[ObjTracker] Unknown phase '{}' in quest {}", phaseId, questId);
            return;
        }

        List<ObjectiveEntry> objectives = phase.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            ObjectiveEntry entry = objectives.get(i);
            ObjectiveKey key = new ObjectiveKey(entry.getType(), entry.getTargetId());

            TrackedObjective tracked = new TrackedObjective(
                    playerId, questId, phaseId, i, key, entry.getRequiredCount());

            this.index.computeIfAbsent(key, k -> new ObjectOpenHashSet<>()).add(tracked);
            this.byPlayer.computeIfAbsent(playerId, k -> new ObjectOpenHashSet<>()).add(tracked);
        }

        LOGGER.debug("[ObjTracker] Registered {} objectives for player {} quest {} phase {}",
                objectives.size(), playerId, questId, phaseId);
    }

    /**
     * 当阶段切换或任务结束时，注销该玩家+任务+阶段的所有目标。
     */
    public void unregisterPhase(UUID playerId, ResourceLocation questId, String phaseId) {
        ObjectOpenHashSet<TrackedObjective> playerSet = this.byPlayer.get(playerId);
        if (playerSet == null) return;

        Iterator<TrackedObjective> it = playerSet.iterator();
        int removed = 0;
        while (it.hasNext()) {
            TrackedObjective tracked = it.next();
            if (tracked.getQuestId().equals(questId) && tracked.getPhaseId().equals(phaseId)) {
                it.remove();
                // 同步从主索引移除
                ObjectOpenHashSet<TrackedObjective> keySet = this.index.get(tracked.getKey());
                if (keySet != null) {
                    keySet.remove(tracked);
                    if (keySet.isEmpty()) {
                        this.index.remove(tracked.getKey());
                    }
                }
                removed++;
            }
        }

        if (playerSet.isEmpty()) {
            this.byPlayer.remove(playerId);
        }

        LOGGER.debug("[ObjTracker] Unregistered {} objectives for player {} quest {} phase {}",
                removed, playerId, questId, phaseId);
    }

    /**
     * 当玩家断开连接时，移除该玩家的所有追踪。
     */
    public void unregisterPlayer(UUID playerId) {
        ObjectOpenHashSet<TrackedObjective> playerSet = this.byPlayer.remove(playerId);
        if (playerSet == null) return;

        for (TrackedObjective tracked : playerSet) {
            ObjectOpenHashSet<TrackedObjective> keySet = this.index.get(tracked.getKey());
            if (keySet != null) {
                keySet.remove(tracked);
                if (keySet.isEmpty()) {
                    this.index.remove(tracked.getKey());
                }
            }
        }

        LOGGER.debug("[ObjTracker] Cleared all tracking for player {}", playerId);
    }

    /**
     * 注销特定任务的所有追踪（用于任务完成/失败/放弃）
     */
    public void unregisterQuest(UUID playerId, String questId) {
        ObjectOpenHashSet<TrackedObjective> playerSet = this.byPlayer.get(playerId);
        if (playerSet == null) return;

        Iterator<TrackedObjective> it = playerSet.iterator();
        int removed = 0;
        while (it.hasNext()) {
            TrackedObjective tracked = it.next();
            if (tracked.getQuestId().toString().equals(questId)) {
                it.remove();
                // 同步从主索引移除
                ObjectOpenHashSet<TrackedObjective> keySet = this.index.get(tracked.getKey());
                if (keySet != null) {
                    keySet.remove(tracked);
                    if (keySet.isEmpty()) {
                        this.index.remove(tracked.getKey());
                    }
                }
                removed++;
            }
        }

        if (playerSet.isEmpty()) {
            this.byPlayer.remove(playerId);
        }

        LOGGER.debug("[ObjTracker] Unregistered {} objectives for quest {} player {}",
                removed, questId, playerId);
    }

    /**
     * 注册单个追踪目标（便捷方法）
     */
    public void register(TrackedObjective tracked) {
        ObjectiveKey key = tracked.getKey();
        this.index.computeIfAbsent(key, k -> new ObjectOpenHashSet<>()).add(tracked);
        this.byPlayer.computeIfAbsent(tracked.getPlayerId(), k -> new ObjectOpenHashSet<>()).add(tracked);
    }

    /**
     * 注销单个追踪目标（便捷方法）
     */
    public void unregister(TrackedObjective tracked) {
        ObjectOpenHashSet<TrackedObjective> keySet = this.index.get(tracked.getKey());
        if (keySet != null) {
            keySet.remove(tracked);
            if (keySet.isEmpty()) {
                this.index.remove(tracked.getKey());
            }
        }

        ObjectOpenHashSet<TrackedObjective> playerSet = this.byPlayer.get(tracked.getPlayerId());
        if (playerSet != null) {
            playerSet.remove(tracked);
            if (playerSet.isEmpty()) {
                this.byPlayer.remove(tracked.getPlayerId());
            }
        }
    }

    // ════════════════════════════════════════
    //  查询（O(1) 核心）
    // ════════════════════════════════════════

    /**
     * 根据事件键查找所有匹配的追踪目标。
     * <p>
     * <b>这是整个系统的性能关键路径。</b>
     * 复杂度：O(1) HashMap lookup + O(K) 遍历匹配结果（K = 匹配数量，通常 ≪ N）。
     *
     * @param key 从 Forge 事件中提取的 ObjectiveKey
     * @return 匹配的追踪目标集合（只读视图），可能为空集
     */
    public Set<TrackedObjective> lookup(ObjectiveKey key) {
        ObjectOpenHashSet<TrackedObjective> result = this.index.get(key);
        return result != null ? Collections.unmodifiableSet(result) : Collections.emptySet();
    }

    /**
     * 按玩家+键联合查询（用于只关心特定玩家的场景）
     */
    public List<TrackedObjective> lookup(UUID playerId, ObjectiveKey key) {
        ObjectOpenHashSet<TrackedObjective> all = this.index.get(key);
        if (all == null || all.isEmpty()) return Collections.emptyList();

        List<TrackedObjective> result = new ArrayList<>(2);  // 通常很少
        for (TrackedObjective tracked : all) {
            if (tracked.getPlayerId().equals(playerId)) {
                result.add(tracked);
            }
        }
        return result;
    }

    // ════════════════════════════════════════
    //  统计（调试）
    // ════════════════════════════════════════

    public int getIndexSize() {
        return this.index.size();
    }

    public int getTrackedCount() {
        int count = 0;
        for (ObjectOpenHashSet<TrackedObjective> set : this.index.values()) {
            count += set.size();
        }
        return count;
    }

    public int getPlayerCount() {
        return this.byPlayer.size();
    }
}