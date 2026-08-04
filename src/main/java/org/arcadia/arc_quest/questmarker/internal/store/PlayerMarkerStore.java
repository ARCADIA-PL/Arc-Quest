package org.arcadia.arc_quest.questmarker.internal.store;

import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.internal.MarkerModelAdapter;
import org.arcadia.arc_quest.questmarker.internal.model.MarkerSnapshot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PlayerMarkerStore {

    public static final int MAX_MARKERS = 4096;

    private final Map<String, MarkerSnapshot> snapshots = new LinkedHashMap<>();
    private final Map<String, QuestMarkerData> publicView;
    private final Set<String> consumedOneShotMarkers;
    private boolean dirty;

    public PlayerMarkerStore(Map<String, QuestMarkerData> publicView, Set<String> consumedOneShotMarkers) {
        this.publicView = Objects.requireNonNull(publicView);
        this.consumedOneShotMarkers = Objects.requireNonNull(consumedOneShotMarkers);
        for (QuestMarkerData marker : publicView.values()) {
            snapshots.put(marker.getId(), MarkerModelAdapter.fromPublic(marker));
        }
    }

    public boolean upsert(QuestMarkerData marker) {
        Objects.requireNonNull(marker);
        if (!snapshots.containsKey(marker.getId()) && snapshots.size() >= MAX_MARKERS) return false;
        MarkerSnapshot snapshot = MarkerModelAdapter.fromPublic(marker);
        MarkerSnapshot previous = snapshots.put(marker.getId(), snapshot);
        publicView.put(marker.getId(), marker);
        boolean changed = !snapshot.equals(previous);
        if (changed) dirty = true;
        return changed;
    }

    public boolean remove(String markerId) {
        MarkerSnapshot removed = snapshots.remove(markerId);
        publicView.remove(markerId);
        if (removed == null) return false;
        dirty = true;
        return true;
    }

    public boolean removeByQuest(String questId) {
        boolean[] changed = {false};
        snapshots.entrySet().removeIf(entry -> {
            if (!MarkerModelAdapter.belongsToQuest(entry.getValue(), questId)) return false;
            publicView.remove(entry.getKey());
            changed[0] = true;
            return true;
        });
        if (changed[0]) dirty = true;
        return changed[0];
    }

    public void clear() {
        if (snapshots.isEmpty() && publicView.isEmpty()) return;
        snapshots.clear();
        publicView.clear();
        dirty = true;
    }

    public void clearAll() {
        boolean changed = !snapshots.isEmpty() || !publicView.isEmpty() || !consumedOneShotMarkers.isEmpty();
        snapshots.clear();
        publicView.clear();
        consumedOneShotMarkers.clear();
        if (changed) dirty = true;
    }

    public QuestMarkerData get(String markerId) {
        return publicView.get(markerId);
    }

    public Map<String, QuestMarkerData> publicView() {
        return Collections.unmodifiableMap(publicView);
    }

    public Map<String, MarkerSnapshot> snapshots() {
        return Collections.unmodifiableMap(snapshots);
    }

    public boolean isOneShotConsumed(String markerId) {
        return consumedOneShotMarkers.contains(markerId);
    }

    public boolean consumeOneShot(String markerId) {
        return consumedOneShotMarkers.add(markerId);
    }

    public boolean removeConsumedByPrefix(String prefix) {
        return consumedOneShotMarkers.removeIf(markerId -> markerId.startsWith(prefix));
    }

    public Set<String> consumedOneShotMarkers() {
        return Collections.unmodifiableSet(consumedOneShotMarkers);
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }
}
