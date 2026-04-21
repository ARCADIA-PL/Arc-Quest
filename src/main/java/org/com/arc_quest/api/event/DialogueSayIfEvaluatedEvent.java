package org.com.arc_quest.api.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Event;

import javax.annotation.Nullable;

/**
 * SayIf 条件评估事件。
 * <p>
 * 当对话节点中的 SayIf 条件分支被评估并选中后触发（服务端）。
 * <b>此事件明确告知附属模组哪个 SayIf 分支被选中</b>。
 * </p>
 * 
 * <h2>使用场景</h2>
 * <ul>
 *   <li>监听特定 SayIf 分支的触发</li>
 *   <li>根据选中的分支执行自定义逻辑</li>
 *   <li>记录玩家看到的剧情分支</li>
 *   <li>动态修改 SayIf 音效或文本</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class DialogueSayIfEvaluatedEvent extends Event {

    private final ServerPlayer player;
    private final Entity npc;
    private final String dialogueId;
    private final String nodeId;
    
    /**
     * 选中的 SayIf 分支索引（从 0 开始）。
     * <p>
     * -1 表示没有 SayIf 分支，使用了默认文本。
     * </p>
     */
    private final int selectedSayIfIndex;
    
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

    public DialogueSayIfEvaluatedEvent(ServerPlayer player, Entity npc, String dialogueId, String nodeId,
                                       int selectedSayIfIndex, String displayText, 
                                       @Nullable SoundEvent saySound, @Nullable ResourceLocation saySoundId) {
        this.player = player;
        this.npc = npc;
        this.dialogueId = dialogueId;
        this.nodeId = nodeId;
        this.selectedSayIfIndex = selectedSayIfIndex;
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
     * 获取选中的 SayIf 分支索引。
     * <p>
     * - 0-based 索引，表示第几个 SayIf 分支被选中
     * - -1 表示没有 SayIf 分支，使用了默认文本
     * </p>
     * 
     * @return SayIf 分支索引，-1 表示无 SayIf
     */
    public int getSelectedSayIfIndex() {
        return selectedSayIfIndex;
    }
    
    /**
     * 检查是否有 SayIf 分支。
     * 
     * @return true 如果有 SayIf 分支且已选中
     */
    public boolean hasSayIfBranch() {
        return selectedSayIfIndex >= 0;
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
