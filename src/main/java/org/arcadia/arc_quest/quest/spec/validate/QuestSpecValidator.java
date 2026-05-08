package org.arcadia.arc_quest.quest.spec.validate;

import org.arcadia.arc_quest.quest.api.QuestCompletionPolicy;
import org.arcadia.arc_quest.quest.spec.*;

import java.util.HashSet;
import java.util.Set;

public final class QuestSpecValidator {
    public ValidationReport validate(QuestSpec spec) {
        ValidationReport report = new ValidationReport();
        if (spec == null) {
            report.add(ValidationIssue.Severity.ERROR, "quest", "QuestSpec is null");
            return report;
        }
        if (spec.id == null || spec.id.isBlank()) report.add(ValidationIssue.Severity.ERROR, "id", "Quest id is required");
        validateText(report, spec.displayName, "displayName");
        validateText(report, spec.description, "description");
        validateMarks(report, spec.relatedMarks, "relatedMarks");
        if (spec.phases == null || spec.phases.isEmpty()) report.add(ValidationIssue.Severity.ERROR, "phases", "Quest must contain at least one phase");
        if (spec.initialPhaseId == null || spec.initialPhaseId.isBlank()) report.add(ValidationIssue.Severity.ERROR, "initialPhaseId", "Initial phase is required");

        Set<String> phaseIds = new HashSet<>();
        if (spec.phases != null) {
            for (int i = 0; i < spec.phases.size(); i++) {
                PhaseSpec phase = spec.phases.get(i);
                String path = "phases[" + i + "]";
                validateText(report, phase.displayName, path + ".displayName");
                validateText(report, phase.description, path + ".description");
                validateText(report, phase.story, path + ".story");
                validateMarks(report, phase.relatedMarks, path + ".relatedMarks");
                if (phase.phaseId == null || phase.phaseId.isBlank()) report.add(ValidationIssue.Severity.ERROR, path + ".phaseId", "Phase id is required");
                else if (!phaseIds.add(phase.phaseId)) report.add(ValidationIssue.Severity.ERROR, path + ".phaseId", "Duplicate phase id: " + phase.phaseId);
            }
            for (int i = 0; i < spec.phases.size(); i++) {
                PhaseSpec phase = spec.phases.get(i);
                String path = "phases[" + i + "]";
                if (phase.objectives == null || phase.objectives.isEmpty()) report.add(ValidationIssue.Severity.ERROR, path + ".objectives", "Phase must contain at least one objective");
                if (phase.transitions != null) {
                    for (int t = 0; t < phase.transitions.size(); t++) {
                        TransitionSpec tr = phase.transitions.get(t);
                        if (tr.targetPhaseId == null || tr.targetPhaseId.isBlank()) report.add(ValidationIssue.Severity.ERROR, path + ".transitions[" + t + "]", "Transition targetPhaseId is required");
                        else if (!phaseIds.contains(tr.targetPhaseId)) report.add(ValidationIssue.Severity.ERROR, path + ".transitions[" + t + "]", "Transition targetPhaseId not found: " + tr.targetPhaseId);
                        validateCondition(report, tr.condition, path + ".transitions[" + t + "].condition");
                    }
                }
                if (phase.choices != null) {
                    for (int c = 0; c < phase.choices.size(); c++) {
                        ChoiceSpec choice = phase.choices.get(c);
                        String choicePath = path + ".choices[" + c + "]";
                        validateText(report, choice.text, choicePath + ".text");
                        if (choice.targetPhaseId == null || choice.targetPhaseId.isBlank()) report.add(ValidationIssue.Severity.ERROR, choicePath + ".targetPhaseId", "Choice targetPhaseId is required");
                        else if (!phaseIds.contains(choice.targetPhaseId)) report.add(ValidationIssue.Severity.ERROR, choicePath + ".targetPhaseId", "Choice targetPhaseId not found: " + choice.targetPhaseId);
                        if (choice.flagToSet == null || choice.flagToSet.isBlank()) report.add(ValidationIssue.Severity.WARNING, choicePath + ".flagToSet", "Choice flagToSet is empty");
                        validateCondition(report, choice.visibleCondition, choicePath + ".visibleCondition");
                    }
                }
                if (phase.objectives != null) {
                    for (int o = 0; o < phase.objectives.size(); o++) {
                        ObjectiveSpec objective = phase.objectives.get(o);
                        String objectivePath = path + ".objectives[" + o + "]";
                        validateText(report, objective.displayText, objectivePath + ".displayText");
                        validateMarks(report, objective.relatedMarks, objectivePath + ".relatedMarks");
                        if (objective.type == null) report.add(ValidationIssue.Severity.ERROR, objectivePath + ".type", "Objective type is required");
                        if (objective.targetId == null || objective.targetId.isBlank()) report.add(ValidationIssue.Severity.ERROR, objectivePath + ".targetId", "Objective targetId is required");
                        validateObjective(report, objective, objectivePath);
                    }
                }
                if (phase.phaseRewards != null) {
                    for (int r = 0; r < phase.phaseRewards.size(); r++) {
                        validateReward(report, phase.phaseRewards.get(r), path + ".phaseRewards[" + r + "]");
                    }
                }
            }
        }
        if (spec.completionRewards != null) {
            for (int i = 0; i < spec.completionRewards.size(); i++) {
                validateReward(report, spec.completionRewards.get(i), "completionRewards[" + i + "]");
            }
        }
        if (spec.unlockConditions != null) {
            for (int i = 0; i < spec.unlockConditions.size(); i++) {
                validateCondition(report, spec.unlockConditions.get(i), "unlockConditions[" + i + "]");
            }
        }
        if (spec.initialPhaseId != null && !spec.initialPhaseId.isBlank() && !phaseIds.contains(spec.initialPhaseId)) {
            report.add(ValidationIssue.Severity.ERROR, "initialPhaseId", "Initial phase id not found: " + spec.initialPhaseId);
        }
        validateCompletionPolicy(report, spec, phaseIds.size());
        return report;
    }

    private void validateText(ValidationReport report, QuestTextSpec text, String path) {
        if (text == null) {
            report.add(ValidationIssue.Severity.ERROR, path, "QuestTextSpec is required");
            return;
        }
        if (text.mode == null || text.mode.isBlank()) {
            report.add(ValidationIssue.Severity.ERROR, path + ".mode", "QuestTextSpec mode is required");
            return;
        }
        if (!"literal".equals(text.mode) && !"translatable".equals(text.mode)) {
            report.add(ValidationIssue.Severity.ERROR, path + ".mode", "Unsupported QuestTextSpec mode: " + text.mode);
        }
        if (text.value == null) {
            report.add(ValidationIssue.Severity.ERROR, path + ".value", "QuestTextSpec value is required");
        }
    }

    private void validateMarks(ValidationReport report, java.util.List<MarkSpecData> marks, String path) {
        if (marks == null) return;
        for (int i = 0; i < marks.size(); i++) {
            validateMark(report, marks.get(i), path + "[" + i + "]");
        }
    }

    private void validateMark(ValidationReport report, MarkSpecData mark, String path) {
        if (mark == null) {
            report.add(ValidationIssue.Severity.ERROR, path, "MarkSpecData is required");
            return;
        }
        if (mark.id == null || mark.id.isBlank()) report.add(ValidationIssue.Severity.ERROR, path + ".id", "Mark id is required");
        if (mark.markerType == null) report.add(ValidationIssue.Severity.ERROR, path + ".markerType", "Mark markerType is required");
        if (mark.maxDistance <= 0) report.add(ValidationIssue.Severity.ERROR, path + ".maxDistance", "Mark maxDistance must be > 0");
        if (mark.refreshTicks <= 0) report.add(ValidationIssue.Severity.ERROR, path + ".refreshTicks", "Mark refreshTicks must be > 0");
        validateMarkTarget(report, mark.target, path + ".target");
        validateMarkActivation(report, mark.activateWhen, path + ".activateWhen", false);
        validateMarkActivation(report, mark.deactivateWhen, path + ".deactivateWhen", true);
    }

    private void validateMarkTarget(ValidationReport report, MarkTargetSpec target, String path) {
        if (target == null) {
            report.add(ValidationIssue.Severity.ERROR, path, "Mark target is required");
            return;
        }
        if (target.type == null || target.type.isBlank()) {
            report.add(ValidationIssue.Severity.ERROR, path + ".type", "Mark target type is required");
            return;
        }
        switch (target.type) {
            case "pos" -> validateXYZ(report, target, path);
            case "dimension_pos" -> {
                if (target.dimension == null || target.dimension.isBlank()) report.add(ValidationIssue.Severity.ERROR, path + ".dimension", "dimension_pos requires dimension");
                validateXYZ(report, target, path);
            }
            case "entity_type_nearest" -> {
                if (target.entityType == null || target.entityType.isBlank()) report.add(ValidationIssue.Severity.ERROR, path + ".entityType", "entity_type_nearest requires entityType");
                validatePositive(report, target.searchRadius, path + ".searchRadius", "entity_type_nearest requires searchRadius > 0");
            }
            case "entity_npc_id" -> {
                if (target.npcId == null || target.npcId.isBlank()) report.add(ValidationIssue.Severity.ERROR, path + ".npcId", "entity_npc_id requires npcId");
                validatePositive(report, target.searchRadius, path + ".searchRadius", "entity_npc_id requires searchRadius > 0");
            }
            case "structure_nearest" -> {
                if (target.structureTag == null || target.structureTag.isBlank()) report.add(ValidationIssue.Severity.ERROR, path + ".structureTag", "structure_nearest requires structureTag");
                validatePositive(report, target.searchRadius, path + ".searchRadius", "structure_nearest requires searchRadius > 0");
            }
            case "custom" -> {
                if (target.resolverId == null || target.resolverId.isBlank()) report.add(ValidationIssue.Severity.ERROR, path + ".resolverId", "custom mark target requires resolverId");
            }
            default -> report.add(ValidationIssue.Severity.ERROR, path + ".type", "Unsupported mark target type: " + target.type);
        }
    }

    private void validateMarkActivation(ValidationReport report, MarkActivationSpec activation, String path, boolean allowNull) {
        if (activation == null) {
            if (!allowNull) report.add(ValidationIssue.Severity.ERROR, path, "Mark activation is required");
            return;
        }
        if (activation.type == null || activation.type.isBlank()) {
            report.add(ValidationIssue.Severity.ERROR, path + ".type", "Mark activation type is required");
            return;
        }
        switch (activation.type) {
            case "always", "never" -> { }
            case "flag_set", "flag_not_set" -> {
                if (activation.flag == null || activation.flag.isBlank()) report.add(ValidationIssue.Severity.ERROR, path + ".flag", activation.type + " requires flag");
            }
            case "quest_active" -> {
                if (activation.questId == null || activation.questId.isBlank()) report.add(ValidationIssue.Severity.ERROR, path + ".questId", "quest_active requires questId");
            }
            case "and", "or" -> {
                if (activation.left == null) report.add(ValidationIssue.Severity.ERROR, path + ".left", activation.type + " requires left activation");
                if (activation.right == null) report.add(ValidationIssue.Severity.ERROR, path + ".right", activation.type + " requires right activation");
                validateMarkActivation(report, activation.left, path + ".left", false);
                validateMarkActivation(report, activation.right, path + ".right", false);
            }
            case "not" -> {
                if (activation.left == null) report.add(ValidationIssue.Severity.ERROR, path + ".left", "not requires left activation");
                validateMarkActivation(report, activation.left, path + ".left", false);
            }
            default -> report.add(ValidationIssue.Severity.ERROR, path + ".type", "Unsupported mark activation type: " + activation.type);
        }
    }

    private void validateXYZ(ValidationReport report, MarkTargetSpec target, String path) {
        if (target.x == null) report.add(ValidationIssue.Severity.ERROR, path + ".x", "Mark target x is required");
        if (target.y == null) report.add(ValidationIssue.Severity.ERROR, path + ".y", "Mark target y is required");
        if (target.z == null) report.add(ValidationIssue.Severity.ERROR, path + ".z", "Mark target z is required");
    }

    private void validatePositive(ValidationReport report, Integer value, String path, String message) {
        if (value == null || value <= 0) report.add(ValidationIssue.Severity.ERROR, path, message);
    }

    private void validateCompletionPolicy(ValidationReport report, QuestSpec spec, int phaseCount) {
        if ((spec.completionPolicy == QuestCompletionPolicy.ALL || spec.completionPolicy == QuestCompletionPolicy.ANY) && spec.completionRequiredCount > 0) {
            report.add(ValidationIssue.Severity.ERROR, "completionRequiredCount", "completionRequiredCount only valid for N_OF_M");
        }
        if (spec.completionPolicy == QuestCompletionPolicy.N_OF_M) {
            if (spec.completionRequiredCount < 1 || spec.completionRequiredCount > phaseCount) {
                report.add(ValidationIssue.Severity.ERROR, "completionRequiredCount", "N_OF_M requires completionRequiredCount in [1," + phaseCount + "]");
            }
            if (spec.completionTargetPhaseId != null && !spec.completionTargetPhaseId.isBlank()) {
                report.add(ValidationIssue.Severity.ERROR, "completionTargetPhaseId", "completionTargetPhaseId not allowed with N_OF_M");
            }
        }
        if (spec.completionPolicy == QuestCompletionPolicy.SPECIFIC_PHASE) {
            if (spec.completionTargetPhaseId == null || spec.completionTargetPhaseId.isBlank()) {
                report.add(ValidationIssue.Severity.ERROR, "completionTargetPhaseId", "SPECIFIC_PHASE requires completionTargetPhaseId");
            }
            if (spec.completionRequiredCount > 0) {
                report.add(ValidationIssue.Severity.ERROR, "completionRequiredCount", "completionRequiredCount not allowed with SPECIFIC_PHASE");
            }
        }
    }

    private void validateObjective(ValidationReport report, ObjectiveSpec objective, String path) {
        if (objective.requiredCount < 1) report.add(ValidationIssue.Severity.ERROR, path + ".requiredCount", "Objective requiredCount must be >= 1");
        if (objective.radius != null && objective.radius < 0) report.add(ValidationIssue.Severity.ERROR, path + ".radius", "Objective radius must be >= 0");
        if (objective.countMin != null && objective.countMin < 1) report.add(ValidationIssue.Severity.ERROR, path + ".countMin", "Objective countMin must be >= 1");
        if (objective.countMax != null && objective.countMax < 1) report.add(ValidationIssue.Severity.ERROR, path + ".countMax", "Objective countMax must be >= 1");
        if (objective.countMin != null && objective.countMax != null && objective.countMin > objective.countMax) {
            report.add(ValidationIssue.Severity.ERROR, path + ".countMin", "Objective countMin cannot exceed countMax");
        }
    }

    private void validateReward(ValidationReport report, RewardSpec reward, String path) {
        if (reward == null || reward.type == null || reward.type.isBlank()) {
            report.add(ValidationIssue.Severity.ERROR, path + ".type", "Reward type is required");
            return;
        }
        switch (reward.type) {
            case "item" -> {
                if (reward.itemId == null || reward.itemId.isBlank()) report.add(ValidationIssue.Severity.ERROR, path + ".itemId", "Item reward requires itemId");
            }
            case "flag_set", "flag_clear" -> {
                if (reward.flag == null || reward.flag.isBlank()) report.add(ValidationIssue.Severity.ERROR, path + ".flag", "Flag reward requires flag");
            }
            case "command" -> {
                if (reward.command == null || reward.command.isBlank()) report.add(ValidationIssue.Severity.ERROR, path + ".command", "Command reward requires command");
            }
            case "var_set", "var_add", "var_subtract", "var_multiply" -> {
                if (reward.variable == null || reward.variable.isBlank()) report.add(ValidationIssue.Severity.ERROR, path + ".variable", "Variable reward requires variable");
            }
            default -> report.add(ValidationIssue.Severity.ERROR, path + ".type", "Unsupported reward type: " + reward.type);
        }
    }

    private void validateCondition(ValidationReport report, ConditionSpec condition, String path) {
        if (condition == null) return;
        if (condition.type == null || condition.type.isBlank()) {
            report.add(ValidationIssue.Severity.ERROR, path + ".type", "Condition type is required");
            return;
        }
        switch (condition.type) {
            case "always", "all_entries_complete", "completed_entry_count", "category_completed_count", "completed_entry_ratio", "category_completed_ratio" -> { }
            case "flag_set", "flag_not_set" -> {
                if (condition.flag == null || condition.flag.isBlank()) report.add(ValidationIssue.Severity.ERROR, path + ".flag", "Flag condition requires flag");
            }
            case "quest_completed" -> {
                if (condition.questId == null || condition.questId.isBlank()) report.add(ValidationIssue.Severity.ERROR, path + ".questId", "Quest condition requires questId");
            }
            case "variable" -> {
                if (condition.variable == null || condition.variable.isBlank()) report.add(ValidationIssue.Severity.ERROR, path + ".variable", "Variable condition requires variable");
                if (condition.compareOp == null) report.add(ValidationIssue.Severity.ERROR, path + ".compareOp", "Variable condition requires compareOp");
            }
            case "and", "or" -> {
                if (condition.left == null) report.add(ValidationIssue.Severity.ERROR, path + ".left", condition.type + " requires left condition");
                if (condition.right == null) report.add(ValidationIssue.Severity.ERROR, path + ".right", condition.type + " requires right condition");
                validateCondition(report, condition.left, path + ".left");
                validateCondition(report, condition.right, path + ".right");
            }
            case "not" -> {
                if (condition.left == null) report.add(ValidationIssue.Severity.ERROR, path + ".left", "not requires left condition");
                validateCondition(report, condition.left, path + ".left");
            }
            default -> report.add(ValidationIssue.Severity.ERROR, path + ".type", "Unsupported condition type: " + condition.type);
        }
    }
}
