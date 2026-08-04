package org.arcadia.arc_quest.client.events;

import net.minecraftforge.eventbus.api.Event;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;

import java.util.Collection;
import java.util.List;

public final class QuestMarkerClientSnapshotEvent extends Event {

    private final List<QuestMarkerData> markers;

    public QuestMarkerClientSnapshotEvent(Collection<QuestMarkerData> markers) {
        this.markers = List.copyOf(markers);
    }

    public List<QuestMarkerData> markers() {
        return markers;
    }
}
