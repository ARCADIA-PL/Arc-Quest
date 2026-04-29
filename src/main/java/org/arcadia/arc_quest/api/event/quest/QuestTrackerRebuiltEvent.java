package org.arcadia.arc_quest.api.event.quest;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

public class QuestTrackerRebuiltEvent extends Event {
    private final ServerPlayer player;
    private final int activeQuestCount;

    public QuestTrackerRebuiltEvent(ServerPlayer player, int activeQuestCount) {
        this.player = player;
        this.activeQuestCount = activeQuestCount;
    }

    public ServerPlayer getPlayer() { return player; }
    public int getActiveQuestCount() { return activeQuestCount; }
}
