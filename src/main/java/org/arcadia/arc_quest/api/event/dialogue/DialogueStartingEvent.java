package org.arcadia.arc_quest.api.event.dialogue;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import org.arcadia.arc_quest.dialogue.api.DialogueContext;
import org.arcadia.arc_quest.dialogue.api.DialogueTree;
import org.jetbrains.annotations.Nullable;

/**
 * 对话会话正式创建前触发的服务端事件。
 * <p>监听器可以补充上下文或取消启动；取消不会结束当前对话，也不会申请 NPC 租约。</p>
 */
public final class DialogueStartingEvent extends Event {
    private final ServerPlayer player;
    @Nullable private final Entity npc;
    private final DialogueTree dialogue;
    private final DialogueContext context;
    private boolean cancelled;
    @Nullable private String cancellationReason;

    public DialogueStartingEvent(ServerPlayer player, @Nullable Entity npc,
                                 DialogueTree dialogue, DialogueContext context) {
        this.player = player;
        this.npc = npc;
        this.dialogue = dialogue;
        this.context = context;
    }

    public ServerPlayer getPlayer() { return player; }
    @Nullable public Entity getNpc() { return npc; }
    public DialogueTree getDialogue() { return dialogue; }
    public String getDialogueId() { return dialogue.dialogueId(); }
    public DialogueContext getContext() { return context; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Nullable public String getCancellationReason() { return cancellationReason; }

    public void cancel(@Nullable String reason) {
        cancelled = true;
        cancellationReason = reason;
    }
}
