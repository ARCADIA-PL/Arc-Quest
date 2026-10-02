package org.arcadia.arc_quest.quest.data;

import java.util.List;

/** One shared completion projection for journal, tracker and server phase progression. */
public record CollectionSheetProgress(int completed, int target, int candidateTotal, boolean complete,
                                      List<CollectionBindingProgress> bindings,
                                      List<CollectionCategoryProgress> categories) {
    public static final CollectionSheetProgress EMPTY = new CollectionSheetProgress(0, 0, 0, false, List.of(), List.of());
    public CollectionSheetProgress {
        bindings = List.copyOf(bindings);
        categories = List.copyOf(categories);
    }
    public CollectionBindingProgress binding(String id) {
        return bindings.stream().filter(binding -> binding.bindingId().equals(id)).findFirst().orElse(null);
    }
}
