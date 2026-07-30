package org.arcadia.arc_quest.questplayer;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.dialogue.runtime.DialogueProgressStore;
import org.arcadia.arc_quest.quest.data.CollectionRuntimeData;
import org.arcadia.arc_quest.quest.data.GachaDataStore;
import org.arcadia.arc_quest.quest.data.NbtVersionManager;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.data.TradeDataStore;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questplayer.state.ArcQuestGuideState;
import org.arcadia.arc_quest.questplayer.state.ArcQuestProfileState;
import org.arcadia.arc_quest.questplayer.state.ArcQuestQuestState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ArcQuestPlayer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArcQuestPlayer.class);

    public enum DirtyKind {
        NONE(0),
        FLAGS_VARS(1 << 0),
        QUEST_STATE(1 << 1),
        DIALOGUE(1 << 2),
        TRADE_GACHA(1 << 3),
        GUIDE_STATE(1 << 4),
        TRACKED_QUEST(1 << 5),
        FULL(0xFF);

        private final int mask;

        DirtyKind(int mask) {
            this.mask = mask;
        }

        public boolean has(DirtyKind other) {
            return (this.mask & other.mask) != 0;
        }

        public DirtyKind or(DirtyKind other) {
            if (this == NONE) return other;
            if (other == NONE) return this;
            if (this == other) return this;
            return FULL;
        }
    }

    public record GachaDrawRecord(
            String itemId,
            String rarityName,
            int actualCount,
            boolean pityTriggered,
            long drawTime) {
    }

    private static final NbtVersionManager VERSION_MANAGER = new NbtVersionManager(
            "arc_quest:player_data", 4, LOGGER
    );

    static {
        VERSION_MANAGER.addMigration(0, 1, tag -> {
            if (!tag.contains("Flags", Tag.TAG_LIST)) {
                tag.put("Flags", new ListTag());
            }
        });
        VERSION_MANAGER.addMigration(1, 2, tag -> {
            if (tag.contains("NodeVisitHistory", Tag.TAG_COMPOUND)) {
                tag.putInt("_needs_dialogue_migration", 1);
            } else if (!tag.contains("DialogueProgress", Tag.TAG_COMPOUND)) {
                tag.put("DialogueProgress", new CompoundTag());
            }
        });
        VERSION_MANAGER.addMigration(2, 3, tag -> {
            if (tag.contains("_version", Tag.TAG_INT)) {
                int oldVersion = tag.getInt("_version");
                tag.putInt("_ArcQuestVer", Math.max(oldVersion, 3));
                tag.remove("_version");
            } else {
                tag.putInt("_ArcQuestVer", 3);
            }
            tag.remove("_needs_dialogue_migration");
        });
        VERSION_MANAGER.addMigration(3, 4, root -> {
            if (!root.contains("ActiveQuests", Tag.TAG_LIST)) return;

            ListTag activeList = root.getList("ActiveQuests", Tag.TAG_COMPOUND);
            for (int i = 0; i < activeList.size(); i++) {
                CompoundTag q = activeList.getCompound(i);
                boolean hasNewStruct = q.contains("ActivePhases", Tag.TAG_LIST)
                        || q.contains("CompletedPhases", Tag.TAG_LIST)
                        || q.contains("PhaseProgress", Tag.TAG_COMPOUND);

                if (!hasNewStruct) {
                    String legacyPhaseId = q.getString("PhaseId");
                    int[] legacyProgress = q.contains("Progress", Tag.TAG_INT_ARRAY)
                            ? q.getIntArray("Progress")
                            : new int[0];

                    ListTag activePhases = new ListTag();
                    ListTag completedPhases = new ListTag();
                    CompoundTag phaseProgress = new CompoundTag();

                    if (legacyPhaseId != null && !legacyPhaseId.isEmpty()) {
                        activePhases.add(StringTag.valueOf(legacyPhaseId));
                        phaseProgress.putIntArray(legacyPhaseId, legacyProgress);
                    }

                    q.put("ActivePhases", activePhases);
                    q.put("CompletedPhases", completedPhases);
                    q.put("PhaseProgress", phaseProgress);
                    q.remove("PhaseId");
                    q.remove("Progress");
                    continue;
                }

                ListTag activePhases = q.getList("ActivePhases", Tag.TAG_STRING);
                CompoundTag phaseProgress = q.contains("PhaseProgress", Tag.TAG_COMPOUND)
                        ? q.getCompound("PhaseProgress")
                        : new CompoundTag();

                for (int j = 0; j < activePhases.size(); j++) {
                    String pid = activePhases.getString(j);
                    if (pid != null && !pid.isEmpty() && !phaseProgress.contains(pid, Tag.TAG_INT_ARRAY)) {
                        phaseProgress.putIntArray(pid, new int[0]);
                    }
                }

                q.put("PhaseProgress", phaseProgress);
                if (!q.contains("CompletedPhases", Tag.TAG_LIST)) {
                    q.put("CompletedPhases", new ListTag());
                }
            }
        });
    }

    private final UUID ownerUuid;
    private final Map<String, QuestRuntimeData> activeQuests = new Object2ObjectOpenHashMap<>();
    private final Set<String> completedQuests = new ObjectOpenHashSet<>();
    private final Set<String> failedQuests = new ObjectOpenHashSet<>();
    private final Map<String, Set<String>> readPhaseStories = new Object2ObjectOpenHashMap<>();
    private final Map<String, Integer> variables = new Object2IntOpenHashMap<>();
    private final Map<String, QuestMarkerData> markers = new LinkedHashMap<>();

    private final ArcQuestQuestState questState;
    private final ArcQuestProfileState profileState;
    private final ArcQuestGuideState guideState;
    private final DialogueProgressStore dialogueProgress = new DialogueProgressStore();
    private final TradeDataStore tradeData = new TradeDataStore();
    private final GachaDataStore gachaData = new GachaDataStore();
    @Nullable
    private String trackedQuestId;
    private boolean trackedQuestDirty;

    private boolean fullDirty;

    public ArcQuestPlayer(UUID ownerUuid) {
        this.ownerUuid = Objects.requireNonNull(ownerUuid);
        this.questState = new ArcQuestQuestState(activeQuests, completedQuests, failedQuests, readPhaseStories, markers);
        this.profileState = new ArcQuestProfileState(variables);
        this.guideState = new ArcQuestGuideState();
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    @Nullable
    public synchronized String getTrackedQuestId() {
        return trackedQuestId;
    }

    public synchronized boolean setTrackedQuestId(@Nullable String questId) {
        String normalized = questId == null || questId.isBlank() ? null : questId;
        if (Objects.equals(trackedQuestId, normalized)) return false;
        trackedQuestId = normalized;
        trackedQuestDirty = true;
        return true;
    }

    private static QuestMarkerData.EntityAttachPoint parseAttachPoint(String value) {
        try {
            return QuestMarkerData.EntityAttachPoint.valueOf(value);
        } catch (Exception ignored) {
            return QuestMarkerData.EntityAttachPoint.HEAD;
        }
    }

    public DialogueProgressStore getDialogueProgress() {
        return dialogueProgress;
    }

    public GachaDataStore getGachaDataStore() {
        return gachaData;
    }

    public TradeDataStore getTradeDataStore() {
        return tradeData;
    }

    public synchronized boolean isGuideUnlocked(ResourceLocation guideId) {
        return guideState.isUnlocked(guideId);
    }

    public synchronized boolean isGuideSeen(ResourceLocation guideId) {
        return guideState.isSeen(guideId);
    }

    public synchronized boolean unlockGuide(ResourceLocation guideId) {
        return guideState.unlock(guideId);
    }

    public synchronized boolean revokeGuideUnlock(ResourceLocation guideId) {
        return guideState.revokeUnlock(guideId);
    }

    public synchronized boolean markGuideSeen(ResourceLocation guideId) {
        return guideState.markSeen(guideId);
    }

    public synchronized boolean clearGuideSeen(ResourceLocation guideId) {
        return guideState.clearSeen(guideId);
    }

    public synchronized Set<ResourceLocation> getUnlockedGuides() {
        return guideState.getUnlockedGuides();
    }

    public synchronized Set<ResourceLocation> getSeenGuides() {
        return guideState.getSeenGuides();
    }

    public synchronized int getGuideProgress(ResourceLocation guideId) {
        return guideState.getProgress(guideId);
    }

    public synchronized boolean setGuideProgress(ResourceLocation guideId, int pageIndex) {
        return guideState.setProgress(guideId, pageIndex);
    }

    public synchronized Map<ResourceLocation, Integer> getAllGuideProgress() {
        return guideState.getAllProgress();
    }

    public synchronized int getGachaDrawCount(String shopId) {
        return gachaData.getDrawCount(shopId);
    }

    public synchronized void incrementGachaDrawCount(String shopId) {
        gachaData.incrementDrawCount(shopId);
    }

    public synchronized void resetGachaDrawCount(String shopId) {
        gachaData.resetDrawCount(shopId);
    }

    public synchronized int getGachaPityCounter(String shopId) {
        return gachaData.getPityCounter(shopId);
    }

    public synchronized void setGachaPityCounter(String shopId, int count) {
        gachaData.setPityCounter(shopId, count);
    }

    public synchronized void addGachaDrawHistory(
            String shopId,
            String itemId,
            String rarityName,
            int actualCount,
            boolean pityTriggered,
            long drawTime) {
        gachaData.addDrawHistory(
                shopId,
                new GachaDrawRecord(itemId, rarityName, actualCount, pityTriggered, drawTime)
        );
    }

    public synchronized List<GachaDrawRecord> getGachaDrawHistory(String shopId) {
        return gachaData.getDrawHistory(shopId);
    }

    public synchronized void clearGachaDrawHistory(String shopId) {
        gachaData.clearDrawHistory(shopId);
    }

    public synchronized void addActiveQuest(QuestRuntimeData data) {
        questState.addActiveQuest(data);
    }

    public synchronized void removeActiveQuest(String questId) {
        questState.removeActiveQuest(questId);
    }

    public synchronized void resetQuest(String questId) {
        questState.resetQuest(questId);
    }

    public synchronized boolean markPhaseStoryRead(String questId, String phaseId) {
        return questState.markPhaseStoryRead(questId, phaseId);
    }

    public synchronized boolean isPhaseStoryRead(String questId, String phaseId) {
        return questState.isPhaseStoryRead(questId, phaseId);
    }

    public synchronized void markCompleted(String questId) {
        questState.markCompleted(questId);
    }

    public synchronized void markFailed(String questId) {
        questState.markFailed(questId);
    }

    @Nullable
    public QuestRuntimeData getActiveQuest(String questId) {
        return questState.getActiveQuest(questId);
    }

    public Map<String, QuestRuntimeData> getAllActiveQuests() {
        return questState.getAllActiveQuests();
    }

    public Set<String> getCompletedQuests() {
        return questState.getCompletedQuests();
    }

    public Set<String> getFailedQuests() {
        return questState.getFailedQuests();
    }

    public boolean isQuestActive(String questId) {
        return questState.isQuestActive(questId);
    }

    public boolean isQuestCompleted(String questId) {
        return questState.isQuestCompleted(questId);
    }

    public boolean isQuestFailed(String questId) {
        return questState.isQuestFailed(questId);
    }

    public boolean isCollectionQuestActive(String questId) {
        QuestRuntimeData data = getActiveQuest(questId);
        return data != null && data.hasCollectionData();
    }

    @Nullable
    public CollectionRuntimeData getCollectionData(String questId) {
        QuestRuntimeData data = getActiveQuest(questId);
        return data != null ? data.getCollectionData() : null;
    }

    public List<String> getCompletedQuestIds() {
        return new ArrayList<>(getCompletedQuests());
    }

    public Set<ResourceLocation> getCompletedQuestLocations() {
        return getCompletedQuests().stream()
                .map(ResourceLocation::parse)
                .collect(Collectors.toUnmodifiableSet());
    }

    public void setFlag(String flag) {
        if (profileState.setFlag(flag)) {
            invalidateAllEnterConditionCaches();
        }
    }

    public boolean hasFlag(String flag) {
        return profileState.hasFlag(flag);
    }

    public void removeFlag(String flag) {
        if (profileState.removeFlag(flag)) {
            invalidateAllEnterConditionCaches();
        }
    }

    public Set<String> getAllFlags() {
        return profileState.getAllFlags();
    }

    public int getVariable(String key) {
        return profileState.getVariable(key);
    }

    public void setVariable(String key, int value) {
        profileState.setVariable(key, value);
        invalidateAllEnterConditionCaches();
    }

    public void incrementVariable(String key, int amount) {
        profileState.incrementVariable(key, amount);
        invalidateAllEnterConditionCaches();
    }

    public Map<String, Integer> getAllVariables() {
        return profileState.getAllVariables();
    }

    public synchronized void upsertMarker(QuestMarkerData marker) {
        questState.upsertMarker(marker);
    }

    public synchronized void removeMarker(String markerId) {
        questState.removeMarker(markerId);
    }

    public synchronized void clearMarkers() {
        questState.clearMarkers();
    }

    public synchronized Map<String, QuestMarkerData> getAllMarkers() {
        return questState.getAllMarkers();
    }

    public CompoundTag serializeNBT() {
        CompoundTag root = new CompoundTag();

        questState.writeToRoot(root);
        profileState.writeToRoot(root);
        guideState.writeToRoot(root);

        root.put("DialogueProgress", dialogueProgress.serialize());
        root.put("TradeData", tradeData.serialize());
        root.put("GachaData", gachaData.serialize());
        if (trackedQuestId != null) root.putString("TrackedQuestId", trackedQuestId);

        VERSION_MANAGER.setInitialVersion(root);
        return root;
    }

    public void deserializeNBT(CompoundTag root) {
        VERSION_MANAGER.migrate(root);

        questState.readFromRoot(root, ArcQuestPlayer::parseAttachPoint);
        profileState.readFromRoot(root);
        guideState.readFromRoot(root);
        trackedQuestId = root.contains("TrackedQuestId", Tag.TAG_STRING)
                ? root.getString("TrackedQuestId")
                : null;
        if (trackedQuestId != null && trackedQuestId.isBlank()) trackedQuestId = null;
        trackedQuestDirty = false;

        if (root.contains("DialogueProgress", Tag.TAG_COMPOUND)) {
            dialogueProgress.deserialize(root.getCompound("DialogueProgress"));
        } else if (root.contains("NodeVisitHistory", Tag.TAG_COMPOUND)) {
            dialogueProgress.migrateFromLegacy(root);
        }

        if (root.contains("TradeData", Tag.TAG_COMPOUND)) {
            tradeData.deserialize(root.getCompound("TradeData"));
        } else {
            tradeData.deserializeLegacy(root);
        }

        if (root.contains("GachaData", Tag.TAG_COMPOUND)) {
            gachaData.deserialize(root.getCompound("GachaData"));
        } else {
            gachaData.deserializeLegacy(root);
        }
    }

    public CompoundTag serializeFlagsVars() {
        CompoundTag tag = new CompoundTag();
        profileState.writeToRoot(tag);
        return tag;
    }

    public DirtyKind getDirtyKind() {
        if (fullDirty) return DirtyKind.FULL;

        DirtyKind kind = DirtyKind.NONE;
        if (profileState.isDirty()) kind = kind.or(DirtyKind.FLAGS_VARS);
        if (questState.isDirty()) kind = kind.or(DirtyKind.QUEST_STATE);
        if (guideState.isDirty()) kind = kind.or(DirtyKind.GUIDE_STATE);
        if (trackedQuestDirty) kind = kind.or(DirtyKind.TRACKED_QUEST);
        if (dialogueProgress.isDirty()) kind = kind.or(DirtyKind.DIALOGUE);
        if (tradeData.isDirty()) kind = kind.or(DirtyKind.TRADE_GACHA);
        if (gachaData.isDirty()) kind = kind.or(DirtyKind.TRADE_GACHA);
        return kind;
    }

    public boolean isDirty() {
        return fullDirty
                || profileState.isDirty()
                || questState.isDirty()
                || guideState.isDirty()
                || trackedQuestDirty
                || dialogueProgress.isDirty()
                || tradeData.isDirty()
                || gachaData.isDirty();
    }

    public void clearDirty(DirtyKind kind) {
        if (kind == DirtyKind.FULL) {
            fullDirty = false;
            profileState.clearDirty();
            questState.clearDirty();
            guideState.clearDirty();
            trackedQuestDirty = false;
            dialogueProgress.clearDirty();
            tradeData.clearDirty();
            gachaData.clearDirty();
            return;
        }
        if (kind == DirtyKind.FLAGS_VARS) profileState.clearDirty();
        if (kind == DirtyKind.QUEST_STATE) questState.clearDirty();
        if (kind == DirtyKind.GUIDE_STATE) guideState.clearDirty();
        if (kind == DirtyKind.TRACKED_QUEST) trackedQuestDirty = false;
        if (kind == DirtyKind.DIALOGUE) dialogueProgress.clearDirty();
        if (kind == DirtyKind.TRADE_GACHA) {
            tradeData.clearDirty();
            gachaData.clearDirty();
        }
    }

    public void clearDirty() {
        fullDirty = false;
        profileState.clearDirty();
        questState.clearDirty();
        guideState.clearDirty();
        trackedQuestDirty = false;
        dialogueProgress.clearDirty();
        tradeData.clearDirty();
        gachaData.clearDirty();
    }

    public void invalidateAllEnterConditionCaches() {
        for (QuestRuntimeData data : activeQuests.values()) {
            data.invalidateEnterConditionCache();
        }
    }

    public void copyFrom(ArcQuestPlayer other) {
        deserializeNBT(other.serializeNBT());
    }

    public void clearAllData() {
        questState.clear();
        profileState.clear();
        guideState.clear();
        trackedQuestId = null;
        trackedQuestDirty = false;
        dialogueProgress.clear();
        tradeData.clear();
        gachaData.clear();
        fullDirty = true;
    }
}
