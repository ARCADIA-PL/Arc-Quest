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
    private final List<String> outcomeIds;
    private final List<CollectionEntryRewardDefinition> rewards;

    public EntryRequirementBinding(String bindingId, ResourceLocation entryId, List<String> objectiveIds,
                                   List<CollectionRecordRequirement> recordRequirements,
                                   CollectionRequirementMode requirementMode, CollectionRecordPolicy recordPolicy,
                                   boolean optional, int sortOrder) {
        this(bindingId, entryId, objectiveIds, recordRequirements, requirementMode, recordPolicy, optional, sortOrder, List.of(), List.of());
    }

    public EntryRequirementBinding(String bindingId, ResourceLocation entryId, List<String> objectiveIds,
                                   List<CollectionRecordRequirement> recordRequirements,
                                   CollectionRequirementMode requirementMode, CollectionRecordPolicy recordPolicy,
                                   boolean optional, int sortOrder, List<String> outcomeIds,
                                   List<CollectionEntryRewardDefinition> rewards) {
        if (bindingId == null || bindingId.isBlank()) throw new IllegalArgumentException("bindingId is required");
        this.bindingId = bindingId.trim();
        this.entryId = Objects.requireNonNull(entryId, "binding entryId");
        this.objectiveIds = List.copyOf(objectiveIds == null ? List.of() : objectiveIds);
        this.recordRequirements = List.copyOf(recordRequirements == null ? List.of() : recordRequirements);
        this.requirementMode = Objects.requireNonNullElse(requirementMode, CollectionRequirementMode.ALL);
        this.recordPolicy = Objects.requireNonNullElse(recordPolicy, CollectionRecordPolicy.EXISTING_RECORDS);
        this.optional = optional;
        this.sortOrder = sortOrder;
        this.outcomeIds = List.copyOf(outcomeIds == null ? List.of() : outcomeIds);
        this.rewards = List.copyOf(rewards == null ? List.of() : rewards);
        HashSet<String> outcomeKeys = new HashSet<>();
        for (String id : this.outcomeIds)
            if (id == null || id.isBlank() || id.length() > 128 || !id.equals(id.trim()) || !outcomeKeys.add(id))
                throw new IllegalArgumentException("Binding requires unique stable outcomeIds");
        HashSet<String> rewardIds = new HashSet<>();
        for (CollectionEntryRewardDefinition reward : this.rewards) {
            if (reward.trigger() != CollectionEntryRewardTrigger.BINDING_COMPLETE)
                throw new IllegalArgumentException("Binding rewards require BINDING_COMPLETE trigger");
            if (!rewardIds.add(reward.rewardId())) throw new IllegalArgumentException("Duplicate binding rewardId: " + reward.rewardId());
        }
        if (!this.outcomeIds.isEmpty() && (this.objectiveIds.isEmpty() || this.requirementMode != CollectionRequirementMode.ALL
                || this.recordRequirements.stream().noneMatch(r -> r.type() == CollectionRecordRequirement.Type.DISCOVERED)))
            throw new IllegalArgumentException("Outcome sources require run objectives, ALL mode and a DISCOVERED prerequisite");
        for (var required : this.recordRequirements)
            if (required.type() == CollectionRecordRequirement.Type.OUTCOME && this.outcomeIds.contains(required.stepId()))
                throw new IllegalArgumentException("Outcome source cannot require its own outcome: " + required.stepId());
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
    public List<String> getOutcomeIds() { return outcomeIds; }
    public List<CollectionEntryRewardDefinition> getRewards() { return rewards; }
}
