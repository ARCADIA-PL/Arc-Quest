package org.arcadia.arc_quest.client.hud.questmarker;

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

    private long currentEpoch = -1L;
    private long currentRevision = -1L;

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
        return currentEpoch;
    }

    public synchronized long getCurrentRevision() {
        return currentRevision;
    }

    public synchronized boolean applySnapshot(long epoch, long revision, Collection<QuestMarkerData> snapshot) {
        if (epoch < currentEpoch) {
            return false;
        }
        if (epoch == currentEpoch && revision < currentRevision) {
            return false;
        }

        markers.clear();
        for (QuestMarkerData data : snapshot) {
            markers.put(data.getId(), data);
        }
        currentEpoch = epoch;
        currentRevision = revision;
        return true;
    }

    public synchronized boolean applyDelta(long epoch, long revision, Consumer<Map<String, QuestMarkerData>> mutator) {
        if (currentEpoch < 0 || currentRevision < 0) {
            return false;
        }
        if (epoch != currentEpoch) {
            return false;
        }
        if (revision <= currentRevision) {
            return false;
        }
        if (revision != currentRevision + 1) {
            return false;
        }

        mutator.accept(markers);
        currentRevision = revision;
        return true;
    }
}
