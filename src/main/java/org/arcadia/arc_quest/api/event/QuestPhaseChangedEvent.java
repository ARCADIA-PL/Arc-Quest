package org.arcadia.arc_quest.api.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * 任务阶段变更事件。
 * <p>
 * 当任务阶段发生变化时触发（服务端）。
 * </p>
 *
 * @since 1.0.0
 */
public class QuestPhaseChangedEvent extends Event {

    private final ServerPlayer player;
    private final ResourceLocation questId;
    private final String oldPhaseId;
    private final String newPhaseId;

    public QuestPhaseChangedEvent(ServerPlayer player, ResourceLocation questId,
                                  String oldPhaseId, String newPhaseId) {
        this.player = player;
        this.questId = questId;
        this.oldPhaseId = oldPhaseId;
        this.newPhaseId = newPhaseId;
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
     * 获取旧阶段 ID。
     */
    public String getOldPhaseId() {
        return oldPhaseId;
    }

    /**
     * 获取新阶段 ID。
     */
    public String getNewPhaseId() {
        return newPhaseId;
    }
}
