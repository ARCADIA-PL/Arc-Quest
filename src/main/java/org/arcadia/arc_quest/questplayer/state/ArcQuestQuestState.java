package org.arcadia.arc_quest.questplayer.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.internal.MarkerIds;
import org.arcadia.arc_quest.questmarker.internal.codec.MarkerNbtCodec;
import org.arcadia.arc_quest.questmarker.internal.store.PlayerMarkerStore;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashSet;
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
    private final PlayerMarkerStore markerStore;
    private boolean dirty;

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
        this.markerStore = new PlayerMarkerStore(markers, consumedOneShotMarkers);
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
        markerStore.removeByQuest(questId);
        markerStore.removeConsumedByPrefix(MarkerIds.autoQuestPrefix(questId));
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
        markerStore.upsert(marker);
    }

    public void removeMarker(String markerId) {
        markerStore.remove(markerId);
    }

    public void clearMarkers() {
        markerStore.clear();
    }

    public Map<String, QuestMarkerData> getAllMarkers() {
        return markerStore.publicView();
    }

    public boolean isOneShotMarkerConsumed(String markerId) {
        return markerStore.isOneShotConsumed(markerId);
    }

    public boolean consumeOneShotMarker(String markerId) {
        boolean changed = markerStore.consumeOneShot(markerId);
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

        MarkerNbtCodec.writeToRoot(root, markerStore);
    }

    public void readFromRoot(CompoundTag root, AttachPointParser attachPointParser) {
        activeQuests.clear();
        completedQuests.clear();
        failedQuests.clear();
        readPhaseStories.clear();
        markerStore.clearAll();

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

        MarkerNbtCodec.readFromRoot(root, markerStore, attachPointParser::parse);
    }

    public boolean isDirty() {
        if (dirty || markerStore.isDirty()) return true;
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
        return markerStore.isDirty();
    }

    public void clearQuestStateDirty() {
        dirty = false;
        for (QuestRuntimeData data : activeQuests.values()) data.clearDirty();
    }

    public void clearMarkerDirty() {
        markerStore.clearDirty();
    }

    public void clearDirty() {
        dirty = false;
        markerStore.clearDirty();
        for (QuestRuntimeData data : activeQuests.values()) data.clearDirty();
    }

    public void clear() {
        activeQuests.clear();
        completedQuests.clear();
        failedQuests.clear();
        readPhaseStories.clear();
        markerStore.clearAll();
        dirty = true;
    }
}
