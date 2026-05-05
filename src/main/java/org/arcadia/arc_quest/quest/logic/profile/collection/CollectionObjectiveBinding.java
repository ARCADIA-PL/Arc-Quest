package org.arcadia.arc_quest.quest.logic.profile.collection;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.CollectionEntryConfig;
import org.arcadia.arc_quest.quest.api.CountingMode;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;

import java.util.Objects;

public final class CollectionObjectiveBinding {

    private final String questId;
    private final String phaseId;
    private final ObjectiveEntry objectiveEntry;
    private final CountingMode countingMode;
    private final boolean repeatableProgress;
    private final boolean repeatableCompletion;

    public CollectionObjectiveBinding(String questId,
                                      String phaseId,
                                      ObjectiveEntry objectiveEntry,
                                      CollectionEntryConfig entryConfig) {
        this.questId = questId == null ? "" : questId;
        this.phaseId = phaseId == null ? "" : phaseId;
        this.objectiveEntry = Objects.requireNonNull(objectiveEntry);
        this.countingMode = entryConfig != null && entryConfig.getCountingMode() != null ? entryConfig.getCountingMode() : CountingMode.BINARY;
        this.repeatableProgress = entryConfig != null && entryConfig.isRepeatableProgress();
        this.repeatableCompletion = entryConfig != null && entryConfig.isRepeatableCompletion();
    }

    public String getQuestId() {
        return questId;
    }

    public String getPhaseId() {
        return phaseId;
    }

    public ObjectiveEntry getObjectiveEntry() {
        return objectiveEntry;
    }

    public CountingMode getCountingMode() {
        return countingMode;
    }

    public boolean isRepeatableProgress() {
        return repeatableProgress;
    }

    public boolean isRepeatableCompletion() {
        return repeatableCompletion;
    }

    public ResourceLocation getTargetId() {
        return objectiveEntry.getTargetId();
    }
}
