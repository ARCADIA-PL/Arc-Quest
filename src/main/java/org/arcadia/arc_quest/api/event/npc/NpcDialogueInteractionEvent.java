package org.arcadia.arc_quest.api.event.npc;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;

/** NPC 交互匹配到 Arc Quest 对话入口后触发的服务端事件。 */
public final class NpcDialogueInteractionEvent extends Event {
    private final ServerPlayer player;
    private final Entity npc;
    @Nullable private final String dialogueId;
    private final BindingSource bindingSource;
    private boolean cancelled;

    public NpcDialogueInteractionEvent(ServerPlayer player, Entity npc,
                                       @Nullable String dialogueId, BindingSource bindingSource) {
        this.player = player;
        this.npc = npc;
        this.dialogueId = dialogueId;
        this.bindingSource = bindingSource;
    }

    public ServerPlayer getPlayer() { return player; }
    public Entity getNpc() { return npc; }
    @Nullable public String getDialogueId() { return dialogueId; }
    public BindingSource getBindingSource() { return bindingSource; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public enum BindingSource {
        ENTITY_INTERFACE,
        REGISTRY
    }
}
