package org.arcadia.arc_quest.dialogue.spec.validate;

import org.arcadia.arc_quest.dialogue.spec.*;

import java.util.Set;
import java.util.stream.Collectors;

public final class DialogueSpecValidator {

    public DialogueValidationReport validate(DialogueSpec spec) {
        DialogueValidationReport report = new DialogueValidationReport();

        if (spec == null) {
            report.add(DialogueValidationIssue.Severity.ERROR, "dialogue", "DialogueSpec is null");
            return report;
        }

        if (spec.id == null || spec.id.isBlank()) {
            report.add(DialogueValidationIssue.Severity.ERROR, "id", "Dialogue id is required");
        }

        validateText(report, spec.defaultNpc, "defaultNpc");

        if (spec.nodes == null || spec.nodes.isEmpty()) {
            report.add(DialogueValidationIssue.Severity.ERROR, "nodes", "Dialogue must contain at least one node");
            return report;
        }

        Set<String> nodeIds = spec.nodes.stream()
                .map(n -> n.nodeId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        if (spec.startNodeId != null && !spec.startNodeId.isBlank()) {
            if (!nodeIds.contains(spec.startNodeId)) {
                report.add(DialogueValidationIssue.Severity.ERROR, "startNodeId",
                        "Start node '" + spec.startNodeId + "' not found in nodes");
            }
        }

        for (int i = 0; i < spec.nodes.size(); i++) {
            validateNode(report, spec.nodes.get(i), i, nodeIds);
        }

        if (spec.npcBindings != null) {
            for (int i = 0; i < spec.npcBindings.size(); i++) {
                validateNpcBinding(report, spec.npcBindings.get(i), i);
            }
        }

        if (spec.entityBindings != null) {
            for (int i = 0; i < spec.entityBindings.size(); i++) {
                validateEntityBinding(report, spec.entityBindings.get(i), i);
            }
        }

        return report;
    }

    private void validateNode(DialogueValidationReport report, DialogueNodeSpec node, int idx, Set<String> allNodeIds) {
        String prefix = "nodes[" + idx + "]";

        if (node.nodeId == null || node.nodeId.isBlank()) {
            report.add(DialogueValidationIssue.Severity.ERROR, prefix + ".nodeId", "Node id is required");
            return;
        }

        validateText(report, node.speaker, prefix + ".speaker");
        validateText(report, node.text, prefix + ".text");

        if (node.autoNextId != null && !node.autoNextId.isBlank()) {
            if (!allNodeIds.contains(node.autoNextId)) {
                report.add(DialogueValidationIssue.Severity.ERROR, prefix + ".autoNextId",
                        "Target node '" + node.autoNextId + "' not found");
            }
        }

        if (node.conditionalTexts != null) {
            for (var entry : node.conditionalTexts.entrySet()) {
                ConditionalSaySpec say = entry.getValue();
                if (say != null) {
                    if (say.sayId == null || say.sayId.isBlank()) {
                        report.add(DialogueValidationIssue.Severity.WARNING,
                                prefix + ".conditionalTexts." + entry.getKey() + ".sayId",
                                "Conditional say id is empty");
                    }
                    validateText(report, say.text, prefix + ".conditionalTexts." + entry.getKey() + ".text");
                }
            }
        }

        if (node.choices != null) {
            for (int ci = 0; ci < node.choices.size(); ci++) {
                validateChoice(report, node.choices.get(ci), ci, prefix, allNodeIds);
            }
        }
    }

    private void validateChoice(DialogueValidationReport report, DialogueChoiceSpec choice, int idx,
                                String parentPrefix, Set<String> allNodeIds) {
        String prefix = parentPrefix + ".choices[" + idx + "]";

        if (choice.choiceId == null || choice.choiceId.isBlank()) {
            report.add(DialogueValidationIssue.Severity.ERROR, prefix + ".choiceId", "Choice id is required");
        }

        validateText(report, choice.text, prefix + ".text");

        if (choice.nextNodeId != null && !choice.nextNodeId.isBlank()) {
            if (!allNodeIds.contains(choice.nextNodeId)) {
                report.add(DialogueValidationIssue.Severity.ERROR, prefix + ".nextNodeId",
                        "Target node '" + choice.nextNodeId + "' not found");
            }
        }

        if (choice.restoreNodeId != null && !choice.restoreNodeId.isBlank()) {
            if (!allNodeIds.contains(choice.restoreNodeId)) {
                report.add(DialogueValidationIssue.Severity.ERROR, prefix + ".restoreNodeId",
                        "Restore node '" + choice.restoreNodeId + "' not found");
            }
        }

        if (choice.conditions != null) {
            for (int ci = 0; ci < choice.conditions.size(); ci++) {
                validateCondition(report, choice.conditions.get(ci), prefix + ".conditions[" + ci + "]");
            }
        }

        if (choice.actions != null) {
            for (int ai = 0; ai < choice.actions.size(); ai++) {
                validateAction(report, choice.actions.get(ai), prefix + ".actions[" + ai + "]");
            }
        }
    }

    private void validateCondition(DialogueValidationReport report, DialogueConditionSpec cond, String prefix) {
        if (cond == null) return;

        if (cond.type == null || cond.type.isBlank()) {
            report.add(DialogueValidationIssue.Severity.WARNING, prefix + ".type", "Condition type is empty, defaulting to always");
            return;
        }

        switch (cond.type) {
            case "not":
                if (cond.inner == null) {
                    report.add(DialogueValidationIssue.Severity.ERROR, prefix + ".inner", "NOT condition requires inner condition");
                } else {
                    validateCondition(report, cond.inner, prefix + ".inner");
                }
                break;
            case "all":
            case "any":
                if (cond.conditions == null || cond.conditions.isEmpty()) {
                    report.add(DialogueValidationIssue.Severity.WARNING, prefix + ".conditions",
                            cond.type.toUpperCase() + " condition has no sub-conditions");
                }
                break;
            case "quest_phase":
            case "quest_phase_active":
            case "quest_phase_completed":
            case "quest_phase_reached":
            case "phase_enterable":
                if (cond.questId.isBlank() || cond.phaseId.isBlank()) {
                    report.add(DialogueValidationIssue.Severity.ERROR, prefix,
                            "Condition type '" + cond.type + "' requires questId and phaseId");
                }
                break;
            case "phase_before":
            case "phase_after":
                if (cond.questId.isBlank() || cond.targetPhaseId.isBlank()) {
                    report.add(DialogueValidationIssue.Severity.ERROR, prefix,
                            "Condition type '" + cond.type + "' requires questId and targetPhaseId");
                }
                break;
            case "phase_between":
            case "any_active_in_range":
            case "all_completed_in_range":
                if (cond.questId.isBlank() || cond.fromPhaseId.isBlank() || cond.toPhaseId.isBlank()) {
                    report.add(DialogueValidationIssue.Severity.ERROR, prefix,
                            "Condition type '" + cond.type + "' requires questId, fromPhaseId and toPhaseId");
                }
                break;
            case "has_quest":
            case "quest_active":
            case "quest_completed":
            case "quest_failed":
            case "dialogue_completed":
            case "dialogue_on_cooldown":
                if (cond.questId.isBlank() && cond.dialogueId.isBlank()) {
                    report.add(DialogueValidationIssue.Severity.ERROR, prefix,
                            "Condition type '" + cond.type + "' requires questId or dialogueId");
                }
                break;
            case "has_flag":
                if (cond.flagName.isBlank()) {
                    report.add(DialogueValidationIssue.Severity.ERROR, prefix,
                            "Condition type 'has_flag' requires flagName");
                }
                break;
            case "variable_check":
                if (cond.variableKey.isBlank()) {
                    report.add(DialogueValidationIssue.Severity.ERROR, prefix,
                            "Condition type 'variable_check' requires variableKey");
                }
                break;
            case "game_time_in_range":
                if (cond.startTick == 0 && cond.endTick == 0) {
                    report.add(DialogueValidationIssue.Severity.WARNING, prefix,
                            "Condition type 'game_time_in_range' has startTick=0 and endTick=0");
                }
                break;
            case "node_visited":
            case "node_on_cooldown":
                if (cond.nodeId.isBlank()) {
                    report.add(DialogueValidationIssue.Severity.ERROR, prefix,
                            "Condition type '" + cond.type + "' requires nodeId");
                }
                break;
            case "choice_selected":
            case "choice_on_cooldown":
                if (cond.choiceId.isBlank()) {
                    report.add(DialogueValidationIssue.Severity.ERROR, prefix,
                            "Condition type '" + cond.type + "' requires choiceId");
                }
                break;
            case "custom":
                if (cond.name.isBlank()) {
                    report.add(DialogueValidationIssue.Severity.WARNING, prefix,
                            "Custom condition has empty name");
                }
                break;
        }
    }

    private void validateAction(DialogueValidationReport report, DialogueActionSpec action, String prefix) {
        if (action == null) return;

        if (action.type == null || action.type.isBlank()) {
            report.add(DialogueValidationIssue.Severity.WARNING, prefix + ".type", "Action type is empty");
            return;
        }

        switch (action.type) {
            case "start_quest":
            case "complete_quest":
            case "advance_phase":
                if (action.questId.isBlank()) {
                    report.add(DialogueValidationIssue.Severity.ERROR, prefix,
                            "Action type '" + action.type + "' requires questId");
                }
                break;
            case "give_item":
                if (action.itemId.isBlank()) {
                    report.add(DialogueValidationIssue.Severity.ERROR, prefix,
                            "Action type 'give_item' requires itemId");
                }
                break;
            case "notify_talk":
                if (action.npcId.isBlank()) {
                    report.add(DialogueValidationIssue.Severity.ERROR, prefix,
                            "Action type 'notify_talk' requires npcId");
                }
                break;
            case "notify_interact":
                if (action.targetId.isBlank()) {
                    report.add(DialogueValidationIssue.Severity.ERROR, prefix,
                            "Action type 'notify_interact' requires targetId");
                }
                break;
            case "run_command":
                if (action.command.isBlank()) {
                    report.add(DialogueValidationIssue.Severity.ERROR, prefix,
                            "Action type 'run_command' requires command");
                }
                break;
            case "set_flag":
                if (action.flagName.isBlank()) {
                    report.add(DialogueValidationIssue.Severity.ERROR, prefix,
                            "Action type 'set_flag' requires flagName");
                }
                break;
            case "set_variable":
                if (action.key.isBlank()) {
                    report.add(DialogueValidationIssue.Severity.ERROR, prefix,
                            "Action type 'set_variable' requires key");
                }
                break;
            case "open_trade":
            case "open_simple_trade":
            case "open_gacha":
                if (action.shopId.isBlank()) {
                    report.add(DialogueValidationIssue.Severity.ERROR, prefix,
                            "Action type '" + action.type + "' requires shopId");
                }
                break;
            case "custom":
                if (action.customTypeId.isBlank()) {
                    report.add(DialogueValidationIssue.Severity.ERROR, prefix,
                            "Action type 'custom' requires customTypeId");
                }
                break;
        }
    }

    private void validateText(DialogueValidationReport report, DialogueTextSpec text, String path) {
        if (text == null) {
            report.add(DialogueValidationIssue.Severity.WARNING, path, "Text spec is null");
            return;
        }

        if (text.mode == null || text.mode.isBlank()) {
            report.add(DialogueValidationIssue.Severity.WARNING, path + ".mode", "Text mode is empty, defaulting to literal");
            return;
        }

        if (!text.mode.equals("literal") && !text.mode.equals("translatable")) {
            report.add(DialogueValidationIssue.Severity.ERROR, path + ".mode",
                    "Text mode must be 'literal' or 'translatable', got '" + text.mode + "'");
        }

        if (text.value == null || text.value.isBlank()) {
            report.add(DialogueValidationIssue.Severity.WARNING, path + ".value", "Text value is empty");
        }
    }

    private void validateNpcBinding(DialogueValidationReport report, NpcBindingSpec binding, int idx) {
        String prefix = "npcBindings[" + idx + "]";
        if (binding.npcId == null || binding.npcId.isBlank()) {
            report.add(DialogueValidationIssue.Severity.ERROR, prefix + ".npcId", "NPC binding requires npcId");
        }
    }

    private void validateEntityBinding(DialogueValidationReport report, EntityBindingSpec binding, int idx) {
        String prefix = "entityBindings[" + idx + "]";
        if (binding.entityType == null || binding.entityType.isBlank()) {
            report.add(DialogueValidationIssue.Severity.ERROR, prefix + ".entityType", "Entity binding requires entityType");
        }
    }
}