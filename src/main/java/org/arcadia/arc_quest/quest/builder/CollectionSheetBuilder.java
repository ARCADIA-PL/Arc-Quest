package org.arcadia.arc_quest.quest.builder;

import org.arcadia.arc_quest.quest.api.*;
import java.util.ArrayList;
import java.util.List;

public final class CollectionSheetBuilder {
    private final List<EntryRequirementBinding> bindings = new ArrayList<>();
    private CollectionSheetCompletionPolicy policy = CollectionSheetCompletionPolicy.ALL;
    private int quota;
    private boolean distinct;
    private CollectionSheetBuilder() {}
    public static CollectionSheetBuilder create() { return new CollectionSheetBuilder(); }
    public CollectionSheetBuilder binding(EntryRequirementBinding binding) { bindings.add(binding); return this; }
    public CollectionSheetBuilder binding(EntryRequirementBuilder binding) { return binding(binding.build()); }
    public CollectionSheetBuilder all() { policy = CollectionSheetCompletionPolicy.ALL; quota = 0; return this; }
    public CollectionSheetBuilder quota(int quota) { policy = CollectionSheetCompletionPolicy.QUOTA; this.quota = quota; return this; }
    public CollectionSheetBuilder countDistinctEntries(boolean distinct) { this.distinct = distinct; return this; }
    public CollectionSheetDefinition build() { return new CollectionSheetDefinition(bindings, policy, quota, distinct); }
}
