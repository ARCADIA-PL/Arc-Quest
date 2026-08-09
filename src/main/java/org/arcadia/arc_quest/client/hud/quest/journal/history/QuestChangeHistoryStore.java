package org.arcadia.arc_quest.client.hud.quest.journal.history;

import org.arcadia.arc_quest.quest.network.ClientQuestCache;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class QuestChangeHistoryStore {
    public static final QuestChangeHistoryStore INSTANCE = new QuestChangeHistoryStore();

    private static final long DEDUP_WINDOW_MS = 1200L;
    private final Object lock = new Object();
    private final Object persistenceLock = new Object();
    private final List<QuestChangeHistoryEntry> entries = new ArrayList<>();
    private final Map<String, Long> recentKeys = new HashMap<>();
    private boolean loaded = false;
    private boolean saveQueued = false;
    private boolean saveDirty = false;
    private int maxEntries = 500;
    private Path loadedFile;
    private volatile long sessionGeneration = 0L;

    private QuestChangeHistoryStore() {
    }

    public void ensureLoaded() {
        Path currentFile = QuestChangeHistoryPersistence.filePath();
        synchronized (lock) {
            if (loaded && Objects.equals(loadedFile, currentFile)) return;
            if (loaded) {
                long previousGeneration = sessionGeneration;
                QuestChangeHistoryPersistence.HistoryFile previousSnapshot = snapshotLocked();
                Path previousFile = loadedFile;
                sessionGeneration++;
                synchronized (persistenceLock) {
                    if (previousGeneration < sessionGeneration) {
                        QuestChangeHistoryPersistence.save(previousFile, previousSnapshot);
                    }
                }
            }
            QuestChangeHistoryPersistence.HistoryFile file = QuestChangeHistoryPersistence.load(currentFile);
            maxEntries = Math.max(1, file.maxEntries);
            entries.clear();
            recentKeys.clear();
            if (file.entries != null) entries.addAll(file.entries);
            entries.sort(Comparator.comparingLong((QuestChangeHistoryEntry e) -> e.timeMs).reversed().thenComparingInt(e -> e.sortPriority));
            trimLocked();
            loadedFile = currentFile;
            saveQueued = false;
            saveDirty = false;
            loaded = true;
        }
    }

    public void flush() {
        ensureLoaded();
        long generation;
        Path file;
        QuestChangeHistoryPersistence.HistoryFile snapshot;
        synchronized (lock) {
            generation = sessionGeneration;
            file = loadedFile;
            snapshot = snapshotLocked();
        }
        saveSnapshot(generation, file, snapshot);
    }

    public void flushAndResetClientSession() {
        Path file;
        QuestChangeHistoryPersistence.HistoryFile snapshot;
        synchronized (lock) {
            file = loadedFile;
            snapshot = loaded ? snapshotLocked() : null;
            sessionGeneration++;
            loaded = false;
            loadedFile = null;
            entries.clear();
            recentKeys.clear();
            saveQueued = false;
            saveDirty = false;
            maxEntries = 500;
        }
        if (snapshot != null) {
            synchronized (persistenceLock) {
                QuestChangeHistoryPersistence.save(file, snapshot);
            }
        }
    }

    public void clear() {
        synchronized (lock) {
            ensureLoaded();
            entries.clear();
            recentKeys.clear();
        }
        queueSave();
    }

    public List<QuestChangeHistoryEntry> query(QuestChangeHistoryFilters filters) {
        ensureLoaded();
        List<QuestChangeHistoryEntry> result = new ArrayList<>();
        int limit = filters != null && filters.limit > 0 ? filters.limit : 200;
        synchronized (lock) {
            for (QuestChangeHistoryEntry entry : entries) {
                if (filters == null || filters.matches(entry)) {
                    result.add(entry);
                    if (result.size() >= limit) break;
                }
            }
        }
        return result;
    }

    public void add(QuestChangeHistoryEntry entry) {
        if (entry == null || entry.type == null) return;
        ensureLoaded();
        long now = System.currentTimeMillis();
        if (entry.timeMs <= 0L) entry.timeMs = now;
        if (entry.category == null) entry.category = entry.type.category();
        if (entry.themeColor == 0) entry.themeColor = entry.type.accentColor();
        if (entry.id == null || entry.id.isEmpty()) entry.id = buildId(entry);
        String key = entry.dedupeKey();
        synchronized (lock) {
            Long last = recentKeys.get(key);
            if (last != null && now - last < DEDUP_WINDOW_MS) return;
            recentKeys.put(key, now);
            recentKeys.entrySet().removeIf(e -> now - e.getValue() > 5000L);
            entries.add(0, entry);
            trimLocked();
        }
        queueSave();
    }

    public void recordQuestAccepted(String questId) {
        add(base(QuestChangeHistoryType.QUEST_ACCEPTED, questId, "", "", "", "Quest Accepted", questName(questId)));
    }

    public void recordQuestCompleted(String questId) {
        add(base(QuestChangeHistoryType.QUEST_COMPLETED, questId, "", "", "", "Quest Completed", questName(questId)));
    }

    public void recordQuestFailed(String questId) {
        add(base(QuestChangeHistoryType.QUEST_FAILED, questId, "", "", "", "Quest Failed", questName(questId)));
    }

    public void recordQuestAbandoned(String questId) {
        add(base(QuestChangeHistoryType.QUEST_ABANDONED, questId, "", "", "", "Quest Abandoned", questName(questId)));
    }

    public void recordPhaseAdded(String questId, String phaseId) {
        add(base(QuestChangeHistoryType.PHASE_ADDED, questId, phaseId, "", "", "Phase Added", phaseName(questId, phaseId)));
    }

    public void recordPhaseSwitched(String questId, String oldPhaseId, String newPhaseId) {
        QuestChangeHistoryEntry entry = base(QuestChangeHistoryType.PHASE_SWITCHED, questId, newPhaseId, "", "", "Phase Switched", phaseName(questId, newPhaseId));
        entry.beforeValue = phaseName(questId, oldPhaseId);
        entry.afterValue = phaseName(questId, newPhaseId);
        add(entry);
    }

    public void recordPhaseAdvanced(String questId, String oldPhaseId, String newPhaseId) {
        QuestChangeHistoryEntry entry = base(QuestChangeHistoryType.PHASE_ADVANCED, questId, newPhaseId, "", "", "Phase Advanced", phaseName(questId, oldPhaseId) + " -> " + phaseName(questId, newPhaseId));
        entry.beforeValue = oldPhaseId == null ? "" : oldPhaseId;
        entry.afterValue = newPhaseId == null ? "" : newPhaseId;
        add(entry);
    }

    public void recordPhaseCompleted(String questId, String phaseId) {
        add(base(QuestChangeHistoryType.PHASE_COMPLETED, questId, phaseId, "", "", "Phase Completed", phaseName(questId, phaseId)));
    }

    public void recordObjectiveProgress(String questId, String phaseId, int index, int oldValue, int newValue, int required) {
        QuestChangeHistoryEntry entry = base(QuestChangeHistoryType.OBJECTIVE_PROGRESS, questId, phaseId, "objective_" + index, "", "Objective Progress", QuestChangeHistoryFormatter.objectiveName(questId, phaseId, index));
        entry.beforeValue = formatProgress(oldValue, required);
        entry.afterValue = formatProgress(newValue, required);
        add(entry);
    }

    public void recordObjectiveCompleted(String questId, String phaseId, int index, int required) {
        QuestChangeHistoryEntry entry = base(QuestChangeHistoryType.OBJECTIVE_COMPLETED, questId, phaseId, "objective_" + index, "", "Objective Completed", QuestChangeHistoryFormatter.objectiveName(questId, phaseId, index));
        entry.afterValue = formatProgress(required, required);
        add(entry);
    }

    public void recordCollectionEntryDiscovered(String questId, String phaseId) {
        add(base(QuestChangeHistoryType.COLLECTION_ENTRY_DISCOVERED, questId, phaseId, "", "", "Entry Discovered", phaseName(questId, phaseId)));
    }

    public void recordCollectionEntryCompleted(String questId, String phaseId) {
        add(base(QuestChangeHistoryType.COLLECTION_ENTRY_COMPLETED, questId, phaseId, "", "", "Entry Completed", phaseName(questId, phaseId)));
    }

    public void recordCollectionCategoryCompleted(String questId, String categoryId) {
        QuestChangeHistoryEntry entry = base(QuestChangeHistoryType.COLLECTION_CATEGORY_COMPLETED, questId, "", "", "", "Category Completed", categoryId);
        entry.categoryId = categoryId == null ? "" : categoryId;
        add(entry);
    }

    public void recordCollectionQuestCompleted(String questId) {
        add(base(QuestChangeHistoryType.COLLECTION_QUEST_COMPLETED, questId, "", "", "", "Collection Completed", questName(questId)));
    }

    public void recordCollectionRewardUnlocked(String questId, String rewardId) {
        add(base(QuestChangeHistoryType.COLLECTION_REWARD_UNLOCKED, questId, "", "", rewardId, "Reward Unlocked", rewardId));
    }

    public void recordCollectionRewardClaimed(String questId, String rewardId) {
        add(base(QuestChangeHistoryType.COLLECTION_REWARD_CLAIMED, questId, "", "", rewardId, "Reward Claimed", rewardId));
    }

    private QuestChangeHistoryEntry base(QuestChangeHistoryType type, String questId, String phaseId, String objectiveId, String rewardId, String title, String detail) {
        QuestChangeHistoryEntry entry = new QuestChangeHistoryEntry();
        entry.timeMs = System.currentTimeMillis();
        entry.type = type;
        entry.category = type.category();
        entry.questId = questId == null ? "" : questId;
        entry.questName = questName(entry.questId);
        entry.phaseId = phaseId == null ? "" : phaseId;
        entry.phaseName = phaseName(entry.questId, entry.phaseId);
        entry.objectiveId = objectiveId == null ? "" : objectiveId;
        entry.rewardId = rewardId == null ? "" : rewardId;
        entry.title = title == null || title.isEmpty() ? type.displayName() : title;
        entry.detail = detail == null ? "" : detail;
        entry.themeColor = ClientQuestCache.INSTANCE.getQuestThemeColor(entry.questId, type.accentColor());
        return entry;
    }

    private String questName(String questId) {
        return QuestChangeHistoryFormatter.questName(questId);
    }

    private String phaseName(String questId, String phaseId) {
        return QuestChangeHistoryFormatter.phaseName(questId, phaseId);
    }

    private String formatProgress(int value, int required) {
        return required > 0 ? Math.max(0, value) + "/" + required : String.valueOf(Math.max(0, value));
    }

    private String buildId(QuestChangeHistoryEntry entry) {
        return entry.timeMs + "|" + entry.type.name() + "|" + entry.questId + "|" + entry.phaseId + "|" + entry.objectiveId + "|" + entry.rewardId + "|" + entry.afterValue;
    }

    private void trimLocked() {
        while (entries.size() > maxEntries) entries.remove(entries.size() - 1);
    }

    private void queueSave() {
        long generation;
        synchronized (lock) {
            saveDirty = true;
            if (saveQueued) return;
            saveQueued = true;
            generation = sessionGeneration;
        }
        CompletableFuture.runAsync(() -> runSaveLoop(generation));
    }

    private void runSaveLoop(long generation) {
        while (true) {
            try {
                Thread.sleep(500L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
            Path file;
            QuestChangeHistoryPersistence.HistoryFile snapshot;
            synchronized (lock) {
                if (generation != sessionGeneration) return;
                saveDirty = false;
                file = loadedFile;
                snapshot = snapshotLocked();
            }
            saveSnapshot(generation, file, snapshot);
            synchronized (lock) {
                if (generation != sessionGeneration) return;
                if (!saveDirty) {
                    saveQueued = false;
                    return;
                }
            }
        }
    }

    private void saveSnapshot(long generation, Path file,
                              QuestChangeHistoryPersistence.HistoryFile snapshot) {
        synchronized (persistenceLock) {
            if (generation != sessionGeneration) return;
            QuestChangeHistoryPersistence.save(file, snapshot);
        }
    }

    private QuestChangeHistoryPersistence.HistoryFile snapshotLocked() {
        QuestChangeHistoryPersistence.HistoryFile file = new QuestChangeHistoryPersistence.HistoryFile();
        file.maxEntries = maxEntries;
        file.entries = new ArrayList<>(entries);
        return file;
    }

}
