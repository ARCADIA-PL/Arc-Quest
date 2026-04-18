package org.com.arc_quest.dialogue.api;

import org.com.arc_quest.quest.api.QuestVisualConfig;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 对话树定义（不可变）。
 *
 * @param dialogueId      对话树唯一 ID
 * @param defaultNpc      默认 NPC 名称（用于 speaker 为空时的 fallback 和 %npc% 替换）
 * @param startNodeId     起始节点 ID
 * @param nodes           节点 ID → 节点定义
 * @param visualConfig    可选的 UI 视觉配置（可为 null，使用默认样式）
 * @param repeatable      是否可重复对话（默认 true）
 * @param cooldownSeconds 对话冷却时间（秒），0 = 无冷却，仅当 repeatable=true 时有效
 * @param cooldownType    冷却类型（SECONDS=秒级, GAME_DAY=游戏日, GAME_TICK=固定时间刻）
 * @param resetTimeTicks  重置时间刻（Minecraft tick），仅当 cooldownType=GAME_TICK 时有效
 */
public record DialogueTree(
        String dialogueId,
        String defaultNpc,
        String startNodeId,
        Map<String, DialogueNode> nodes,
        @Nullable QuestVisualConfig visualConfig,
        boolean repeatable,
        long cooldownSeconds,
        CooldownType cooldownType,
        int resetTimeTicks
) {

    /**
     * 向后兼容构造器（默认可重复，无冷却）。
     */
    public DialogueTree(String dialogueId, String defaultNpc, String startNodeId,
                        Map<String, DialogueNode> nodes, @Nullable QuestVisualConfig visualConfig) {
        this(dialogueId, defaultNpc, startNodeId, nodes, visualConfig, true, 0, CooldownType.NONE, 0);
    }

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

        // ═══════════════════════════════════════════
        //  检查生命周期配置冲突
        // ═══════════════════════════════════════════

        // 规则1：如果对话树是一次性的（repeatable=false），则节点和选项不应设置冷却
        if (!repeatable) {
            for (var entry : nodes.entrySet()) {
                String nodeId = entry.getKey();
                DialogueNode node = entry.getValue();

                if (node.cooldownSeconds() > 0) {
                    errors.add("Node '" + nodeId + "' has cooldownSeconds=" + node.cooldownSeconds()
                            + " but dialogue tree is one-time (repeatable=false). Cooldown is meaningless.");
                }

                // 检查选项
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

        // 规则2：如果节点是一次性的（repeatable=false），则不应设置冷却
        for (var entry : nodes.entrySet()) {
            String nodeId = entry.getKey();
            DialogueNode node = entry.getValue();

            if (!node.repeatable() && node.cooldownSeconds() > 0) {
                errors.add("Node '" + nodeId + "' is one-time (repeatable=false) but has cooldownSeconds="
                        + node.cooldownSeconds() + ". Cooldown is meaningless for one-time nodes.");
            }

            // 规则3：如果选项是一次性的（repeatable=false），则不应设置冷却
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