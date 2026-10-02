package org.arcadia.arc_quest.quest.builder;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.*;

import java.util.ArrayList;
import java.util.List;

/** The entry ID is reusable; the binding ID remains stable within this real phase. */
public final class EntryRequirementBuilder {
    private final String bindingId;
    private final ResourceLocation entryId;
    private final List<String> objectiveIds = new ArrayList<>();
    private final List<CollectionRecordRequirement> recordRequirements = new ArrayList<>();
    private CollectionRequirementMode mode = CollectionRequirementMode.ALL;
    private CollectionRecordPolicy policy = CollectionRecordPolicy.EXISTING_RECORDS;
    private boolean optional;
    private int sortOrder;

    private EntryRequirementBuilder(String bindingId, ResourceLocation entryId) { this.bindingId = bindingId; this.entryId = entryId; }
    public static EntryRequirementBuilder create(String bindingId, ResourceLocation entryId) { return new EntryRequirementBuilder(bindingId, entryId); }
    public static EntryRequirementBuilder create(String bindingId, String entryId) { return create(bindingId, ResourceLocation.parse(entryId)); }
    public EntryRequirementBuilder objective(String objectiveId) { objectiveIds.add(objectiveId); return this; }
    public EntryRequirementBuilder objectives(String... ids) { objectiveIds.addAll(List.of(ids)); return this; }
    public EntryRequirementBuilder discovered() { recordRequirements.add(CollectionRecordRequirement.discovered()); return this; }
    public EntryRequirementBuilder researched() { recordRequirements.add(CollectionRecordRequirement.researched()); return this; }
    public EntryRequirementBuilder researchStep(String stepId) { recordRequirements.add(CollectionRecordRequirement.researchStep(stepId)); return this; }
    public EntryRequirementBuilder requirementMode(CollectionRequirementMode mode) { this.mode = mode; return this; }
    public EntryRequirementBuilder recordPolicy(CollectionRecordPolicy policy) { this.policy = policy; return this; }
    public EntryRequirementBuilder optional() { optional = true; return this; }
    public EntryRequirementBuilder sortOrder(int order) { sortOrder = order; return this; }
    public EntryRequirementBinding build() { return new EntryRequirementBinding(bindingId, entryId, objectiveIds, recordRequirements, mode, policy, optional, sortOrder); }
}
