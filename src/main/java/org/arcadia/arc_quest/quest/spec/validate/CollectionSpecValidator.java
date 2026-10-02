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
                  BiConsumer<org.arcadia.arc_quest.condition.ConditionSpec, String> conditionValidator) {
        CollectionQuestSpecData config = quest.collectionConfig;
        boolean hasSheets = safe(quest.phases).stream().anyMatch(p -> p != null && p.collectionSheet != null);
        if (hasSheets && quest.mode != QuestMode.COLLECTION) error(report, "mode", "Collection sheets require COLLECTION mode");
        if (hasSheets && config == null) { error(report, "collectionConfig", "Collection sheets require collectionConfig"); return; }
        if (config == null) return;
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
        if (safe(config.entries).size() > 4096) error(report, "collectionConfig.entries", "Entry count exceeds 4096");
        Map<String, Set<String>> researchByEntry = new LinkedHashMap<>();
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
                } else if (!blank(block.revealStepId)) error(report, bp + ".revealStepId", "revealStepId only belongs to RESEARCH_STEP");
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
                if (!categories.contains(shared.getCategoryId())) error(report, path, "Shared entry uses unknown category: " + shared.getCategoryId());
            }
        }
        for (int i = 0; i < safe(quest.phases).size(); i++) {
            PhaseSpec phase = quest.phases.get(i);
            if (phase == null || phase.collectionSheet == null) continue;
            sheet(quest, phase, "phases[" + i + "].collectionSheet", report, researchByEntry);
        }
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

    private void sheet(QuestSpec quest, PhaseSpec phase, String path, ValidationReport report, Map<String, Set<String>> researchByEntry) {
        CollectionSheetSpecData sheet = phase.collectionSheet;
        if (phase.collectionEntryConfig != null) error(report, path, "Cannot mix modern sheet and legacy phase entry config");
        enumValue(report, CollectionSheetCompletionPolicy.class, sheet.completionPolicy, path + ".completionPolicy");
        if (safe(sheet.bindings).isEmpty()) error(report, path + ".bindings", "Collection sheet needs bindings");
        if (safe(sheet.bindings).size() > 4096) error(report, path + ".bindings", "Binding count exceeds 4096");
        Set<String> objectiveIds = new HashSet<>();
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
                } else if (!blank(requirement.stepId)) error(report, rp + ".stepId", "stepId only belongs to RESEARCH_STEP");
                if ("NEW_DISCOVERIES".equals(binding.recordPolicy) && !"DISCOVERED".equals(requirement.type)) error(report, bp + ".recordPolicy", "NEW_DISCOVERIES only supports DISCOVERED requirements");
            }
            if ("NEW_DISCOVERIES".equals(binding.recordPolicy) && safe(binding.recordRequirements).isEmpty()) error(report, bp + ".recordPolicy", "NEW_DISCOVERIES requires DISCOVERED record requirement");
            if (!binding.optional) candidates.add(sheet.countDistinctEntries ? binding.entryId : binding.bindingId);
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
