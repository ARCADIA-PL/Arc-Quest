package org.arcadia.arc_quest.api.event.quest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

public class QuestMarkersRefreshedEvent extends Event {
    private final ServerPlayer player;
    private final ResourceLocation questId;
    private final int activePhaseCount;

    public QuestMarkersRefreshedEvent(ServerPlayer player, ResourceLocation questId, int activePhaseCount) {
        this.player = player;
        this.questId = questId;
        this.activePhaseCount = activePhaseCount;
    }

    public ServerPlayer getPlayer() { return player; }
    public ResourceLocation getQuestId() { return questId; }
    public int getActivePhaseCount() { return activePhaseCount; }
}
