package org.arcadia.arc_quest.api.event.quest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

public class QuestPhaseActivatedEvent extends Event {
    private final ServerPlayer player;
    private final ResourceLocation questId;
    private final String fromPhaseId;
    private final String toPhaseId;
    private final boolean autoActivated;

    public QuestPhaseActivatedEvent(ServerPlayer player, ResourceLocation questId,
                                    String fromPhaseId, String toPhaseId, boolean autoActivated) {
        this.player = player;
        this.questId = questId;
        this.fromPhaseId = fromPhaseId;
        this.toPhaseId = toPhaseId;
        this.autoActivated = autoActivated;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public ResourceLocation getQuestId() {
        return questId;
    }

    public String getFromPhaseId() {
        return fromPhaseId;
    }

    public String getToPhaseId() {
        return toPhaseId;
    }

    public boolean isAutoActivated() {
        return autoActivated;
    }
}
