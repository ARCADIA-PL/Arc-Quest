package org.arcadia.arc_quest.quest.logic.profile.collection;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.CountingMode;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;

import java.util.Objects;

public final class CollectionObjectiveBinding {

    private final String questId;
    private final String phaseId;
    private final ObjectiveEntry objectiveEntry;
    private final CountingMode countingMode;

    public CollectionObjectiveBinding(String questId,
                                      String phaseId,
                                      ObjectiveEntry objectiveEntry,
                                      CountingMode countingMode) {
        this.questId = questId == null ? "" : questId;
        this.phaseId = phaseId == null ? "" : phaseId;
        this.objectiveEntry = Objects.requireNonNull(objectiveEntry);
        this.countingMode = countingMode != null ? countingMode : CountingMode.BINARY;
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

    public ResourceLocation getTargetId() {
        return objectiveEntry.getTargetId();
    }
}
