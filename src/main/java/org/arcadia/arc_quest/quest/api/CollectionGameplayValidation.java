package org.arcadia.arc_quest.quest.api;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Authoring safeguards for new investigations; old research definitions retain their published semantics. */
public final class CollectionGameplayValidation {
    private CollectionGameplayValidation() { }

    public static void validate(QuestDefinition quest) {
        if (!quest.hasCollectionSheets()) return;
        var config = quest.getCollectionConfig();
        boolean questPaid = !quest.getCompletionRewards().isEmpty() || !config.getQuestRewardNodes().isEmpty()
                || config.getCategories().stream().anyMatch(category -> !category.getRewardNodes().isEmpty());
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
                boolean paid = !binding.getRewards().isEmpty() || !phase.getPhaseRewards().isEmpty()
                        || questPaid && !binding.isOptional();
                if (quest.isRepeatable() && paid && config.getRepeatCooldownTicks() <= 0
                        && !guaranteesFreshFulfillment(phase, binding)) {
                    throw new IllegalArgumentException("Paid repeatable binding '" + binding.getBindingId()
                            + "' requires unavoidable fresh fulfillment or repeatCooldownTicks. Holdings, location polling,"
                            + " generic interaction, crafting and legacy acquisition alone can be replayed without a new economic cost.");
                }
            }
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
        var required = binding.getObjectiveIds().stream().map(phase::getObjective)
                .filter(java.util.Objects::nonNull).filter(o -> !o.isOptional()).toList();
        if (binding.getRequirementMode() == CollectionRequirementMode.ALL)
            return required.stream().anyMatch(CollectionGameplayValidation::freshFulfillment);
        // An ANY binding must not bypass its real action with a previously acquired permanent record.
        return binding.getRecordRequirements().isEmpty() && !required.isEmpty()
                && required.stream().allMatch(CollectionGameplayValidation::freshFulfillment);
    }
}
