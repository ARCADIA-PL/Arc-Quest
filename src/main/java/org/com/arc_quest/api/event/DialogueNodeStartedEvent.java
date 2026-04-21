package org.com.arc_quest.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Event;

/**
 * 对话节点开始事件。
 * <p>
 * 当对话中的某个节点（Say）开始时触发（服务端）。
 * </p>
 *
 * @since 1.0.0
 */
public class DialogueNodeStartedEvent extends Event {

    private final ServerPlayer player;
    private final Entity npc;
    private final String dialogueId;
    private final String nodeId;

    public DialogueNodeStartedEvent(ServerPlayer player, Entity npc, String dialogueId, String nodeId) {
        this.player = player;
        this.npc = npc;
        this.dialogueId = dialogueId;
        this.nodeId = nodeId;
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

    /**
     * 获取节点 ID。
     */
    public String getNodeId() {
        return nodeId;
    }
}
