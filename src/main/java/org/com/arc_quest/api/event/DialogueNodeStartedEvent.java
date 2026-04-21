package org.com.arc_quest.api.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Event;

import javax.annotation.Nullable;

/**
 * 对话节点开始事件。
 * <p>
 * 当对话中的某个节点（Say）开始时触发（服务端）。
 * <b>包含 SayIf 条件评估后的最终文本、音效和 Say ID</b>。
 * </p>
 *
 * @since 1.0.0
 */
public class DialogueNodeStartedEvent extends Event {

    private final ServerPlayer player;
    private final Entity npc;
    private final String dialogueId;
    private final String nodeId;
    
    /**
     * Say/SayIf 的唯一标识符（带命名空间）。
     * <p>
     * - 如果是普通 say()，则为配置的 sayId
     * - 如果是 sayIf()，则为选中的分支 ID
     * </p>
     */
    private final String sayId;
    
    /**
     * SayIf 评估后的最终显示文本（已进行变量替换）。
     */
    private final String displayText;
    
    /**
     * SayIf 匹配到的音效（可能为 null）。
     */
    @Nullable
    private final SoundEvent saySound;
    
    /**
     * SayIf 匹配到的音效 ID（用于网络传输，可能为 null）。
     */
    @Nullable
    private final ResourceLocation saySoundId;

    public DialogueNodeStartedEvent(ServerPlayer player, Entity npc, String dialogueId, String nodeId,
                                    String sayId, String displayText, 
                                    @Nullable SoundEvent saySound, @Nullable ResourceLocation saySoundId) {
        this.player = player;
        this.npc = npc;
        this.dialogueId = dialogueId;
        this.nodeId = nodeId;
        this.sayId = sayId;
        this.displayText = displayText;
        this.saySound = saySound;
        this.saySoundId = saySoundId;
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
     * 获取 Say/SayIf 的唯一标识符。
     * <p>
     * - 如果是普通 say()，则为配置的 sayId
     * - 如果是 sayIf()，则为选中的分支 ID
     * </p>
     * 
     * @return Say ID（ guaranteed non-null）
     */
    public String getSayId() {
        return sayId;
    }
    
    /**
     * 获取 SayIf 评估后的最终显示文本（已进行变量替换）。
     * <p>
     * 这是实际会显示给玩家的文本，已经过条件分支选择和变量处理。
     * </p>
     */
    public String getDisplayText() {
        return displayText;
    }
    
    /**
     * 获取 SayIf 匹配到的音效。
     * <p>
     * 如果节点没有 SayIf 或没有匹配的音效，返回 null。
     * </p>
     */
    @Nullable
    public SoundEvent getSaySound() {
        return saySound;
    }
    
    /**
     * 获取 SayIf 匹配到的音效 ID（用于网络传输）。
     * <p>
     * 如果节点没有 SayIf 或没有匹配的音效，返回 null。
     * </p>
     */
    @Nullable
    public ResourceLocation getSaySoundId() {
        return saySoundId;
    }
    
    /**
     * 检查是否有 SayIf 音效。
     */
    public boolean hasSaySound() {
        return saySound != null;
    }
}
