package org.arcadia.arc_quest.quest.spec.validate;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.condition.ConditionSpec;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.api.QuestCategory;
import org.arcadia.arc_quest.quest.api.QuestCompletionPolicy;
import org.arcadia.arc_quest.quest.registry.ObjectiveTypeRegistry;
import org.arcadia.arc_quest.quest.registry.QuestCategoryRegistry;
import org.arcadia.arc_quest.quest.spec.*;
import org.arcadia.arc_quest.questmarker.api.MarkTrigger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class QuestSpecValidator {
    public ValidationReport validate(QuestSpec spec) {
        ValidationReport r = new ValidationReport();
        if (spec == null) return err(r, "quest", "QuestSpec is null");
        req(r, spec.id, "id", "Quest id is required");
        validateCategory(r, spec.category, "category");
        txt(r, spec.displayName, "displayName");
        txt(r, spec.description, "description");
        marks(r, spec.relatedMarks, "relatedMarks", Set.of(MarkTrigger.CONTINUOUS, MarkTrigger.QUEST_ACCEPTED));
        if (empty(spec.phases)) err(r, "phases", "Quest must contain at least one phase");
        if (empty(spec.initialPhaseIds)) {
            req(r, spec.initialPhaseId, "initialPhaseId", "Initial phase is required");
        }

        Set<String> ids = new HashSet<>();
        if (spec.phases != null) {
            for (int i = 0; i < spec.phases.size(); i++) {
                PhaseSpec p = spec.phases.get(i); String b = "phases[" + i + "]";
                txt(r, p.displayName, b + ".displayName"); txt(r, p.description, b + ".description"); txt(r, p.story, b + ".story");
                marks(r, p.relatedMarks, b + ".relatedMarks", Set.of(
                        MarkTrigger.CONTINUOUS, MarkTrigger.PHASE_ENTERED,
                        MarkTrigger.PHASE_COMPLETED, MarkTrigger.PHASE_ADVANCED));
                if (blank(p.phaseId)) err(r, b + ".phaseId", "Phase id is required"); else if (!ids.add(p.phaseId)) err(r, b + ".phaseId", "Duplicate phase id: " + p.phaseId);
            }
            for (int i = 0; i < spec.phases.size(); i++) phase(r, spec.phases.get(i), i, ids);
        }
        list(spec.completionRewards, (x,i) -> reward(r, x, "completionRewards[" + i + "]"));
        list(spec.unlockConditions, (x,i) -> condition(r, x, "unlockConditions[" + i + "]"));
        if (!empty(spec.initialPhaseIds)) {
            for (int i = 0; i < spec.initialPhaseIds.size(); i++) {
                String initialPhaseId = spec.initialPhaseIds.get(i);
                if (blank(initialPhaseId)) err(r, "initialPhaseIds[" + i + "]", "Initial phase id is required");
                else if (!ids.contains(initialPhaseId)) err(r, "initialPhaseIds[" + i + "]", "Initial phase id not found: " + initialPhaseId);
            }
        } else if (!blank(spec.initialPhaseId) && !ids.contains(spec.initialPhaseId)) {
            err(r, "initialPhaseId", "Initial phase id not found: " + spec.initialPhaseId);
        }
        completion(r, spec, ids.size());
        return r;
    }

    private void phase(ValidationReport r, PhaseSpec p, int i, Set<String> ids) {
        String b = "phases[" + i + "]";
        if (empty(p.objectives)) err(r, b + ".objectives", "Phase must contain at least one objective");
        list(p.transitions, (t,idx) -> {
            if (!empty(t.targetPhaseIds)) {
                for (int targetIndex = 0; targetIndex < t.targetPhaseIds.size(); targetIndex++) {
                    String targetPhaseId = t.targetPhaseIds.get(targetIndex);
                    String path = b + ".transitions[" + idx + "].targetPhaseIds[" + targetIndex + "]";
                    if (blank(targetPhaseId)) err(r, path, "Transition target phase id is required");
                    else if (!ids.contains(targetPhaseId)) err(r, path, "Transition target phase id not found: " + targetPhaseId);
                }
            } else if (blank(t.targetPhaseId)) {
                err(r, b + ".transitions[" + idx + "]", "Transition targetPhaseId is required");
            } else if (!ids.contains(t.targetPhaseId)) {
                err(r, b + ".transitions[" + idx + "]", "Transition targetPhaseId not found: " + t.targetPhaseId);
            }
            condition(r, t.condition, b + ".transitions[" + idx + "].condition");
        });
        list(p.choices, (c,idx) -> {
            String cp = b + ".choices[" + idx + "]";
            txt(r, c.text, cp + ".text");
            if (blank(c.targetPhaseId)) err(r, cp + ".targetPhaseId", "Choice targetPhaseId is required");
            else if (!ids.contains(c.targetPhaseId)) err(r, cp + ".targetPhaseId", "Choice targetPhaseId not found: " + c.targetPhaseId);
            if (blank(c.flagToSet)) warn(r, cp + ".flagToSet", "Choice flagToSet is empty");
            condition(r, c.visibleCondition, cp + ".visibleCondition");
        });
        Set<String> objectiveIds = new HashSet<>();
        list(p.objectives, (o,idx) -> {
            String path = b + ".objectives[" + idx + "]";
            if (blank(o.id)) {
                warn(r, path + ".id", "Objective id missing; legacy fallback objective_" + (idx + 1) + " will be used");
            } else if (!objectiveIds.add(o.id)) {
                err(r, path + ".id", "Duplicate objective id: " + o.id);
            }
            objective(r, o, path);
        });
        list(p.phaseRewards, (x,idx) -> reward(r, x, b + ".phaseRewards[" + idx + "]"));
        list(p.guidesToGrantOnEnter, (id, idx) -> resourceId(r, id, b + ".guidesToGrantOnEnter[" + idx + "]"));
        list(p.guidesToGrantOnComplete, (id, idx) -> resourceId(r, id, b + ".guidesToGrantOnComplete[" + idx + "]"));
    }

    private void objective(ValidationReport r, ObjectiveSpec o, String p) {
        txt(r, o.displayText, p + ".displayText");
        marks(r, o.relatedMarks, p + ".relatedMarks", Set.of(
                MarkTrigger.CONTINUOUS, MarkTrigger.OBJECTIVE_COMPLETED));
        if (o.type == null) err(r, p + ".type", "Objective type is required");
        ObjectiveType t = objType(o.type);
        if (t != null && t.requiresTargetId() && blank(o.targetId)) err(r, p + ".targetId", "Objective targetId is required");
        if (o.requiredCount < 1) err(r, p + ".requiredCount", "Objective requiredCount must be >= 1");
        if (o.radius != null && o.radius < 0) err(r, p + ".radius", "Objective radius must be >= 0");
        if (o.countMin != null && o.countMin < 1) err(r, p + ".countMin", "Objective countMin must be >= 1");
        if (o.countMax != null && o.countMax < 1) err(r, p + ".countMax", "Objective countMax must be >= 1");
        if (o.countMin != null && o.countMax != null && o.countMin > o.countMax) err(r, p + ".countMin", "Objective countMin cannot exceed countMax");
    }

    private void txt(ValidationReport r, QuestTextSpec t, String p) {
        if (t == null) { err(r, p, "QuestTextSpec is required"); return; }
        if (blank(t.mode)) { err(r, p + ".mode", "QuestTextSpec mode is required"); return; }
        if (!"literal".equals(t.mode) && !"translatable".equals(t.mode)) err(r, p + ".mode", "Unsupported QuestTextSpec mode: " + t.mode);
        if (t.value == null) err(r, p + ".value", "QuestTextSpec value is required");
    }

    private void marks(ValidationReport r, List<MarkSpecData> ms, String p, Set<MarkTrigger> allowedTriggers) {
        if (ms == null) return;
        if (ms.size() > 4096) err(r, p, "Marker collection exceeds 4096 entries");
        Set<String> ids = new HashSet<>();
        list(ms, (m, i) -> {
            mark(r, m, p + "[" + i + "]");
            if (m != null && m.trigger != null && !allowedTriggers.contains(m.trigger)) {
                err(r, p + "[" + i + "].trigger", "Mark trigger is not valid in this scope: " + m.trigger);
            }
            if (m != null && !blank(m.id) && !ids.add(m.id)) err(r, p + "[" + i + "].id", "Duplicate marker id: " + m.id);
        });
    }

    private void mark(ValidationReport r, MarkSpecData m, String p) {
        if (m == null) { err(r, p, "MarkSpecData is required"); return; }
        req(r, m.id, p + ".id", "Mark id is required");
        if (m.markerType == null) err(r, p + ".markerType", "Mark markerType is required");
        if (m.priority < 0) err(r, p + ".priority", "Mark priority must be >= 0");
        if (m.maxDistance <= 0) err(r, p + ".maxDistance", "Mark maxDistance must be > 0");
        if (m.refreshTicks <= 0) err(r, p + ".refreshTicks", "Mark refreshTicks must be > 0");
        if (m.trigger == null) err(r, p + ".trigger", "Mark trigger is required");
        if (m.durationTicks <= 0 || m.durationTicks > 72000) {
            err(r, p + ".durationTicks", "Mark durationTicks must be between 1 and 72000");
        }
        if (m.styleHints != null && m.styleHints.size() > 64) err(r, p + ".styleHints", "Mark styleHints exceeds 64 entries");
        markTarget(r, m.target, p + ".target");
        markActivation(r, m.activateWhen, p + ".activateWhen", 0);
        markActivation(r, m.deactivateWhen, p + ".deactivateWhen", 0);
    }

    private void markTarget(ValidationReport r, MarkTargetSpec target, String p) {
        if (target == null || blank(target.type)) { err(r, p, "Mark target is required"); return; }
        switch (target.type) {
            case "pos", "block" -> markCoordinates(r, target, p);
            case "dimension_pos" -> { resourceId(r, target.dimension, p + ".dimension"); markCoordinates(r, target, p); }
            case "entity_type_nearest" -> { resourceId(r, target.entityType, p + ".entityType"); positive(r, target.searchRadius, p + ".searchRadius"); }
            case "entity_npc_id" -> { req(r, target.npcId, p + ".npcId", "NPC id is required"); positive(r, target.searchRadius, p + ".searchRadius"); }
            case "structure_nearest" -> {
                resourceId(r, target.structureTag, p + ".structureTag");
                positive(r, target.searchRadius, p + ".searchRadius");
                if (target.y != null && target.useSurfaceY) {
                    err(r, p, "Structure mark y and useSurfaceY cannot both be set");
                }
            }
            case "custom" -> req(r, target.resolverId, p + ".resolverId", "Custom resolver id is required");
            default -> err(r, p + ".type", "Unsupported mark target type: " + target.type);
        }
    }

    private void markCoordinates(ValidationReport r, MarkTargetSpec target, String p) {
        if (target.x == null) err(r, p + ".x", "Mark x is required");
        if (target.y == null) err(r, p + ".y", "Mark y is required");
        if (target.z == null) err(r, p + ".z", "Mark z is required");
    }

    private void positive(ValidationReport r, Integer value, String p) {
        if (value == null || value <= 0) err(r, p, "Value must be > 0");
    }

    private void markActivation(ValidationReport r, MarkActivationSpec activation, String p, int depth) {
        if (activation == null) return;
        if (depth > 16) { err(r, p, "Mark activation depth exceeds 16"); return; }
        switch (activation.type) {
            case "always", "never" -> { }
            case "flag_set", "flag_not_set" -> req(r, activation.flag, p + ".flag", "Flag is required");
            case "quest_active" -> resourceId(r, activation.questId, p + ".questId");
            case "and", "or" -> {
                if (activation.left == null || activation.right == null) err(r, p, "Binary activation requires left and right");
                markActivation(r, activation.left, p + ".left", depth + 1);
                markActivation(r, activation.right, p + ".right", depth + 1);
            }
            case "not" -> {
                if (activation.left == null) err(r, p, "Not activation requires left");
                markActivation(r, activation.left, p + ".left", depth + 1);
            }
            default -> err(r, p + ".type", "Unsupported mark activation type: " + activation.type);
        }
    }

    private void completion(ValidationReport r, QuestSpec s, int n) {
        if ((s.completionPolicy == QuestCompletionPolicy.ALL || s.completionPolicy == QuestCompletionPolicy.ANY) && s.completionRequiredCount > 0) err(r, "completionRequiredCount", "completionRequiredCount only valid for N_OF_M");
        if (s.completionPolicy == QuestCompletionPolicy.N_OF_M) {
            if (s.completionRequiredCount < 1 || s.completionRequiredCount > n) err(r, "completionRequiredCount", "N_OF_M requires completionRequiredCount in [1," + n + "]");
            if (!blank(s.completionTargetPhaseId)) err(r, "completionTargetPhaseId", "completionTargetPhaseId not allowed with N_OF_M");
        }
        if (s.completionPolicy == QuestCompletionPolicy.SPECIFIC_PHASE) {
            req(r, s.completionTargetPhaseId, "completionTargetPhaseId", "SPECIFIC_PHASE requires completionTargetPhaseId");
            if (s.completionRequiredCount > 0) err(r, "completionRequiredCount", "completionRequiredCount not allowed with SPECIFIC_PHASE");
        }
    }

    private void validateCategory(ValidationReport r, String raw, String p) {
        if (blank(raw)) { err(r, p, "Quest category is required"); return; }
        ResourceLocation id = id(raw);
        if (id == null) { err(r, p, "Invalid quest category id: " + raw); return; }
        QuestCategory c = QuestCategoryRegistry.get(id);
        if (c == null) err(r, p, "Unknown quest category: " + id);
    }

    private ObjectiveType objType(String raw) { ResourceLocation id = id(raw); return id == null ? null : ObjectiveTypeRegistry.get(id); }
    private ResourceLocation id(String raw) { if (blank(raw)) return null; String n = raw.trim().toLowerCase(); if (!n.contains(":")) n = "arc_quest:" + n; return ResourceLocation.tryParse(n); }
    private void resourceId(ValidationReport r, String raw, String path) { if (ResourceLocation.tryParse(raw) == null) err(r, path, "Invalid resource id: " + raw); }
    private void reward(ValidationReport r, RewardSpec rw, String p) { if (rw == null || blank(rw.type)) { err(r, p + ".type", "Reward type is required"); return; } switch (rw.type) { case "item" -> req(r, rw.itemId, p + ".itemId", "Item reward requires itemId"); case "flag_set", "flag_clear" -> req(r, rw.flag, p + ".flag", "Flag reward requires flag"); case "command" -> req(r, rw.command, p + ".command", "Command reward requires command"); case "var_set", "var_add", "var_subtract", "var_multiply" -> req(r, rw.variable, p + ".variable", "Variable reward requires variable"); default -> err(r, p + ".type", "Unsupported reward type: " + rw.type); } }
    private void condition(ValidationReport r, ConditionSpec c, String p) { if (c == null || c.isAlways()) return; String x = c.condition; if (blank(x)) { err(r, p + ".condition", "Condition type is required"); return; } if (x.startsWith("minecraft:")) { if (c.predicate == null) warn(r, p + ".predicate", "Vanilla predicate condition has no predicate JSON"); return; } switch (x) { case "arc_quest:always" -> {} case "arc_quest:quest_completed", "arc_quest:quest_accepted", "arc_quest:quest_not_started" -> req(r, c.questId, p + ".questId", x + " requires questId"); case "arc_quest:quest_phase", "arc_quest:quest_phase_completed", "arc_quest:quest_phase_reached" -> { req(r, c.questId, p + ".questId", x + " requires questId"); req(r, c.phaseId, p + ".phaseId", x + " requires phaseId"); } case "arc_quest:has_flag", "arc_quest:not_has_flag" -> req(r, c.flag, p + ".flag", x + " requires flag"); case "arc_quest:variable_check" -> { req(r, c.key, p + ".key", "variable_check requires key"); req(r, c.op, p + ".op", "variable_check requires op"); } case "arc_quest:and", "arc_quest:or" -> { if (empty(c.conditions)) err(r, p + ".conditions", x + " requires conditions list"); else list(c.conditions, (q,i) -> condition(r, q, p + ".conditions[" + i + "]")); } case "arc_quest:not" -> { if (c.inner == null) err(r, p + ".inner", "not requires inner condition"); else condition(r, c.inner, p + ".inner"); } default -> err(r, p + ".condition", "Unsupported condition type: " + x); } }

    private <T> void list(List<T> xs, ItemConsumer<T> c) { if (xs != null) for (int i = 0; i < xs.size(); i++) c.accept(xs.get(i), i); }
    private interface ItemConsumer<T> { void accept(T x, int i); }
    private boolean blank(String s) { return s == null || s.isBlank(); }
    private boolean empty(List<?> l) { return l == null || l.isEmpty(); }
    private void req(ValidationReport r, String s, String p, String m) { if (blank(s)) err(r, p, m); }
    private ValidationReport err(ValidationReport r, String p, String m) { r.add(ValidationIssue.Severity.ERROR, p, m); return r; }
    private void warn(ValidationReport r, String p, String m) { r.add(ValidationIssue.Severity.WARNING, p, m); }
}
