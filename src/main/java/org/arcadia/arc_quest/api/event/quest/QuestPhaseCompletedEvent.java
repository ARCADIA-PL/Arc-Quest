package org.arcadia.arc_quest.api.event.quest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * 任务阶段完成事件。
 * <p>
 * 当任务的某个阶段完成时触发（服务端）。
 * </p>
 *
 * @since 1.0.0
 */
public class QuestPhaseCompletedEvent extends Event {

    private final ServerPlayer player;
    private final ResourceLocation questId;
    private final String phaseId;

    public QuestPhaseCompletedEvent(ServerPlayer player, ResourceLocation questId, String phaseId) {
        this.player = player;
        this.questId = questId;
        this.phaseId = phaseId;
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
     * 获取完成的阶段 ID。
     */
    public String getPhaseId() {
        return phaseId;
    }
}
