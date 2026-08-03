package org.arcadia.arc_quest.questplayer.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerState;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerType;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ArcQuestQuestState {

    private static final int MAX_PERSISTED_MARKERS = 4096;
    private static final int MAX_MARKER_STRING_LENGTH = 512;

    public interface AttachPointParser {
        QuestMarkerData.EntityAttachPoint parse(String value);
    }

    private final Map<String, QuestRuntimeData> activeQuests;
    private final Set<String> completedQuests;
    private final Set<String> failedQuests;
    private final Map<String, Set<String>> readPhaseStories;
    private final Map<String, QuestMarkerData> markers;
    private final Set<String> consumedOneShotMarkers;
    private boolean dirty;
    private boolean markerDirty;

    public ArcQuestQuestState(Map<String, QuestRuntimeData> activeQuests,
                              Set<String> completedQuests,
                               Set<String> failedQuests,
                               Map<String, Set<String>> readPhaseStories,
                               Map<String, QuestMarkerData> markers) {
        this(activeQuests, completedQuests, failedQuests, readPhaseStories, markers, new LinkedHashSet<>());
    }

    public ArcQuestQuestState(Map<String, QuestRuntimeData> activeQuests,
                              Set<String> completedQuests,
                              Set<String> failedQuests,
                              Map<String, Set<String>> readPhaseStories,
                              Map<String, QuestMarkerData> markers,
                              Set<String> consumedOneShotMarkers) {
        this.activeQuests = Objects.requireNonNull(activeQuests);
        this.completedQuests = Objects.requireNonNull(completedQuests);
        this.failedQuests = Objects.requireNonNull(failedQuests);
        this.readPhaseStories = Objects.requireNonNull(readPhaseStories);
        this.markers = Objects.requireNonNull(markers);
        this.consumedOneShotMarkers = Objects.requireNonNull(consumedOneShotMarkers);
    }

    public void addActiveQuest(QuestRuntimeData data) {
        Objects.requireNonNull(data);
        String questId = data.getQuestId();
        activeQuests.put(questId, data);
        failedQuests.remove(questId);
        dirty = true;
    }

    public void removeActiveQuest(String questId) {
        activeQuests.remove(questId);
        dirty = true;
    }

    public void resetQuest(String questId) {
        activeQuests.remove(questId);
        completedQuests.remove(questId);
        failedQuests.remove(questId);
        readPhaseStories.remove(questId);
        markers.entrySet().removeIf(entry -> questId.equals(entry.getValue().getQuestId()));
        consumedOneShotMarkers.removeIf(id -> id.startsWith("aq:auto:" + questId + ":"));
        dirty = true;
        markerDirty = true;
    }

    public boolean markPhaseStoryRead(String questId, String phaseId) {
        if (questId == null || questId.isBlank() || phaseId == null || phaseId.isBlank()) return false;
        boolean changed = readPhaseStories.computeIfAbsent(questId, ignored -> new LinkedHashSet<>()).add(phaseId);
        if (changed) dirty = true;
        return changed;
    }

    public boolean isPhaseStoryRead(String questId, String phaseId) {
        Set<String> phaseIds = readPhaseStories.get(questId);
        return phaseIds != null && phaseIds.contains(phaseId);
    }

    public void markCompleted(String questId) {
        activeQuests.remove(questId);
        completedQuests.add(questId);
        failedQuests.remove(questId);
        dirty = true;
    }

    public void markFailed(String questId) {
        activeQuests.remove(questId);
        failedQuests.add(questId);
        dirty = true;
    }

    @Nullable
    public QuestRuntimeData getActiveQuest(String questId) {
        return activeQuests.get(questId);
    }

    public Map<String, QuestRuntimeData> getAllActiveQuests() {
        return Collections.unmodifiableMap(activeQuests);
    }

    public Set<String> getCompletedQuests() {
        return Collections.unmodifiableSet(completedQuests);
    }

    public Set<String> getFailedQuests() {
        return Collections.unmodifiableSet(failedQuests);
    }

    public boolean isQuestActive(String questId) {
        return activeQuests.containsKey(questId);
    }

    public boolean isQuestCompleted(String questId) {
        return completedQuests.contains(questId);
    }

    public boolean isQuestFailed(String questId) {
        return failedQuests.contains(questId);
    }

    public void upsertMarker(QuestMarkerData marker) {
        Objects.requireNonNull(marker);
        if (!markers.containsKey(marker.getId()) && markers.size() >= MAX_PERSISTED_MARKERS) return;
        QuestMarkerData previous = markers.put(marker.getId(), marker);
        if (!marker.equals(previous)) markerDirty = true;
    }

    public void removeMarker(String markerId) {
        if (markers.remove(markerId) != null) markerDirty = true;
    }

    public void clearMarkers() {
        if (markers.isEmpty()) return;
        markers.clear();
        markerDirty = true;
    }

    public Map<String, QuestMarkerData> getAllMarkers() {
        return Collections.unmodifiableMap(markers);
    }

    public boolean isOneShotMarkerConsumed(String markerId) {
        return consumedOneShotMarkers.contains(markerId);
    }

    public boolean consumeOneShotMarker(String markerId) {
        boolean changed = consumedOneShotMarkers.add(markerId);
        if (changed) dirty = true;
        return changed;
    }

    public void writeToRoot(CompoundTag root) {
        ListTag activeList = new ListTag();
        for (QuestRuntimeData data : activeQuests.values()) activeList.add(data.serializeNBT());
        root.put("ActiveQuests", activeList);

        ListTag completedList = new ListTag();
        for (String id : completedQuests) completedList.add(StringTag.valueOf(id));
        root.put("CompletedQuests", completedList);

        ListTag failedList = new ListTag();
        for (String id : failedQuests) failedList.add(StringTag.valueOf(id));
        root.put("FailedQuests", failedList);

        ListTag readStoryList = new ListTag();
        for (Map.Entry<String, Set<String>> entry : readPhaseStories.entrySet()) {
            for (String phaseId : entry.getValue()) {
                CompoundTag storyTag = new CompoundTag();
                storyTag.putString("questId", entry.getKey());
                storyTag.putString("phaseId", phaseId);
                readStoryList.add(storyTag);
            }
        }
        root.put("ReadPhaseStories", readStoryList);

        ListTag markerList = new ListTag();
        for (QuestMarkerData m : markers.values()) {
            if (!m.isPersistent() || markerList.size() >= MAX_PERSISTED_MARKERS) continue;
            CompoundTag t = new CompoundTag();
            t.putString("id", m.getId());
            t.putDouble("x", m.getWorldX());
            t.putDouble("y", m.getWorldY());
            t.putDouble("z", m.getWorldZ());
            t.putString("label", m.getLabel());
            t.putString("dimension", m.getDimension());
            t.putString("questId", m.getQuestId());
            t.putString("phaseId", m.getPhaseId());
            t.putInt("objectiveIndex", m.getObjectiveIndex());
            t.putInt("color", m.getColorARGB());
            t.putString("type", m.getType().name());
            t.putString("state", m.getState().name());
            t.putBoolean("showDistance", m.isShowDistance());
            t.putBoolean("allowOffscreenArrow", m.isAllowOffscreenArrow());
            t.putInt("followEntityId", m.getFollowEntityId());
            t.putString("followEntityUuid", m.getFollowEntityUuid());
            t.putString("followEntityGuid", m.getFollowEntityGuid());
            t.putString("attachPoint", m.getAttachPoint().name());
            t.putInt("priority", m.getPriority());
            t.putBoolean("persistent", m.isPersistent());
            CompoundTag styleHints = new CompoundTag();
            int styleCount = 0;
            for (Map.Entry<String, String> style : m.getStyleHints().entrySet()) {
                if (styleCount++ >= 64) break;
                styleHints.putString(limitedString(style.getKey()), limitedString(style.getValue()));
            }
            t.put("styleHints", styleHints);
            markerList.add(t);
        }
        root.put("Markers", markerList);

        ListTag consumedMarkerList = new ListTag();
        consumedOneShotMarkers.stream().filter(ArcQuestQuestState::validMarkerString).limit(MAX_PERSISTED_MARKERS)
                .map(StringTag::valueOf)
                .forEach(consumedMarkerList::add);
        root.put("ConsumedOneShotMarkers", consumedMarkerList);
    }

    public void readFromRoot(CompoundTag root, AttachPointParser attachPointParser) {
        activeQuests.clear();
        completedQuests.clear();
        failedQuests.clear();
        readPhaseStories.clear();
        markers.clear();
        consumedOneShotMarkers.clear();

        ListTag activeList = root.getList("ActiveQuests", Tag.TAG_COMPOUND);
        for (int i = 0; i < activeList.size(); i++) {
            QuestRuntimeData data = QuestRuntimeData.deserializeNBT(activeList.getCompound(i));
            activeQuests.put(data.getQuestId(), data);
        }

        ListTag completedList = root.getList("CompletedQuests", Tag.TAG_STRING);
        for (int i = 0; i < completedList.size(); i++) completedQuests.add(completedList.getString(i));

        ListTag failedList = root.getList("FailedQuests", Tag.TAG_STRING);
        for (int i = 0; i < failedList.size(); i++) failedQuests.add(failedList.getString(i));

        ListTag readStoryList = root.getList("ReadPhaseStories", Tag.TAG_COMPOUND);
        int readStoryCount = Math.min(readStoryList.size(), 8192);
        for (int i = 0; i < readStoryCount; i++) {
            CompoundTag storyTag = readStoryList.getCompound(i);
            String questId = storyTag.getString("questId");
            String phaseId = storyTag.getString("phaseId");
            if (questId.isBlank() || phaseId.isBlank() || questId.length() > 256 || phaseId.length() > 256) continue;
            readPhaseStories.computeIfAbsent(questId, ignored -> new LinkedHashSet<>()).add(phaseId);
        }

        ListTag markerList = root.getList("Markers", Tag.TAG_COMPOUND);
        int markerCount = Math.min(markerList.size(), MAX_PERSISTED_MARKERS);
        for (int i = 0; i < markerCount; i++) {
            CompoundTag t = markerList.getCompound(i);
            String id = t.getString("id");
            if (!validMarkerString(id) || isDerivedMarkerId(id)) continue;
            double x = t.getDouble("x");
            double y = t.getDouble("y");
            double z = t.getDouble("z");
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) continue;

            QuestMarkerType type;
            QuestMarkerState state;
            try { type = QuestMarkerType.valueOf(t.getString("type")); }
            catch (Exception e) { type = QuestMarkerType.CUSTOM; }
            try { state = QuestMarkerState.valueOf(t.getString("state")); }
            catch (Exception e) { state = QuestMarkerState.ACTIVE; }

            QuestMarkerData marker = new QuestMarkerData.Builder(
                    id, x, y, z, limitedString(t.getString("label")))
                    .dimension(t.contains("dimension", Tag.TAG_STRING) ? t.getString("dimension") : "minecraft:overworld")
                    .bindQuest(t.contains("questId", Tag.TAG_STRING) ? t.getString("questId") : "")
                    .bindPhase(t.contains("phaseId", Tag.TAG_STRING) ? t.getString("phaseId") : "")
                    .bindObjective(t.contains("objectiveIndex", Tag.TAG_INT) ? t.getInt("objectiveIndex") : -1)
                    .followEntity(
                            t.contains("followEntityId", Tag.TAG_INT) ? t.getInt("followEntityId") : -1,
                            t.contains("followEntityUuid", Tag.TAG_STRING) ? t.getString("followEntityUuid") : "",
                            t.contains("followEntityGuid", Tag.TAG_STRING) ? t.getString("followEntityGuid") : "",
                            attachPointParser.parse(t.contains("attachPoint", Tag.TAG_STRING) ? t.getString("attachPoint") : "HEAD"))
                    .type(type).state(state).color(t.getInt("color"))
                    .showDistance(!t.contains("showDistance", Tag.TAG_BYTE) || t.getBoolean("showDistance"))
                    .allowOffscreenArrow(!t.contains("allowOffscreenArrow", Tag.TAG_BYTE) || t.getBoolean("allowOffscreenArrow"))
                    .priority(t.contains("priority", Tag.TAG_INT) ? t.getInt("priority") : 0)
                    .styleHints(readStyleHints(t))
                    .persistent(!t.contains("persistent", Tag.TAG_BYTE) || t.getBoolean("persistent"))
                    .build();
            markers.put(id, marker);
        }
        dirty = false;

        ListTag consumedMarkerList = root.getList("ConsumedOneShotMarkers", Tag.TAG_STRING);
        int consumedCount = Math.min(consumedMarkerList.size(), MAX_PERSISTED_MARKERS);
        for (int i = 0; i < consumedCount; i++) {
            String markerId = consumedMarkerList.getString(i);
            if (validMarkerString(markerId)) consumedOneShotMarkers.add(markerId);
        }
    }

    private static Map<String, String> readStyleHints(CompoundTag markerTag) {
        if (!markerTag.contains("styleHints", Tag.TAG_COMPOUND)) return Map.of();
        CompoundTag styleTag = markerTag.getCompound("styleHints");
        Map<String, String> result = new LinkedHashMap<>();
        int count = 0;
        for (String key : styleTag.getAllKeys()) {
            if (count >= 64 || !validMarkerString(key)) break;
            String value = styleTag.getString(key);
            if (validMarkerString(value)) {
                result.put(key, value);
                count++;
            }
        }
        return result;
    }

    private static boolean validMarkerString(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_MARKER_STRING_LENGTH;
    }

    private static String limitedString(String value) {
        if (value == null) return "";
        return value.length() <= MAX_MARKER_STRING_LENGTH ? value : value.substring(0, MAX_MARKER_STRING_LENGTH);
    }

    private static boolean isDerivedMarkerId(String markerId) {
        return markerId.startsWith("aq:auto:") || markerId.startsWith("aq:dlg:") || markerId.startsWith("quest:");
    }

    public boolean isDirty() {
        if (dirty || markerDirty) return true;
        for (QuestRuntimeData data : activeQuests.values()) {
            if (data.isDirty()) return true;
        }
        return false;
    }

    public boolean isQuestStateDirty() {
        if (dirty) return true;
        for (QuestRuntimeData data : activeQuests.values()) {
            if (data.isDirty()) return true;
        }
        return false;
    }

    public boolean isMarkerDirty() {
        return markerDirty;
    }

    public void clearQuestStateDirty() {
        dirty = false;
        for (QuestRuntimeData data : activeQuests.values()) data.clearDirty();
    }

    public void clearMarkerDirty() {
        markerDirty = false;
    }

    public void clearDirty() {
        dirty = false;
        markerDirty = false;
        for (QuestRuntimeData data : activeQuests.values()) data.clearDirty();
    }

    public void clear() {
        activeQuests.clear();
        completedQuests.clear();
        failedQuests.clear();
        readPhaseStories.clear();
        markers.clear();
        consumedOneShotMarkers.clear();
        dirty = true;
        markerDirty = true;
    }
}
