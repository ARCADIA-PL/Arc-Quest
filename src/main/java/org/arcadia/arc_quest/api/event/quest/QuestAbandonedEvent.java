package org.arcadia.arc_quest.api.event.quest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

public class QuestAbandonedEvent extends Event {
    private final ServerPlayer player;
    private final ResourceLocation questId;

    public QuestAbandonedEvent(ServerPlayer player, ResourceLocation questId) {
        this.player = player;
        this.questId = questId;
    }

    public ServerPlayer getPlayer() { return player; }
    public ResourceLocation getQuestId() { return questId; }
}
