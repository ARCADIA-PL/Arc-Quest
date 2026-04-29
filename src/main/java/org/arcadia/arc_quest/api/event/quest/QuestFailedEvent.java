package org.arcadia.arc_quest.api.event.quest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * 任务失败事件。
 * <p>
 * 当任务失败时触发（服务端）。
 * </p>
 *
 * @since 1.0.0
 */
public class QuestFailedEvent extends Event {

    private final ServerPlayer player;
    private final ResourceLocation questId;

    public QuestFailedEvent(ServerPlayer player, ResourceLocation questId) {
        this.player = player;
        this.questId = questId;
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
}
