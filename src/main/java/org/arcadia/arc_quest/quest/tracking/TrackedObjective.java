package org.arcadia.arc_quest.quest.tracking;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;

import java.util.Objects;
import java.util.UUID;

/**
 * 运行时追踪句柄——表示"某玩家的某任务的某阶段的某目标"。
 * <p>
 * 当任务变为 ACTIVE 时，其当前阶段的所有 objectives 都注册为 TrackedObjective；
 * 阶段切换或任务结束时移除。
 */
public final class TrackedObjective {

    private final UUID playerId;
    private final ResourceLocation questId;
    private final String phaseId;
    private final int objectiveIndex;
    private final ObjectiveKey key;
    private final int requiredCount;
    private final ObjectiveEntry entry;

    public TrackedObjective(UUID playerId,
                            ResourceLocation questId,
                            String phaseId,
                            int objectiveIndex,
                            ObjectiveKey key,
                            int requiredCount) {
        this.playerId = Objects.requireNonNull(playerId);
        this.questId = Objects.requireNonNull(questId);
        this.phaseId = Objects.requireNonNull(phaseId);
        this.objectiveIndex = objectiveIndex;
        this.key = Objects.requireNonNull(key);
        this.requiredCount = requiredCount;
        entry = null;
    }

    /**
     * 便捷构造器：直接从 ObjectiveEntry 创建
     */
    public TrackedObjective(UUID playerId,
                            ResourceLocation questId,
                            String phaseId,
                            int objectiveIndex,
                            ObjectiveEntry entry) {
        this.playerId = Objects.requireNonNull(playerId);
        this.questId = Objects.requireNonNull(questId);
        this.phaseId = Objects.requireNonNull(phaseId);
        this.objectiveIndex = objectiveIndex;
        this.entry = Objects.requireNonNull(entry);
        key = new ObjectiveKey(entry.getType(), entry.getTargetId());
        requiredCount = entry.getRequiredCount();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public ResourceLocation getQuestId() {
        return questId;
    }

    public String getPhaseId() {
        return phaseId;
    }

    public int getObjectiveIndex() {
        return objectiveIndex;
    }

    public ObjectiveKey getKey() {
        return key;
    }

    public int getRequiredCount() {
        return requiredCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrackedObjective t)) return false;
        return objectiveIndex == t.objectiveIndex
                && playerId.equals(t.playerId)
                && questId.equals(t.questId)
                && phaseId.equals(t.phaseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, questId, phaseId, objectiveIndex);
    }

    @Override
    public String toString() {
        return "Tracked[" + playerId.toString().substring(0, 8)
                + ":" + questId.getPath()
                + "/" + phaseId
                + "#" + objectiveIndex + "]";
    }
}