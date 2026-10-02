package org.arcadia.arc_quest.quest.api;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A specimen sheet belonging to one actual phase; entries are never phases themselves. */
public final class CollectionSheetDefinition {
    private final List<EntryRequirementBinding> bindings;
    private final Map<String, EntryRequirementBinding> bindingsById;
    private final CollectionSheetCompletionPolicy completionPolicy;
    private final int requiredCount;
    private final boolean countDistinctEntries;

    public CollectionSheetDefinition(List<EntryRequirementBinding> bindings,
                                     CollectionSheetCompletionPolicy completionPolicy,
                                     int requiredCount, boolean countDistinctEntries) {
        this.bindings = List.copyOf(Objects.requireNonNull(bindings, "sheet bindings"));
        if (this.bindings.isEmpty()) throw new IllegalArgumentException("Collection sheet needs bindings");
        this.completionPolicy = Objects.requireNonNullElse(completionPolicy, CollectionSheetCompletionPolicy.ALL);
        this.countDistinctEntries = countDistinctEntries;
        LinkedHashMap<String, EntryRequirementBinding> byId = new LinkedHashMap<>();
        for (EntryRequirementBinding binding : this.bindings) {
            if (byId.putIfAbsent(binding.getBindingId(), binding) != null) {
                throw new IllegalArgumentException("Duplicate bindingId: " + binding.getBindingId());
            }
        }
        this.bindingsById = Map.copyOf(byId);
        long candidateCount = this.bindings.stream().filter(b -> !b.isOptional())
                .map(b -> countDistinctEntries ? b.getEntryId().toString() : b.getBindingId()).distinct().count();
        if (candidateCount == 0) throw new IllegalArgumentException("Collection sheet needs a required binding");
        if (this.completionPolicy == CollectionSheetCompletionPolicy.QUOTA) {
            if (requiredCount < 1 || requiredCount > candidateCount) {
                throw new IllegalArgumentException("Collection QUOTA requiredCount must be between 1 and " + candidateCount);
            }
            this.requiredCount = requiredCount;
        } else {
            if (requiredCount != 0) throw new IllegalArgumentException("ALL sheet must not specify requiredCount");
            this.requiredCount = (int) candidateCount;
        }
    }

    public List<EntryRequirementBinding> getBindings() { return bindings; }
    @Nullable public EntryRequirementBinding getBinding(String bindingId) { return bindingsById.get(bindingId); }
    public CollectionSheetCompletionPolicy getCompletionPolicy() { return completionPolicy; }
    /** Effective player-facing completion threshold, including ALL sheets. */
    public int getRequiredCount() { return requiredCount; }
    public boolean isCountDistinctEntries() { return countDistinctEntries; }
}
