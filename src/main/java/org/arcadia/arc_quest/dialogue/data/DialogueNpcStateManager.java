package org.arcadia.arc_quest.dialogue.data;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DialogueNpcStateManager {

    private static final ConcurrentHashMap<UUID, State> MAP = new ConcurrentHashMap<>();

    private DialogueNpcStateManager() {
    }

    public record State(@Nullable Player conversingPlayer, @Nullable UUID playerUuid) {}

    @Nullable
    public static State get(Entity npc) {
        return MAP.get(npc.getUUID());
    }

    public static void setConversing(Entity npc, @Nullable Player player) {
        if (player == null) MAP.remove(npc.getUUID());
        else MAP.put(npc.getUUID(), new State(player, player.getUUID()));
    }

    public static void clear(Entity npc) {
        MAP.remove(npc.getUUID());
    }
}
