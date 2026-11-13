package org.arcadia.arc_quest.dialogue.api;

import org.arcadia.arc_quest.quest.api.QuestVisualConfig;
import org.arcadia.arc_quest.questmarker.api.MarkSpec;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 对话树定义（不可变）。
 */
public record DialogueTree(
        String dialogueId,
        DialogueText defaultNpc,
        String startNodeId,
        Map<String, DialogueNode> nodes,
        @Nullable QuestVisualConfig visualConfig,
        boolean repeatable,
        long cooldownSeconds,
        CooldownType cooldownType,
        int resetTimeTicks,
        List<MarkSpec> relatedMarks,
        List<DialogueEntry> entries
) {

    public DialogueTree {
        entries = List.copyOf(entries);
    }

    /** Preserve the existing constructor for previously compiled consumers. */
    public DialogueTree(String dialogueId, DialogueText defaultNpc, String startNodeId,
                        Map<String, DialogueNode> nodes, @Nullable QuestVisualConfig visualConfig,
                        boolean repeatable, long cooldownSeconds, CooldownType cooldownType,
                        int resetTimeTicks, List<MarkSpec> relatedMarks) {
        this(dialogueId, defaultNpc, startNodeId, nodes, visualConfig, repeatable,
                cooldownSeconds, cooldownType, resetTimeTicks, relatedMarks, List.of());
    }

    public DialogueTree(String dialogueId, DialogueText defaultNpc, String startNodeId,
                        Map<String, DialogueNode> nodes, @Nullable QuestVisualConfig visualConfig) {
        this(dialogueId, defaultNpc, startNodeId, nodes, visualConfig, true, 0, CooldownType.NONE, 0, List.of());
    }

    @Nullable
    public DialogueNode getStartNode() {
        return nodes.get(startNodeId);
    }

    /** First matching registration wins; the ordinary start remains the fallback. */
    public DialogueNode resolveStartNode(org.arcadia.arc_quest.dialogue.runtime.DialogueEvalContext context) {
        for (var entry : entries)
            if (entry.condition().test(context)) return nodes.get(entry.nodeId());
        return getStartNode();
    }

    @Nullable
    public DialogueNode getNode(String nodeId) {
        return nodeId != null ? nodes.get(nodeId) : null;
    }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (dialogueId == null || dialogueId.isEmpty()) errors.add("dialogueId is null or empty.");
        if (startNodeId == null || startNodeId.isEmpty()) errors.add("startNodeId is null or empty.");
        if (nodes == null || nodes.isEmpty()) {
            errors.add("No nodes defined.");
            return errors;
        }
        if (!nodes.containsKey(startNodeId)) errors.add("Start node '" + startNodeId + "' not found in nodes.");
        for (var entry : entries)
            if (!nodes.containsKey(entry.nodeId()))
                errors.add("Entry node '" + entry.nodeId() + "' not found in nodes.");

        if (!repeatable) {
            for (var entry : nodes.entrySet()) {
                String nodeId = entry.getKey();
                DialogueNode node = entry.getValue();
                if (node.cooldownSeconds() > 0) {
                    errors.add("Node '" + nodeId + "' has cooldownSeconds=" + node.cooldownSeconds()
                            + " but dialogue tree is one-time (repeatable=false). Cooldown is meaningless.");
                }
                if (node.choices() != null) {
                    for (int i = 0; i < node.choices().size(); i++) {
                        DialogueChoice choice = node.choices().get(i);
                        if (choice.cooldownSeconds() > 0) {
                            errors.add("Node '" + nodeId + "' choice[" + i + "] has cooldownSeconds="
                                    + choice.cooldownSeconds()
                                    + " but dialogue tree is one-time. Cooldown is meaningless.");
                        }
                    }
                }
            }
        }

        for (var entry : nodes.entrySet()) {
            String nodeId = entry.getKey();
            DialogueNode node = entry.getValue();

            if (!node.repeatable() && node.cooldownSeconds() > 0) {
                errors.add("Node '" + nodeId + "' is one-time (repeatable=false) but has cooldownSeconds="
                        + node.cooldownSeconds() + ". Cooldown is meaningless for one-time nodes.");
            }

            if (node.choices() != null) {
                for (int i = 0; i < node.choices().size(); i++) {
                    DialogueChoice choice = node.choices().get(i);
                    if (!choice.repeatable() && choice.cooldownSeconds() > 0) {
                        errors.add("Node '" + nodeId + "' choice[" + i + "] is one-time but has cooldownSeconds="
                                + choice.cooldownSeconds() + ". Cooldown is meaningless for one-time choices.");
                    }
                }
            }
        }

        for (var entry : nodes.entrySet()) {
            String nodeId = entry.getKey();
            DialogueNode node = entry.getValue();

            if (node.autoNextId() != null && !nodes.containsKey(node.autoNextId())) {
                errors.add("Node '" + nodeId + "' autoNextId '" + node.autoNextId()
                        + "' references non-existent node.");
            }

            if (node.choices() != null) {
                for (int i = 0; i < node.choices().size(); i++) {
                    DialogueChoice choice = node.choices().get(i);
                    if (choice.nextNodeId() != null && !nodes.containsKey(choice.nextNodeId())) {
                        errors.add("Node '" + nodeId + "' choice[" + i + "] nextNodeId '"
                                + choice.nextNodeId() + "' references non-existent node.");
                    }
                }
            }
        }

        return errors;
    }
}
