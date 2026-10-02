package org.arcadia.arc_quest.quest.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.LinkedHashMap;
import java.util.Map;

/** Last terminal task run for review; permanent entry facts live in CollectionRecordState. */
public final class CollectionQuestArchives {
    public static final String ROOT_KEY = "CollectionQuestArchives";
    private final Map<String, QuestRuntimeData> runs = new LinkedHashMap<>();
    public void capture(QuestRuntimeData runtime) {
        if (runtime != null && runtime.hasCollectionData()) runs.put(runtime.getQuestId(), runtime.copy());
    }
    public QuestRuntimeData get(String questId) { return runs.get(questId); }
    public void remove(String questId) { runs.remove(questId); }
    public void clear() { runs.clear(); }
    public void writeToRoot(CompoundTag root) {
        ListTag list = new ListTag();
        runs.values().forEach(run -> list.add(run.serializeNBT()));
        root.put(ROOT_KEY, list);
    }
    public void readFromRoot(CompoundTag root) {
        runs.clear();
        ListTag list = root.getList(ROOT_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            QuestRuntimeData run = QuestRuntimeData.deserializeNBT(list.getCompound(i));
            if (run.hasCollectionData()) runs.put(run.getQuestId(), run);
        }
    }
    public void copyFrom(CollectionQuestArchives source) {
        runs.clear();
        source.runs.forEach((id, run) -> runs.put(id, run.copy()));
    }
}
