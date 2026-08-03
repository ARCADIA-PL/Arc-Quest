package org.arcadia.arc_quest.client.hud.questmarker;

import org.arcadia.arc_quest.core.state.VersionedStreamGate;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;

import java.util.Collection;
import java.util.HashMap;
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

    public synchronized void add(QuestMarkerData data) {
        markers.put(data.getId(), data);
    }

    public synchronized void remove(String id) {
        markers.remove(id);
    }

    public synchronized void clear() {
        markers.clear();
        revisionGate.clear();
        MarkerHudRenderer.INSTANCE.clearVisualState();
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

    public synchronized boolean applySnapshot(long epoch, long revision, Collection<QuestMarkerData> snapshot) {
        VersionedStreamGate.Decision decision = revisionGate.applySnapshot(epoch, revision, () -> {
            markers.clear();
            for (QuestMarkerData data : snapshot) {
                markers.put(data.getId(), data);
            }
        });
        if (decision != VersionedStreamGate.Decision.ACCEPT) return false;
        return true;
    }

    public synchronized boolean applyDelta(long epoch, long revision, Consumer<Map<String, QuestMarkerData>> mutator) {
        long baseRevision = revisionGate.revision();
        VersionedStreamGate.Decision decision = revisionGate.applyDelta(
                epoch, baseRevision, revision, () -> mutator.accept(markers));
        if (decision == VersionedStreamGate.Decision.GAP) ArcQuestNetwork.requestMarkerResync();
        if (decision != VersionedStreamGate.Decision.ACCEPT) return false;
        return true;
    }
}
