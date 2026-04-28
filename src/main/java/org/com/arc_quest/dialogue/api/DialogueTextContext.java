package org.com.arc_quest.dialogue.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.com.arc_quest.quest.capability.IQuestCapability;

import javax.annotation.Nullable;
import java.util.Map;

public record DialogueTextContext(
        ServerPlayer player,
        @Nullable Entity npc,
        @Nullable String dialogueId,
        @Nullable String nodeId,
        @Nullable IQuestCapability questCap,
        Map<String, Object> vars
) {
    public DialogueTextContext {
        vars = vars == null ? Map.of() : Map.copyOf(vars);
    }

    public static DialogueTextContext of(ServerPlayer player, @Nullable Entity npc) {
        return new DialogueTextContext(player, npc, null, null, null, Map.of());
    }
}
