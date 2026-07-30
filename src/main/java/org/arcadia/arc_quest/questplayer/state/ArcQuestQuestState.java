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

    public interface AttachPointParser {
        QuestMarkerData.EntityAttachPoint parse(String value);
    }

    private final Map<String, QuestRuntimeData> activeQuests;
    private final Set<String> completedQuests;
    private final Set<String> failedQuests;
    private final Map<String, Set<String>> readPhaseStories;
    private final Map<String, QuestMarkerData> markers;
    private boolean dirty;

    public ArcQuestQuestState(Map<String, QuestRuntimeData> activeQuests,
                              Set<String> completedQuests,
                              Set<String> failedQuests,
                              Map<String, Set<String>> readPhaseStories,
                              Map<String, QuestMarkerData> markers) {
        this.activeQuests = Objects.requireNonNull(activeQuests);
        this.completedQuests = Objects.requireNonNull(completedQuests);
        this.failedQuests = Objects.requireNonNull(failedQuests);
        this.readPhaseStories = Objects.requireNonNull(readPhaseStories);
        this.markers = Objects.requireNonNull(markers);
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
        dirty = true;
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
        markers.put(marker.getId(), marker);
        dirty = true;
    }

    public void removeMarker(String markerId) {
        markers.remove(markerId);
        dirty = true;
    }

    public void clearMarkers() {
        markers.clear();
        dirty = true;
    }

    public Map<String, QuestMarkerData> getAllMarkers() {
        return Collections.unmodifiableMap(markers);
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
            markerList.add(t);
        }
        root.put("Markers", markerList);
    }

    public void readFromRoot(CompoundTag root, AttachPointParser attachPointParser) {
        activeQuests.clear();
        completedQuests.clear();
        failedQuests.clear();
        readPhaseStories.clear();
        markers.clear();

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
        for (int i = 0; i < markerList.size(); i++) {
            CompoundTag t = markerList.getCompound(i);
            String id = t.getString("id");
            if (id == null || id.isEmpty()) continue;

            QuestMarkerType type;
            QuestMarkerState state;
            try { type = QuestMarkerType.valueOf(t.getString("type")); }
            catch (Exception e) { type = QuestMarkerType.CUSTOM; }
            try { state = QuestMarkerState.valueOf(t.getString("state")); }
            catch (Exception e) { state = QuestMarkerState.ACTIVE; }

            QuestMarkerData marker = new QuestMarkerData.Builder(
                    id, t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getString("label"))
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
                    .build();
            markers.put(id, marker);
        }
    }

    public boolean isDirty() {
        if (dirty) return true;
        for (QuestRuntimeData data : activeQuests.values()) {
            if (data.isDirty()) return true;
        }
        return false;
    }

    public void clearDirty() {
        dirty = false;
        for (QuestRuntimeData data : activeQuests.values()) data.clearDirty();
    }

    public void clear() {
        activeQuests.clear();
        completedQuests.clear();
        failedQuests.clear();
        readPhaseStories.clear();
        markers.clear();
        dirty = true;
    }
}
