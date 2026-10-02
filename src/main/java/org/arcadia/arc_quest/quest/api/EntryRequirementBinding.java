package org.arcadia.arc_quest.quest.api;

import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Stable link from one real phase to a reusable knowledge entry and run/record requirements. */
public final class EntryRequirementBinding {
    private final String bindingId;
    private final ResourceLocation entryId;
    private final List<String> objectiveIds;
    private final List<CollectionRecordRequirement> recordRequirements;
    private final CollectionRequirementMode requirementMode;
    private final CollectionRecordPolicy recordPolicy;
    private final boolean optional;
    private final int sortOrder;

    public EntryRequirementBinding(String bindingId, ResourceLocation entryId, List<String> objectiveIds,
                                   List<CollectionRecordRequirement> recordRequirements,
                                   CollectionRequirementMode requirementMode, CollectionRecordPolicy recordPolicy,
                                   boolean optional, int sortOrder) {
        if (bindingId == null || bindingId.isBlank()) throw new IllegalArgumentException("bindingId is required");
        this.bindingId = bindingId.trim();
        this.entryId = Objects.requireNonNull(entryId, "binding entryId");
        this.objectiveIds = List.copyOf(objectiveIds == null ? List.of() : objectiveIds);
        this.recordRequirements = List.copyOf(recordRequirements == null ? List.of() : recordRequirements);
        this.requirementMode = Objects.requireNonNullElse(requirementMode, CollectionRequirementMode.ALL);
        this.recordPolicy = Objects.requireNonNullElse(recordPolicy, CollectionRecordPolicy.EXISTING_RECORDS);
        this.optional = optional;
        this.sortOrder = sortOrder;
        if (this.objectiveIds.isEmpty() && this.recordRequirements.isEmpty()) {
            throw new IllegalArgumentException("Binding '" + bindingId + "' needs at least one requirement");
        }
        HashSet<String> objectiveKeys = new HashSet<>();
        for (String objectiveId : this.objectiveIds) {
            if (objectiveId.isBlank() || !objectiveKeys.add(objectiveId)) {
                throw new IllegalArgumentException("Binding '" + bindingId + "' has blank/duplicate objectiveId");
            }
        }
        if (new HashSet<>(this.recordRequirements).size() != this.recordRequirements.size()) {
            throw new IllegalArgumentException("Binding '" + bindingId + "' has duplicate record requirements");
        }
        if (this.recordPolicy == CollectionRecordPolicy.NEW_DISCOVERIES
                && (this.recordRequirements.isEmpty() || this.recordRequirements.stream()
                .anyMatch(r -> r.type() != CollectionRecordRequirement.Type.DISCOVERED))) {
            throw new IllegalArgumentException("NEW_DISCOVERIES requires a DISCOVERED record requirement only");
        }
    }

    public String getBindingId() { return bindingId; }
    public ResourceLocation getEntryId() { return entryId; }
    public List<String> getObjectiveIds() { return objectiveIds; }
    public List<CollectionRecordRequirement> getRecordRequirements() { return recordRequirements; }
    public CollectionRequirementMode getRequirementMode() { return requirementMode; }
    public CollectionRecordPolicy getRecordPolicy() { return recordPolicy; }
    public boolean isOptional() { return optional; }
    public int getSortOrder() { return sortOrder; }
}
