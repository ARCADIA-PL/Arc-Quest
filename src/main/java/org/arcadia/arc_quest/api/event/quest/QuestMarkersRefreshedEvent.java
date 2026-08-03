package org.arcadia.arc_quest.api.event.quest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

public class QuestMarkersRefreshedEvent extends Event {
    private final ServerPlayer player;
    private final ResourceLocation questId;
    private final int activePhaseCount;
    private final int markerCount;

    public QuestMarkersRefreshedEvent(ServerPlayer player, ResourceLocation questId, int activePhaseCount) {
        this(player, questId, activePhaseCount, -1);
    }

    public QuestMarkersRefreshedEvent(ServerPlayer player,
                                      ResourceLocation questId,
                                      int activePhaseCount,
                                      int markerCount) {
        this.player = player;
        this.questId = questId;
        this.activePhaseCount = activePhaseCount;
        this.markerCount = markerCount;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public ResourceLocation getQuestId() {
        return questId;
    }

    public int getActivePhaseCount() {
        return activePhaseCount;
    }

    public int getMarkerCount() {
        return markerCount;
    }
}
