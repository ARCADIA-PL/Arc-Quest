package org.arcadia.arc_quest.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Event;

/**
 * 对话开始事件。
 * <p>
 * 当玩家开始与 NPC 对话时触发（服务端）。
 * </p>
 *
 * @since 1.0.0
 */
public class DialogueStartedEvent extends Event {

    private final ServerPlayer player;
    private final Entity npc;
    private final String dialogueId;

    public DialogueStartedEvent(ServerPlayer player, Entity npc, String dialogueId) {
        this.player = player;
        this.npc = npc;
        this.dialogueId = dialogueId;
    }

    /**
     * 获取玩家。
     */
    public ServerPlayer getPlayer() {
        return player;
    }

    /**
     * 获取 NPC 实体。
     */
    public Entity getNpc() {
        return npc;
    }

    /**
     * 获取对话树 ID。
     */
    public String getDialogueId() {
        return dialogueId;
    }
}
