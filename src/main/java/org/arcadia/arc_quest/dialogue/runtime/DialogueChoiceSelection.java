package org.arcadia.arc_quest.dialogue.runtime;

import org.arcadia.arc_quest.dialogue.api.DialogueChoice;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** 以位置保留选项身份，相等的选项也有独立的冷却和一次性记录。 */
record DialogueChoiceSelection(List<DialogueChoice> choices, int[] originalIndices) {
    static DialogueChoiceSelection select(List<DialogueChoice> all, Predicate<DialogueChoice> visible,
                                          BooleanSupplier canContinue) {
        List<DialogueChoice> choices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        int highestPriority = Integer.MIN_VALUE;
        for (int index = 0; index < all.size(); index++) {
            if (!canContinue.getAsBoolean()) break;
            DialogueChoice choice = all.get(index);
            if (!visible.test(choice)) continue;
            if (choice.priority() < highestPriority) continue;
            if (choice.priority() > highestPriority) {
                choices.clear();
                indices.clear();
                highestPriority = choice.priority();
            }
            choices.add(choice);
            indices.add(index);
        }
        return new DialogueChoiceSelection(List.copyOf(choices), indices.stream().mapToInt(Integer::intValue).toArray());
    }
}
