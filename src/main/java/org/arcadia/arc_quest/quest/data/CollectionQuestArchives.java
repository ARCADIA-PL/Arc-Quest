package org.arcadia.arc_quest.quest.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Last terminal run for review plus earlier runs with unpaid entry rewards. */
public final class CollectionQuestArchives {
    public static final String ROOT_KEY = "CollectionQuestArchives";
    private final Map<String, QuestRuntimeData> runs = new LinkedHashMap<>();
    private final Map<String, LinkedHashMap<String, QuestRuntimeData>> unpaidRuns = new LinkedHashMap<>();
    public void capture(QuestRuntimeData runtime) {
        if (runtime == null || !runtime.hasCollectionData()) return;
        String questId = runtime.getQuestId(), runId = runtime.getCollectionData().getRunId();
        QuestRuntimeData previous = runs.get(questId);
        if (previous != null && !previous.getCollectionData().getRunId().equals(runId)
                && previous.getCollectionData().hasPendingEntryRewards()) {
            unpaidRuns.computeIfAbsent(questId, ignored -> new LinkedHashMap<>())
                    .put(previous.getCollectionData().getRunId(), previous);
        }
        runs.put(questId, runtime.copy());
        Map<String, QuestRuntimeData> unpaid = unpaidRuns.get(questId);
        if (unpaid != null) unpaid.remove(runId);
        pruneSettledPending(questId);
    }
    public QuestRuntimeData get(String questId) { return runs.get(questId); }
    public QuestRuntimeData get(String questId, String runId) {
        QuestRuntimeData latest = runs.get(questId);
        if (latest != null && latest.getCollectionData().getRunId().equals(runId)) return latest;
        Map<String, QuestRuntimeData> unpaid = unpaidRuns.get(questId);
        return unpaid == null ? null : unpaid.get(runId);
    }
    public List<QuestRuntimeData> allRuns(String questId) {
        List<QuestRuntimeData> result = new ArrayList<>();
        Map<String, QuestRuntimeData> unpaid = unpaidRuns.get(questId);
        if (unpaid != null) result.addAll(unpaid.values());
        QuestRuntimeData latest = runs.get(questId);
        if (latest != null) result.add(latest);
        return List.copyOf(result);
    }
    public List<QuestRuntimeData> pendingRuns(String questId) {
        return allRuns(questId).stream().filter(run -> run.getCollectionData().hasPendingEntryRewards()).toList();
    }
    public void pruneSettledPending(String questId) {
        Map<String, QuestRuntimeData> unpaid = unpaidRuns.get(questId);
        if (unpaid == null) return;
        unpaid.values().removeIf(run -> !run.getCollectionData().hasPendingEntryRewards());
        if (unpaid.isEmpty()) unpaidRuns.remove(questId);
    }
    public void remove(String questId) { runs.remove(questId); unpaidRuns.remove(questId); }
    public void clear() { runs.clear(); unpaidRuns.clear(); }
    public void writeToRoot(CompoundTag root) {
        ListTag list = new ListTag();
        // Earlier unpaid runs precede each latest run so legacy list readers remain last-run compatible.
        unpaidRuns.values().forEach(questRuns -> questRuns.values().forEach(run -> list.add(run.serializeNBT())));
        runs.values().forEach(run -> list.add(run.serializeNBT()));
        root.put(ROOT_KEY, list);
    }
    public void readFromRoot(CompoundTag root) {
        clear();
        ListTag list = root.getList(ROOT_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            QuestRuntimeData run = QuestRuntimeData.deserializeNBT(list.getCompound(i));
            capture(run);
        }
    }
    public void copyFrom(CollectionQuestArchives source) {
        clear();
        source.runs.forEach((id, run) -> runs.put(id, run.copy()));
        source.unpaidRuns.forEach((questId, archived) -> {
            var copied = new LinkedHashMap<String, QuestRuntimeData>();
            archived.forEach((runId, run) -> copied.put(runId, run.copy()));
            unpaidRuns.put(questId, copied);
        });
    }
}
