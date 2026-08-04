package org.arcadia.arc_quest.client.hud.questmarker;

import net.minecraftforge.common.MinecraftForge;
import org.arcadia.arc_quest.client.events.QuestMarkerClientSnapshotEvent;
import org.arcadia.arc_quest.core.state.VersionedStreamGate;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 客户端任务标记缓存（由服务端权威同步）。
 */
public final class QuestMarkerManager {

    public static final QuestMarkerManager INSTANCE = new QuestMarkerManager();

    private final Map<String, QuestMarkerData> markers = new HashMap<>();

    private final VersionedStreamGate revisionGate = new VersionedStreamGate();

    private QuestMarkerManager() {
    }

    public void add(QuestMarkerData data) {
        Collection<QuestMarkerData> snapshot;
        synchronized (this) {
            markers.put(data.getId(), data);
            snapshot = snapshot();
        }
        publish(snapshot);
    }

    public void remove(String id) {
        Collection<QuestMarkerData> snapshot;
        synchronized (this) {
            markers.remove(id);
            snapshot = snapshot();
        }
        publish(snapshot);
    }

    public void clear() {
        synchronized (this) {
            markers.clear();
            revisionGate.clear();
            MarkerHudRenderer.INSTANCE.clearVisualState();
        }
        publish(List.of());
    }

    public synchronized QuestMarkerData get(String id) {
        return markers.get(id);
    }

    public synchronized Collection<QuestMarkerData> all() {
        return markers.values().stream().toList();
    }

    public synchronized boolean has(String id) {
        return markers.containsKey(id);
    }

    public synchronized long getCurrentEpoch() {
        return revisionGate.epoch();
    }

    public synchronized long getCurrentRevision() {
        return revisionGate.revision();
    }

    public boolean applySnapshot(long epoch, long revision, Collection<QuestMarkerData> incomingSnapshot) {
        Collection<QuestMarkerData> acceptedSnapshot;
        synchronized (this) {
            VersionedStreamGate.Decision decision = revisionGate.applySnapshot(epoch, revision, () -> {
                markers.clear();
                for (QuestMarkerData data : incomingSnapshot) {
                    markers.put(data.getId(), data);
                }
            });
            if (decision != VersionedStreamGate.Decision.ACCEPT) return false;
            acceptedSnapshot = snapshot();
        }
        publish(acceptedSnapshot);
        return true;
    }

    public boolean applyDelta(long epoch, long revision, Consumer<Map<String, QuestMarkerData>> mutator) {
        Collection<QuestMarkerData> acceptedSnapshot;
        synchronized (this) {
            long baseRevision = revisionGate.revision();
            VersionedStreamGate.Decision decision = revisionGate.applyDelta(
                    epoch, baseRevision, revision, () -> mutator.accept(markers));
            if (decision == VersionedStreamGate.Decision.GAP) ArcQuestNetwork.requestMarkerResync();
            if (decision != VersionedStreamGate.Decision.ACCEPT) return false;
            acceptedSnapshot = snapshot();
        }
        publish(acceptedSnapshot);
        return true;
    }

    private Collection<QuestMarkerData> snapshot() {
        return List.copyOf(markers.values());
    }

    private static void publish(Collection<QuestMarkerData> snapshot) {
        MinecraftForge.EVENT_BUS.post(new QuestMarkerClientSnapshotEvent(snapshot));
    }
}
