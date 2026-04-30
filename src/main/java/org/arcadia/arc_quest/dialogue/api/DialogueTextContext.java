package org.arcadia.arc_quest.dialogue.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.arcadia.arc_quest.quest.capability.IQuestCapability;

import javax.annotation.Nullable;
import java.util.Map;

public record DialogueTextContext(
        ServerPlayer player,
        @Nullable Entity npc,
        @Nullable IDialogueNpc dialogueNpc,
        @Nullable String dialogueId,
        @Nullable String nodeId,
        @Nullable IQuestCapability questCap,
        Map<String, Object> vars
) {
    public DialogueTextContext {
        vars = vars == null ? Map.of() : Map.copyOf(vars);
    }

    public static DialogueTextContext of(ServerPlayer player, @Nullable Entity npc) {
        IDialogueNpc dialogueNpc = (npc instanceof IDialogueNpc d) ? d : null;
        return new DialogueTextContext(player, npc, dialogueNpc, null, null, null, Map.of());
    }
}