package org.arcadia.arc_quest.api.event.quest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * 任务进度变化事件。
 * <p>
 * 当任务目标进度发生变化时触发（服务端）。
 * </p>
 *
 * @since 1.0.0
 */
public class QuestProgressChangedEvent extends Event {

    private final ServerPlayer player;
    private final ResourceLocation questId;
    private final String phaseId;
    private final int objectiveIndex;
    private final int oldProgress;
    private final int newProgress;
    private final int requiredCount;

    public QuestProgressChangedEvent(ServerPlayer player, ResourceLocation questId, String phaseId,
                                     int objectiveIndex, int oldProgress, int newProgress, int requiredCount) {
        this.player = player;
        this.questId = questId;
        this.phaseId = phaseId;
        this.objectiveIndex = objectiveIndex;
        this.oldProgress = oldProgress;
        this.newProgress = newProgress;
        this.requiredCount = requiredCount;
    }

    /**
     * 获取玩家。
     */
    public ServerPlayer getPlayer() {
        return player;
    }

    /**
     * 获取任务 ID。
     */
    public ResourceLocation getQuestId() {
        return questId;
    }

    /**
     * 获取阶段 ID。
     */
    public String getPhaseId() {
        return phaseId;
    }

    /**
     * 获取目标索引。
     */
    public int getObjectiveIndex() {
        return objectiveIndex;
    }

    /**
     * 获取旧进度。
     */
    public int getOldProgress() {
        return oldProgress;
    }

    /**
     * 获取新进度。
     */
    public int getNewProgress() {
        return newProgress;
    }

    /**
     * 获取所需数量。
     */
    public int getRequiredCount() {
        return requiredCount;
    }

    /**
     * 检查目标是否已完成。
     */
    public boolean isObjectiveCompleted() {
        return newProgress >= requiredCount;
    }
}
