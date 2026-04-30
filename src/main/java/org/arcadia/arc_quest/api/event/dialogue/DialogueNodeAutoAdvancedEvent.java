package org.arcadia.arc_quest.api.event.dialogue;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

public class DialogueNodeAutoAdvancedEvent extends Event {
    private final ServerPlayer player;
    private final String dialogueId;
    private final String fromNodeId;
    private final String toNodeId;

    public DialogueNodeAutoAdvancedEvent(ServerPlayer player, String dialogueId, String fromNodeId, String toNodeId) {
        this.player = player;
        this.dialogueId = dialogueId;
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public String getDialogueId() {
        return dialogueId;
    }

    public String getFromNodeId() {
        return fromNodeId;
    }

    public String getToNodeId() {
        return toNodeId;
    }
}
