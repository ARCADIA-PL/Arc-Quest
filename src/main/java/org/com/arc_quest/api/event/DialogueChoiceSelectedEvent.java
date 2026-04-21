package org.com.arc_quest.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Event;

/**
 * 对话选项选择事件。
 * <p>
 * 当玩家选择对话选项时触发（服务端）。
 * </p>
 *
 * @since 1.0.0
 */
public class DialogueChoiceSelectedEvent extends Event {

    private final ServerPlayer player;
    private final Entity npc;
    private final String dialogueId;
    private final String nodeId;
    private final int choiceIndex;
    private final String choiceText;

    public DialogueChoiceSelectedEvent(ServerPlayer player, Entity npc, String dialogueId,
                                       String nodeId, int choiceIndex, String choiceText) {
        this.player = player;
        this.npc = npc;
        this.dialogueId = dialogueId;
        this.nodeId = nodeId;
        this.choiceIndex = choiceIndex;
        this.choiceText = choiceText;
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

    /**
     * 获取选项索引。
     */
    public int getChoiceIndex() {
        return choiceIndex;
    }

    /**
     * 获取选项文本。
     */
    public String getChoiceText() {
        return choiceText;
    }
}
