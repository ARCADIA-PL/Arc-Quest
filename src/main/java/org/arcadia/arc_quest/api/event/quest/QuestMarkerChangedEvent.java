package org.arcadia.arc_quest.api.event.quest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;

import java.util.List;

/** 任务 Marker 对账产生实际增删改后触发的服务端事件。 */
public final class QuestMarkerChangedEvent extends Event {
    private final ServerPlayer player;
    private final ResourceLocation questId;
    private final List<QuestMarkerData> upsertedMarkers;
    private final List<String> removedMarkerIds;

    public QuestMarkerChangedEvent(ServerPlayer player, ResourceLocation questId,
                                   List<QuestMarkerData> upsertedMarkers, List<String> removedMarkerIds) {
        this.player = player;
        this.questId = questId;
        this.upsertedMarkers = List.copyOf(upsertedMarkers);
        this.removedMarkerIds = List.copyOf(removedMarkerIds);
    }

    public ServerPlayer getPlayer() { return player; }
    public ResourceLocation getQuestId() { return questId; }
    public List<QuestMarkerData> getUpsertedMarkers() { return upsertedMarkers; }
    public List<String> getRemovedMarkerIds() { return removedMarkerIds; }
    public boolean hasChanges() { return !upsertedMarkers.isEmpty() || !removedMarkerIds.isEmpty(); }
}
