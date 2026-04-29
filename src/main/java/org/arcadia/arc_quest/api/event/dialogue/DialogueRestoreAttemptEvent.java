package org.arcadia.arc_quest.api.event.dialogue;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

public class DialogueRestoreAttemptEvent extends Event {
    private final ServerPlayer player;
    private final String dialogueId;
    private final String restoreNodeId;

    public DialogueRestoreAttemptEvent(ServerPlayer player, String dialogueId, String restoreNodeId) {
        this.player = player;
        this.dialogueId = dialogueId;
        this.restoreNodeId = restoreNodeId;
    }

    public ServerPlayer getPlayer() { return player; }
    public String getDialogueId() { return dialogueId; }
    public String getRestoreNodeId() { return restoreNodeId; }
}
