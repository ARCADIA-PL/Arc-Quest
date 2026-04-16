package org.com.arc_quest.quest.tracking;

import net.minecraft.resources.ResourceLocation;
import org.com.arc_quest.quest.api.ObjectiveEntry;

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
        this.entry = null;
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
        this.key = new ObjectiveKey(entry.getType(), entry.getTargetId());
        this.requiredCount = entry.getRequiredCount();
    }

    public UUID getPlayerId() {
        return this.playerId;
    }

    public ResourceLocation getQuestId() {
        return this.questId;
    }

    public String getPhaseId() {
        return this.phaseId;
    }

    public int getObjectiveIndex() {
        return this.objectiveIndex;
    }

    public ObjectiveKey getKey() {
        return this.key;
    }

    public int getRequiredCount() {
        return this.requiredCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrackedObjective t)) return false;
        return this.objectiveIndex == t.objectiveIndex
                && this.playerId.equals(t.playerId)
                && this.questId.equals(t.questId)
                && this.phaseId.equals(t.phaseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.playerId, this.questId, this.phaseId, this.objectiveIndex);
    }

    @Override
    public String toString() {
        return "Tracked[" + this.playerId.toString().substring(0, 8)
                + ":" + this.questId.getPath()
                + "/" + this.phaseId
                + "#" + this.objectiveIndex + "]";
    }
}