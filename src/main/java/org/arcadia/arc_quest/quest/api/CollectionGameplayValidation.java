package org.arcadia.arc_quest.quest.api;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.arcadia.arc_quest.quest.api.rule.collection.AllEntriesCompleteRule;
import org.arcadia.arc_quest.quest.api.rule.collection.CompletedEntryCountRule;
import org.arcadia.arc_quest.quest.api.rule.collection.CompletedEntryRatioRule;

/** Authoring safeguards for new investigations; old research definitions retain their published semantics. */
public final class CollectionGameplayValidation {
    private CollectionGameplayValidation() { }

    public static void validate(QuestDefinition quest) {
        if (!quest.hasCollectionSheets()) return;
        var config = quest.getCollectionConfig();
        for (PhaseDefinition phase : quest.getAllPhases()) {
            if (!phase.hasCollectionSheet()) continue;
            Map<String, String> consumptionOwner = new HashMap<>();
            Set<String> unifiedConsumption = new HashSet<>();
            for (EntryRequirementBinding binding : phase.getCollectionSheet().getBindings()) {
                var entry = config.getEntry(binding.getEntryId());
                boolean unified = entry != null && entry.isUnifiedGameplay();
                for (String id : binding.getObjectiveIds()) {
                    var objective = phase.getObjective(id);
                    if (objective != null && consumes(objective.getType())) {
                        String previous = consumptionOwner.putIfAbsent(id, binding.getBindingId());
                        if (previous != null && (unified || unifiedConsumption.contains(id))) throw new IllegalArgumentException("Consumed objective '" + id
                                + "' cannot fund multiple bindings '" + previous + "' and '" + binding.getBindingId()
                                + "'. Define independent submitted quantities for each investigation.");
                        if (unified) unifiedConsumption.add(id);
                    }
                }
                if (!unified) continue;
                boolean earlyPaid = !binding.getRewards().isEmpty() || !phase.getPhaseRewards().isEmpty();
                boolean paymentCanReplay = quest.isRepeatable()
                        && (earlyPaid || !binding.isOptional() && !quest.getCompletionRewards().isEmpty());
                if (paymentCanReplay && config.getRepeatCooldownTicks() <= 0
                        && !guaranteesFreshFulfillment(phase, binding)) {
                    throw new IllegalArgumentException("Replayable paid binding '" + binding.getBindingId()
                            + "' requires unavoidable fresh fulfillment or repeatCooldownTicks. Holdings, location polling,"
                            + " generic interaction, crafting and legacy acquisition alone can be replayed by repeating the quest.");
                }
            }
        }
        if (config.getRepeatCooldownTicks() <= 0 && quest.isRepeatable()
                && config.getEntries().stream().anyMatch(CollectionEntryDefinition::isUnifiedGameplay)) {
            for (var node : config.getQuestRewardNodes()) validateMilestone(quest, node, null);
            for (var category : config.getCategories()) for (var node : category.getRewardNodes())
                validateMilestone(quest, node, category.getCategoryId());
        }
    }

    public static boolean consumes(ObjectiveType type) {
        return ObjectiveType.OFFER.equals(type) || ObjectiveType.DELIVER.equals(type);
    }

    private static boolean freshFulfillment(ObjectiveEntry objective) {
        ObjectiveType type = objective.getType();
        return consumes(type) || ObjectiveType.KILL.equals(type) || ObjectiveType.CUSTOM.equals(type);
    }

    private static boolean guaranteesFreshFulfillment(PhaseDefinition phase, EntryRequirementBinding binding) {
        return guaranteesRunAction(phase, binding, CollectionGameplayValidation::freshFulfillment);
    }

    /** Milestone proof only counts requirements that can replay immediately from held/permanent state. */
    private static boolean guaranteesNonInstantRunAction(PhaseDefinition phase, EntryRequirementBinding binding) {
        return guaranteesRunAction(phase, binding, objective -> freshFulfillment(objective)
                || ObjectiveType.CRAFT.equals(objective.getType()) || ObjectiveType.COLLECT.equals(objective.getType())
                && CollectMode.from(objective) == CollectMode.CRAFTED_ONLY);
    }

    private static boolean guaranteesRunAction(PhaseDefinition phase, EntryRequirementBinding binding,
                                                java.util.function.Predicate<ObjectiveEntry> action) {
        var required = binding.getObjectiveIds().stream().map(phase::getObjective)
                .filter(java.util.Objects::nonNull).filter(o -> !o.isOptional()).toList();
        if (binding.getRequirementMode() == CollectionRequirementMode.ALL)
            return required.stream().anyMatch(action);
        // An ANY binding must not bypass its real action with a previously acquired permanent record.
        return binding.getRecordRequirements().isEmpty() && !required.isEmpty()
                && required.stream().allMatch(action);
    }

    private static void validateMilestone(QuestDefinition quest, CollectionRewardNode node, String categoryId) {
        if (node.getRewards().isEmpty()) return;
        Map<String, Boolean> candidates = new HashMap<>();
        for (var phase : quest.getAllPhases()) {
            if (!phase.hasCollectionSheet()) continue;
            for (var binding : phase.getCollectionSheet().getBindings()) {
                if (binding.isOptional()) continue;
                var entry = quest.getCollectionConfig().getEntry(binding.getEntryId());
                if (entry == null || categoryId != null && !categoryId.equals(entry.getCategoryId())) continue;
                String key = phase.getPhaseId() + "/" + (phase.getCollectionSheet().isCountDistinctEntries()
                        ? binding.getEntryId() : binding.getBindingId());
                boolean free = quest.getInitialPhaseIds().contains(phase.getPhaseId()) && !guaranteesNonInstantRunAction(phase, binding);
                candidates.merge(key, free, (one, two) -> one && two);
            }
        }
        int total = candidates.size(), free = (int) candidates.values().stream().filter(Boolean::booleanValue).count();
        for (var rule : node.getUnlockRules()) {
            if (rule instanceof AllEntriesCompleteRule) { if (total == 0 || free < total) return; }
            else if (rule instanceof CompletedEntryCountRule count) { if (free < count.getRequiredCount()) return; }
            else if (rule instanceof CompletedEntryRatioRule ratio) { if (ratio.getRequiredRatio() > 0 && (total == 0 || free / (float) total < ratio.getRequiredRatio())) return; }
            else return; // Unprovable/custom thresholds receive an authoring diagnostic in the Spec validator.
        }
        throw new IllegalArgumentException("Replayable milestone '" + node.getRewardNodeId()
                + "' can be earned from initial preparation or permanent facts alone; require fresh fulfillment or repeatCooldownTicks.");
    }
}
