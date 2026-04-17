package org.com.arc_quest.dialogue.api;

import org.com.arc_quest.quest.api.QuestVisualConfig;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 对话树定义（不可变）。
 *
 * @param dialogueId  对话树唯一 ID
 * @param defaultNpc  默认 NPC 名称（用于 speaker 为空时的 fallback 和 %npc% 替换）
 * @param startNodeId 起始节点 ID
 * @param nodes       节点 ID → 节点定义
 * @param visualConfig 可选的 UI 视觉配置（可为 null，使用默认样式）
 */
public record DialogueTree(
        String dialogueId,
        String defaultNpc,
        String startNodeId,
        Map<String, DialogueNode> nodes,
        @Nullable QuestVisualConfig visualConfig
) {

    /**
     * 获取起始节点。
     */
    @Nullable
    public DialogueNode getStartNode() {
        return nodes.get(startNodeId);
    }

    /**
     * 根据 ID 获取节点。
     */
    @Nullable
    public DialogueNode getNode(String nodeId) {
        return nodeId != null ? nodes.get(nodeId) : null;
    }

    /**
     * 验证对话树的完整性，返回错误列表（空列表 = 无错误）。
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (dialogueId == null || dialogueId.isEmpty()) {
            errors.add("dialogueId is null or empty.");
        }
        if (startNodeId == null || startNodeId.isEmpty()) {
            errors.add("startNodeId is null or empty.");
        }
        if (nodes == null || nodes.isEmpty()) {
            errors.add("No nodes defined.");
            return errors;
        }
        if (!nodes.containsKey(startNodeId)) {
            errors.add("Start node '" + startNodeId + "' not found in nodes.");
        }

        // 检查每个节点的引用是否有效
        for (var entry : nodes.entrySet()) {
            String nodeId = entry.getKey();
            DialogueNode node = entry.getValue();

            // 检查 autoNextId
            if (node.autoNextId() != null && !nodes.containsKey(node.autoNextId())) {
                errors.add("Node '" + nodeId + "' autoNextId '" + node.autoNextId()
                        + "' references non-existent node.");
            }

            // 检查选项的 nextNodeId
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