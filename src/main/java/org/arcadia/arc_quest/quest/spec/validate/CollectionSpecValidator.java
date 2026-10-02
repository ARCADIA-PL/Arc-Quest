package org.arcadia.arc_quest.quest.spec.validate;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.registry.CollectionEntryRegistry;
import org.arcadia.arc_quest.quest.spec.*;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/** Cross-reference and semantic validation for modern collection sheets; legacy data stays readable. */
final class CollectionSpecValidator {
    void validate(QuestSpec quest, ValidationReport report,
                  BiConsumer<ObjectiveSpec, String> objectiveValidator,
                  BiConsumer<QuestTextSpec, String> textValidator,
                  BiConsumer<org.arcadia.arc_quest.condition.ConditionSpec, String> conditionValidator,
                  BiConsumer<List<RewardSpec>, String> rewardValidator) {
        CollectionQuestSpecData config = quest.collectionConfig;
        boolean hasSheets = safe(quest.phases).stream().anyMatch(p -> p != null && p.collectionSheet != null);
        if (hasSheets && quest.mode != QuestMode.COLLECTION) error(report, "mode", "Collection sheets require COLLECTION mode");
        if (hasSheets && config == null) { error(report, "collectionConfig", "Collection sheets require collectionConfig"); return; }
        if (config == null) return;
        if (config.repeatCooldownTicks < 0) error(report, "collectionConfig.repeatCooldownTicks", "repeatCooldownTicks must be >= 0");
        if (quest.mode != QuestMode.COLLECTION) error(report, "collectionConfig", "Collection config requires COLLECTION mode");
        Set<String> categories = new HashSet<>();
        int categoryIndex = 0;
        for (CollectionCategorySpecData category : safe(config.categories)) {
            String path = "collectionConfig.categories[" + categoryIndex++ + "]";
            if (category == null || blank(category.categoryId)) error(report, path, "Collection categoryId is required");
            else if (!categories.add(category.categoryId)) error(report, path, "Duplicate collection categoryId: " + category.categoryId);
        }
        if (hasSheets && categories.isEmpty()) error(report, "collectionConfig.categories", "Collection sheets require categories");
        if (hasSheets && !safe(config.completionRules).isEmpty()) error(report, "collectionConfig.completionRules", "Modern quests use true phase completionPolicy and collectionSheet thresholds; legacy quest completionRules are not supported");
        if (hasSheets) milestones(quest, config, report);
        if (safe(config.entries).size() > 4096) error(report, "collectionConfig.entries", "Entry count exceeds 4096");
        Map<String, Set<String>> researchByEntry = new LinkedHashMap<>();
        Map<String, Set<String>> outcomesByEntry = new LinkedHashMap<>();
        Set<String> unifiedEntries = new HashSet<>();
        Set<String> entryIds = new HashSet<>();
        for (int i = 0; i < safe(config.entries).size(); i++) {
            CollectionEntrySpecData entry = config.entries.get(i);
            String path = "collectionConfig.entries[" + i + "]";
            if (entry == null) { error(report, path, "Collection entry is required"); continue; }
            resource(report, entry.entryId, path + ".entryId");
            if (!entryIds.add(entry.entryId)) error(report, path + ".entryId", "Duplicate entryId: " + entry.entryId);
            if (!categories.contains(entry.categoryId)) error(report, path + ".categoryId", "Unknown collection category: " + entry.categoryId);
            textValidator.accept(entry.displayName, path + ".displayName");
            textValidator.accept(entry.description, path + ".description");
            enumValue(report, CollectionSubjectKind.class, entry.subjectKind, path + ".subjectKind");
            if (!"CUSTOM".equals(entry.subjectKind) && blank(entry.subjectId) && blank(entry.itemTag)) {
                error(report, path + ".subjectId", "Entity/item entries require subjectId or itemTag");
            }
            if (!blank(entry.subjectId)) resource(report, entry.subjectId, path + ".subjectId");
            if (!blank(entry.itemTag)) {
                resource(report, entry.itemTag, path + ".itemTag");
                if (!"ITEM".equals(entry.subjectKind)) error(report, path + ".itemTag", "itemTag only belongs to ITEM entries");
            }
            enumValue(report, VisibilityMode.class, entry.visibilityMode, path + ".visibilityMode");
            enumValue(report, HiddenPresentationMode.class, entry.hiddenPresentationMode, path + ".hiddenPresentationMode");
            if (entry.icon != null) {
                try { entry.icon.validate(); } catch (RuntimeException e) { error(report, path + ".icon", e.getMessage()); }
            }
            for (int n = 0; n < safe(entry.relatedItems).size(); n++) resource(report, entry.relatedItems.get(n), path + ".relatedItems[" + n + "]");
            for (int n = 0; n < safe(entry.recordConditions).size(); n++) conditionValidator.accept(entry.recordConditions.get(n), path + ".recordConditions[" + n + "]");
            stableObjectives(report, entry.discoveryObjectives, path + ".discoveryObjectives", objectiveValidator);
            Set<String> researchIds = stableObjectives(report, entry.researchObjectives, path + ".researchObjectives", objectiveValidator);
            researchByEntry.put(entry.entryId, researchIds);
            if (entry.gameplayVersion != 1 && entry.gameplayVersion != 2) error(report, path + ".gameplayVersion", "Supported collection gameplay versions are 1 and 2");
            boolean unified = entry.gameplayVersion == 2;
            if (unified) unifiedEntries.add(entry.entryId);
            if (unified && (!researchIds.isEmpty() || entry.researchAfterDiscovery)) error(report, path, "Unified entries cannot contain independent research objectives");
            if (!unified && (!safe(entry.outcomes).isEmpty() || !safe(entry.legacyResearchObjectives).isEmpty()
                    || (entry.legacyResearchOutcomeMappings != null && !entry.legacyResearchOutcomeMappings.isEmpty())))
                error(report, path, "Legacy research and unified outcomes cannot be mixed");
            Set<String> outcomeIds = new HashSet<>();
            if (safe(entry.outcomes).size() > 128) error(report, path + ".outcomes", "Outcome count exceeds 128");
            for (int n = 0; n < safe(entry.outcomes).size(); n++) {
                var outcome = entry.outcomes.get(n); String op = path + ".outcomes[" + n + "]";
                if (outcome == null) { error(report, op, "Outcome is required"); continue; }
                if (blank(outcome.outcomeId) || outcome.outcomeId.length() > 128 || !outcome.outcomeId.equals(outcome.outcomeId.trim()) || !outcomeIds.add(outcome.outcomeId))
                    error(report, op + ".outcomeId", "Outcome needs a unique stable outcomeId of 1..128 characters");
                textValidator.accept(outcome.displayName, op + ".displayName");
            }
            outcomesByEntry.put(entry.entryId, outcomeIds);
            Set<String> legacyIds = stableObjectives(report, entry.legacyResearchObjectives, path + ".legacyResearchObjectives", objectiveValidator);
            if (entry.legacyResearchOutcomeMappings != null) for (var mapping : entry.legacyResearchOutcomeMappings.entrySet()) {
                if (!outcomeIds.contains(mapping.getValue())) error(report, path + ".legacyResearchOutcomeMappings", "Unknown migration outcome: " + mapping.getValue());
                if (CollectionEntryDefinition.LEGACY_RESEARCH_COMPLETE.equals(mapping.getKey()) ? legacyIds.isEmpty() : !legacyIds.contains(mapping.getKey()))
                    error(report, path + ".legacyResearchOutcomeMappings", "Migration requires original research thresholds: " + mapping.getKey());
            }
            Set<String> rewardIds = new HashSet<>();
            if (safe(entry.rewards).size() > 128) error(report, path + ".rewards", "Entry reward count exceeds 128");
            for (int n = 0; n < safe(entry.rewards).size(); n++) {
                CollectionEntryRewardSpecData reward = entry.rewards.get(n); String rp = path + ".rewards[" + n + "]";
                if (reward == null) { error(report, rp, "Entry reward is required"); continue; }
                if (blank(reward.rewardId) || reward.rewardId.length() > 128 || !reward.rewardId.equals(reward.rewardId.trim()))
                    error(report, rp + ".rewardId", "Entry reward requires a stable rewardId of 1..128 characters");
                else if (!rewardIds.add(reward.rewardId)) error(report, rp + ".rewardId", "Duplicate entry rewardId across triggers");
                enumValue(report, CollectionEntryRewardTrigger.class, reward.trigger, rp + ".trigger");
                enumValue(report, EntryRewardGrantMode.class, reward.grantMode, rp + ".grantMode");
                enumValue(report, CollectionRewardPreviewVisibility.class, reward.previewVisibility, rp + ".previewVisibility");
                if ("OUTCOME".equals(reward.trigger)) {
                    if (!outcomeIds.contains(reward.outcomeId)) error(report, rp + ".outcomeId", "Unknown reward outcome: " + reward.outcomeId);
                } else if (!blank(reward.outcomeId)) error(report, rp + ".outcomeId", "outcomeId only belongs to OUTCOME rewards");
                if (unified && ("RESEARCH_COMPLETE".equals(reward.trigger) || "BINDING_COMPLETE".equals(reward.trigger)))
                    error(report, rp + ".trigger", "Unified entry rewards belong to discovery or a named outcome; run rewards belong to bindings");
                rewardValidator.accept(reward.rewards, rp + ".rewards");
            }
            if (safe(entry.content).size() > 128) error(report, path + ".content", "Content block count exceeds 128");
            Set<String> blocks = new HashSet<>();
            for (int n = 0; n < safe(entry.content).size(); n++) {
                var block = entry.content.get(n); String bp = path + ".content[" + n + "]";
                if (block == null) { error(report, bp, "Content block is required"); continue; }
                if (blank(block.blockId) || !blocks.add(block.blockId)) error(report, bp + ".blockId", "Content needs a unique stable blockId");
                textValidator.accept(block.text, bp + ".text");
                textValidator.accept(block.caption, bp + ".caption");
                enumValue(report, CollectionMediaFit.class, block.fit, bp + ".fit");
                enumValue(report, CollectionContentReveal.class, block.reveal, bp + ".reveal");
                if ("RESEARCH_STEP".equals(block.reveal)) {
                    if (!researchIds.contains(block.revealStepId)) error(report, bp + ".revealStepId", "Unknown research step: " + block.revealStepId);
                } else if ("OUTCOME".equals(block.reveal)) {
                    if (!outcomeIds.contains(block.revealStepId)) error(report, bp + ".revealStepId", "Unknown content outcome: " + block.revealStepId);
                } else if (!blank(block.revealStepId)) error(report, bp + ".revealStepId", "revealStepId only belongs to RESEARCH_STEP or OUTCOME");
                if (unified && ("RESEARCH_STEP".equals(block.reveal) || "RESEARCH_COMPLETE".equals(block.reveal)))
                    error(report, bp + ".reveal", "Unified content requires an explicit outcome instead of research reveal");
                if (block.media != null) {
                    if (!"image".equalsIgnoreCase(block.media.type) && !"none".equalsIgnoreCase(block.media.type)) {
                        error(report, bp + ".media.type", "Collection content supports image media only");
                    }
                    if ("image".equalsIgnoreCase(block.media.type)) resource(report, block.media.texture, bp + ".media.texture");
                    if (block.media.width < 1 || block.media.width > 8192 || block.media.height < 1 || block.media.height > 8192) {
                        error(report, bp + ".media", "Image dimensions must be in [1,8192]");
                    }
                }
            }
        }
        for (int i = 0; i < safe(config.entryIds).size(); i++) {
            String id = config.entryIds.get(i); String path = "collectionConfig.entryIds[" + i + "]";
            resource(report, id, path);
            if (!entryIds.add(id)) error(report, path, "Duplicate shared entry reference: " + id);
            var sharedId = id == null ? null : ResourceLocation.tryParse(id);
            var shared = sharedId == null ? null : CollectionEntryRegistry.getServerEntry(sharedId);
            if (shared == null) error(report, path, "Shared entry has not been registered: " + id);
            else {
                Set<String> research = new HashSet<>();
                for (ObjectiveEntry o : shared.getResearchObjectives()) research.add(o.getObjectiveId());
                researchByEntry.put(id, research);
                outcomesByEntry.put(id, shared.getOutcomes().stream().map(CollectionOutcomeDefinition::outcomeId).collect(java.util.stream.Collectors.toSet()));
                if (shared.isUnifiedGameplay()) unifiedEntries.add(id);
                if (!categories.contains(shared.getCategoryId())) error(report, path, "Shared entry uses unknown category: " + shared.getCategoryId());
            }
        }
        for (int i = 0; i < safe(quest.phases).size(); i++) {
            PhaseSpec phase = quest.phases.get(i);
            if (phase == null || phase.collectionSheet == null) continue;
            sheet(quest, phase, "phases[" + i + "].collectionSheet", report, researchByEntry, outcomesByEntry, unifiedEntries, rewardValidator);
        }
        outcomeGraph(quest, outcomesByEntry, report);
    }

    private record OutcomeKey(String entryId, String outcomeId) { }

    private void outcomeGraph(QuestSpec quest, Map<String, Set<String>> outcomesByEntry, ValidationReport report) {
        Map<OutcomeKey, Set<OutcomeKey>> graph = new LinkedHashMap<>();
        for (PhaseSpec phase : safe(quest.phases)) {
            if (phase == null || phase.collectionSheet == null) continue;
            for (var binding : safe(phase.collectionSheet.bindings)) {
                if (binding == null) continue;
                for (String outcomeId : safe(binding.outcomeIds)) {
                    if (blank(outcomeId)) continue;
                    Set<OutcomeKey> requirements = graph.computeIfAbsent(new OutcomeKey(binding.entryId, outcomeId), ignored -> new HashSet<>());
                    for (var record : safe(binding.recordRequirements)) if (record != null && "OUTCOME".equals(record.type))
                        requirements.add(new OutcomeKey(binding.entryId, record.stepId));
                }
            }
        }
        Set<OutcomeKey> visited = new HashSet<>(), visiting = new HashSet<>();
        for (OutcomeKey key : graph.keySet()) if (cyclicOutcome(key, graph, visited, visiting)) {
            error(report, "collectionConfig.outcomes", "Cyclic outcome investigation prerequisites: " + key);
            break;
        }
        for (var entry : outcomesByEntry.entrySet()) for (String outcomeId : entry.getValue())
            if (!graph.containsKey(new OutcomeKey(entry.getKey(), outcomeId)))
                warning(report, "collectionConfig.outcomes", "No investigation source in this quest for " + entry.getKey() + "/" + outcomeId + "; a registered equivalent quest must provide it");
    }

    private boolean cyclicOutcome(OutcomeKey key, Map<OutcomeKey, Set<OutcomeKey>> graph, Set<OutcomeKey> visited, Set<OutcomeKey> visiting) {
        if (visited.contains(key)) return false;
        if (!visiting.add(key)) return true;
        for (var next : graph.getOrDefault(key, Set.of())) if (cyclicOutcome(next, graph, visited, visiting)) return true;
        visiting.remove(key); visited.add(key); return false;
    }

    private void milestones(QuestSpec quest, CollectionQuestSpecData config, ValidationReport report) {
        Set<String> ids = new HashSet<>();
        nodes(config.rewardNodes, "collectionConfig.rewardNodes", RewardScope.QUEST, quest.id, ids, report);
        for (int i = 0; i < safe(config.categories).size(); i++) {
            CollectionCategorySpecData category = config.categories.get(i);
            if (category == null) continue;
            String path = "collectionConfig.categories[" + i + "]";
            nodes(category.rewardNodes, path + ".rewardNodes", RewardScope.CATEGORY, category.categoryId, ids, report);
            rules(category.completionRules, path + ".completionRules", report);
        }
    }

    private void nodes(List<CollectionRewardNodeSpecData> nodes, String path, RewardScope scope, String owner,
                       Set<String> ids, ValidationReport report) {
        for (int i = 0; i < safe(nodes).size(); i++) {
            CollectionRewardNodeSpecData node = nodes.get(i); String p = path + "[" + i + "]";
            if (node == null) { error(report, p, "Collection reward node is required"); continue; }
            if (blank(node.nodeId)) error(report, p + ".nodeId", "Collection reward node requires an explicit stable nodeId");
            else if (!ids.add(node.nodeId)) error(report, p + ".nodeId", "Duplicate collection reward nodeId across quest scopes: " + node.nodeId);
            if (!scope.name().equals(node.scope)) error(report, p + ".scope", "Collection reward node requires scope " + scope);
            if (!blank(node.scopeRefId) && !node.scopeRefId.equals(owner)) error(report, p + ".scopeRefId", "Collection reward node owner must match " + owner);
            enumValue(report, EntryRewardGrantMode.class, node.grantMode, p + ".grantMode");
            rules(node.completionRules, p + ".completionRules", report);
        }
    }

    private void rules(List<ConditionSpec> rules, String path, ValidationReport report) {
        for (int i = 0; i < safe(rules).size(); i++) rule(rules.get(i), path + "[" + i + "]", report);
    }

    private void rule(ConditionSpec rule, String path, ValidationReport report) {
        if (rule == null) return;
        if (rule.ratio != null && (!Float.isFinite(rule.ratio) || rule.ratio < 0f || rule.ratio > 1f))
            error(report, path + ".ratio", "Collection rule ratio must be finite and in [0,1]");
        if (rule.left != null) rule(rule.left, path + ".left", report);
        if (rule.right != null) rule(rule.right, path + ".right", report);
    }

    private Set<String> stableObjectives(ValidationReport report, List<ObjectiveSpec> objectives, String path,
                                         BiConsumer<ObjectiveSpec, String> validator) {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < safe(objectives).size(); i++) {
            ObjectiveSpec o = objectives.get(i); String p = path + "[" + i + "]";
            if (o == null) { error(report, p, "Objective is required"); continue; }
            if (blank(o.id) || !ids.add(o.id)) error(report, p + ".id", "Record objectives require unique explicit stable IDs");
            validator.accept(o, p);
        }
        return ids;
    }

    private void sheet(QuestSpec quest, PhaseSpec phase, String path, ValidationReport report, Map<String, Set<String>> researchByEntry,
                       Map<String, Set<String>> outcomesByEntry, Set<String> unifiedEntries, BiConsumer<List<RewardSpec>, String> rewardValidator) {
        CollectionSheetSpecData sheet = phase.collectionSheet;
        if (phase.collectionEntryConfig != null) error(report, path, "Cannot mix modern sheet and legacy phase entry config");
        enumValue(report, CollectionSheetCompletionPolicy.class, sheet.completionPolicy, path + ".completionPolicy");
        if (safe(sheet.bindings).isEmpty()) error(report, path + ".bindings", "Collection sheet needs bindings");
        if (safe(sheet.bindings).size() > 4096) error(report, path + ".bindings", "Binding count exceeds 4096");
        Set<String> objectiveIds = new HashSet<>();
        Map<String, String> consumptionOwners = new LinkedHashMap<>();
        Set<String> unifiedConsumption = new HashSet<>();
        for (ObjectiveSpec o : safe(phase.objectives)) if (o != null && !blank(o.id)) objectiveIds.add(o.id);
        Set<String> bindingIds = new HashSet<>(), candidates = new HashSet<>();
        boolean onlyExistingRecords = true;
        for (int i = 0; i < safe(sheet.bindings).size(); i++) {
            var binding = sheet.bindings.get(i); String bp = path + ".bindings[" + i + "]";
            if (binding == null) { error(report, bp, "Binding is required"); continue; }
            if (blank(binding.bindingId) || !bindingIds.add(binding.bindingId)) error(report, bp + ".bindingId", "Binding needs a unique stable bindingId");
            resource(report, binding.entryId, bp + ".entryId");
            if (!researchByEntry.containsKey(binding.entryId)) error(report, bp + ".entryId", "Unknown collection entry: " + binding.entryId);
            enumValue(report, CollectionRequirementMode.class, binding.requirementMode, bp + ".requirementMode");
            enumValue(report, CollectionRecordPolicy.class, binding.recordPolicy, bp + ".recordPolicy");
            if (safe(binding.objectiveIds).isEmpty() && safe(binding.recordRequirements).isEmpty()) error(report, bp, "Binding needs at least one requirement");
            Set<String> references = new HashSet<>();
            for (String id : safe(binding.objectiveIds)) {
                if (!objectiveIds.contains(id)) error(report, bp + ".objectiveIds", "Objective must reference an explicit stable phase ID: " + id);
                if (!references.add(id)) error(report, bp + ".objectiveIds", "Duplicate objective reference: " + id);
                if (safe(phase.objectives).stream().anyMatch(o ->
                        o != null && id.equals(o.id) && ("arc_quest:offer".equals(o.type) || "arc_quest:deliver".equals(o.type)))) {
                    String owner = consumptionOwners.putIfAbsent(id, binding.bindingId);
                    if (owner != null && (unifiedEntries.contains(binding.entryId) || unifiedConsumption.contains(id))) error(report, bp + ".objectiveIds", "Consumed objective '" + id
                            + "' is already assigned to binding '" + owner + "'; define independent submitted quantities");
                    if (unifiedEntries.contains(binding.entryId)) unifiedConsumption.add(id);
                }
            }
            if (safe(binding.recordRequirements).isEmpty() && !safe(binding.objectiveIds).isEmpty()
                    && safe(phase.objectives).stream().filter(o -> o != null && binding.objectiveIds.contains(o.id)).allMatch(o -> o.optional)) {
                error(report, bp, "Binding needs a non-optional requirement");
            }
            Set<String> records = new HashSet<>();
            for (int n = 0; n < safe(binding.recordRequirements).size(); n++) {
                var requirement = binding.recordRequirements.get(n); String rp = bp + ".recordRequirements[" + n + "]";
                if (requirement == null) { error(report, rp, "Record requirement is required"); continue; }
                enumValue(report, CollectionRecordRequirement.Type.class, requirement.type, rp + ".type");
                if (!records.add(requirement.type + ":" + requirement.stepId)) error(report, rp, "Duplicate record requirement");
                if ("RESEARCH_STEP".equals(requirement.type)) {
                    if (!researchByEntry.getOrDefault(binding.entryId, Set.of()).contains(requirement.stepId)) error(report, rp + ".stepId", "Unknown research step: " + requirement.stepId);
                } else if ("OUTCOME".equals(requirement.type)) {
                    if (!outcomesByEntry.getOrDefault(binding.entryId, Set.of()).contains(requirement.stepId)) error(report, rp + ".stepId", "Unknown outcome: " + requirement.stepId);
                } else if (!blank(requirement.stepId)) error(report, rp + ".stepId", "stepId only belongs to RESEARCH_STEP or OUTCOME");
                if (unifiedEntries.contains(binding.entryId) && ("RESEARCH_STEP".equals(requirement.type) || "RESEARCH_COMPLETE".equals(requirement.type)))
                    error(report, rp + ".type", "Unified bindings must require named outcomes, not legacy research");
                if ("NEW_DISCOVERIES".equals(binding.recordPolicy) && !"DISCOVERED".equals(requirement.type)) error(report, bp + ".recordPolicy", "NEW_DISCOVERIES only supports DISCOVERED requirements");
            }
            if ("NEW_DISCOVERIES".equals(binding.recordPolicy) && safe(binding.recordRequirements).isEmpty()) error(report, bp + ".recordPolicy", "NEW_DISCOVERIES requires DISCOVERED record requirement");
            Set<String> produced = new HashSet<>();
            for (String outcomeId : safe(binding.outcomeIds)) {
                if (blank(outcomeId) || !produced.add(outcomeId)) error(report, bp + ".outcomeIds", "Outcome sources require unique stable outcomeIds");
                if (!unifiedEntries.contains(binding.entryId) || !outcomesByEntry.getOrDefault(binding.entryId, Set.of()).contains(outcomeId))
                    error(report, bp + ".outcomeIds", "Unknown outcome source: " + outcomeId);
                if (safe(binding.recordRequirements).stream().anyMatch(r -> r != null && "OUTCOME".equals(r.type) && outcomeId.equals(r.stepId)))
                    error(report, bp + ".outcomeIds", "Outcome source cannot require its own outcome: " + outcomeId);
            }
            if (!produced.isEmpty() && safe(binding.recordRequirements).stream().noneMatch(r -> r != null && "DISCOVERED".equals(r.type)))
                error(report, bp + ".recordRequirements", "Outcome source needs a DISCOVERED prerequisite");
            if ((!produced.isEmpty() || !safe(binding.rewards).isEmpty()) && (!"ALL".equals(binding.requirementMode)
                    || safe(phase.objectives).stream().noneMatch(o -> o != null && safe(binding.objectiveIds).contains(o.id) && !o.optional)))
                error(report, bp, "Outcome sources and paid investigations require ALL mode and non-optional run actions");
            Set<String> bindingRewardIds = new HashSet<>();
            if (safe(binding.rewards).size() > 128) error(report, bp + ".rewards", "Binding reward count exceeds 128");
            for (int n = 0; n < safe(binding.rewards).size(); n++) {
                var reward = binding.rewards.get(n); String rp = bp + ".rewards[" + n + "]";
                if (reward == null) { error(report, rp, "Binding reward is required"); continue; }
                if (blank(reward.rewardId) || reward.rewardId.length() > 128 || !reward.rewardId.equals(reward.rewardId.trim()) || !bindingRewardIds.add(reward.rewardId))
                    error(report, rp + ".rewardId", "Binding reward needs a unique stable rewardId");
                if (!"BINDING_COMPLETE".equals(reward.trigger)) error(report, rp + ".trigger", "Binding rewards require BINDING_COMPLETE trigger");
                if (!blank(reward.outcomeId)) error(report, rp + ".outcomeId", "Binding rewards cannot identify an outcome");
                enumValue(report, EntryRewardGrantMode.class, reward.grantMode, rp + ".grantMode");
                enumValue(report, CollectionRewardPreviewVisibility.class, reward.previewVisibility, rp + ".previewVisibility");
                rewardValidator.accept(reward.rewards, rp + ".rewards");
            }
            if (!binding.optional) candidates.add(sheet.countDistinctEntries ? binding.entryId : binding.bindingId);
            boolean paid = !safe(binding.rewards).isEmpty() || !safe(phase.phaseRewards).isEmpty()
                    || !binding.optional && (!safe(quest.completionRewards).isEmpty() || !safe(quest.collectionConfig.rewardNodes).isEmpty()
                    || safe(quest.collectionConfig.categories).stream().anyMatch(c -> c != null && !safe(c.rewardNodes).isEmpty()));
            var requiredActions = safe(phase.objectives).stream().filter(o -> o != null && !o.optional && safe(binding.objectiveIds).contains(o.id)).toList();
            Set<String> fulfillmentTypes = Set.of("arc_quest:offer", "arc_quest:deliver", "arc_quest:kill", "arc_quest:custom");
            boolean guaranteedFulfillment = "ALL".equals(binding.requirementMode)
                    ? requiredActions.stream().anyMatch(o -> fulfillmentTypes.contains(o.type))
                    : safe(binding.recordRequirements).isEmpty() && !requiredActions.isEmpty()
                    && requiredActions.stream().allMatch(o -> fulfillmentTypes.contains(o.type));
            if (quest.repeatable && unifiedEntries.contains(binding.entryId) && paid && quest.collectionConfig.repeatCooldownTicks <= 0
                    && !guaranteedFulfillment) {
                error(report, bp, "Paid repeatable investigation needs unavoidable fresh fulfillment or repeatCooldownTicks; holdings, location polling, generic interaction, crafting and legacy acquisition can be replayed");
            }
            if (!safe(binding.objectiveIds).isEmpty() || "NEW_DISCOVERIES".equals(binding.recordPolicy)) onlyExistingRecords = false;
        }
        if (candidates.isEmpty()) error(report, path + ".bindings", "Sheet needs a required binding");
        if ("QUOTA".equals(sheet.completionPolicy)) {
            if (sheet.requiredCount < 1 || sheet.requiredCount > candidates.size()) error(report, path + ".requiredCount", "QUOTA threshold must be in [1," + candidates.size() + "]");
        } else if (sheet.requiredCount != 0) error(report, path + ".requiredCount", "ALL sheet must not specify a quota");
        if (quest.repeatable && onlyExistingRecords) warning(report, path, "Repeatable sheet only uses permanent records; repeated acceptance can immediately award rewards. Add run objectives or explicitly accept this behavior.");
    }

    private static <T> List<T> safe(List<T> list) { return list == null ? List.of() : list; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static void resource(ValidationReport report, String value, String path) { if (value == null || value.isBlank() || ResourceLocation.tryParse(value) == null) error(report, path, "Invalid resource ID: " + value); }
    private static <E extends Enum<E>> void enumValue(ValidationReport report, Class<E> type, String value, String path) {
        try { Enum.valueOf(type, value == null ? "" : value); }
        catch (IllegalArgumentException e) { error(report, path, "Invalid " + type.getSimpleName() + ": " + value); }
    }
    private static void error(ValidationReport report, String path, String message) { report.add(ValidationIssue.Severity.ERROR, path, message); }
    private static void warning(ValidationReport report, String path, String message) { report.add(ValidationIssue.Severity.WARNING, path, message); }
}
