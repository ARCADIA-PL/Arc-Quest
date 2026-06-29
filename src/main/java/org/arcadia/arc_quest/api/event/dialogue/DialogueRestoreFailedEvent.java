package org.arcadia.arc_quest.api.event.dialogue;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

public class DialogueRestoreFailedEvent extends Event {
    private final ServerPlayer player;
    private final String dialogueId;
    private final String restoreNodeId;
    private final FailureReason reason;

    public DialogueRestoreFailedEvent(ServerPlayer player, String dialogueId, String restoreNodeId, FailureReason reason) {
        this.player = player;
        this.dialogueId = dialogueId;
        this.restoreNodeId = restoreNodeId;
        this.reason = reason;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public String getDialogueId() {
        return dialogueId;
    }

    public String getRestoreNodeId() {
        return restoreNodeId;
    }

    public FailureReason getReason() {
        return reason;
    }

    public enum FailureReason {NO_SESSION, NODE_NOT_FOUND}
}
