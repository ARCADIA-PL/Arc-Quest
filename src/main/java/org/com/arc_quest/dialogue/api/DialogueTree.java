package org.com.arc_quest.dialogue.api;

import org.com.arc_quest.quest.api.QuestVisualConfig;

import java.util.*;

/**
 * 完整的对话树定义（不可变）。
 *
 * @param dialogueId    全局唯一 ID
 * @param defaultNpc    默认 NPC 名称
 * @param startNodeId   入口节点 ID
 * @param nodes         所有节点的映射 (nodeId → DialogueNode)
 * @param visualConfig  视觉配置（立绘、图标、主题色）
 */
public record DialogueTree(
        String dialogueId,
        String defaultNpc,
        String startNodeId,
        Map<String, DialogueNode> nodes,
        QuestVisualConfig visualConfig
) {
    public DialogueNode getStartNode() {
        return nodes.get(startNodeId);
    }

    public DialogueNode getNode(String nodeId) {
        return nodeId == null ? null : nodes.get(nodeId);
    }

    /**
     * 获取对话的主题色。
     */
    public int getThemeColor() {
        return visualConfig != null ? visualConfig.getThemeColor() : 0xFFFFFFFF;
    }

    /**
     * 获取对话的立绘配置。
     */
    public java.util.Optional<org.com.arc_quest.quest.api.VisualAsset> getSplashConfig(
            org.com.arc_quest.quest.api.SplashType type) {
        if (visualConfig == null) {
            return java.util.Optional.empty();
        }
        return visualConfig.getSplash(type);
    }

    /** 验证树的完整性。 */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (getStartNode() == null) {
            errors.add("Start node '" + startNodeId + "' not found in dialogue '" + dialogueId + "'");
        }
        for (var entry : nodes.entrySet()) {
            DialogueNode node = entry.getValue();
            if (node.hasChoices()) {
                for (DialogueChoice choice : node.choices()) {
                    if (choice.nextNodeId() != null && !nodes.containsKey(choice.nextNodeId())) {
                        errors.add("Node '" + entry.getKey() + "' choice points to missing node '"
                                + choice.nextNodeId() + "'");
                    }
                }
            }
            if (node.autoNextId() != null && !nodes.containsKey(node.autoNextId())) {
                errors.add("Node '" + entry.getKey() + "' autoNext points to missing node '"
                        + node.autoNextId() + "'");
            }
        }
        return errors;
    }

    /** Builder. */
    public static Builder builder(String dialogueId) {
        return new Builder(dialogueId);
    }

    public static class Builder {
        private final String dialogueId;
        private String defaultNpc = "NPC";
        private String startNodeId = "start";
        private final Map<String, DialogueNode> nodes = new LinkedHashMap<>();
        private QuestVisualConfig visualConfig = QuestVisualConfig.EMPTY;

        Builder(String dialogueId) { this.dialogueId = dialogueId; }

        public Builder defaultNpc(String npc) { this.defaultNpc = npc; return this; }
        public Builder startNode(String id) { this.startNodeId = id; return this; }

        public Builder addNode(DialogueNode node) {
            nodes.put(node.nodeId(), node);
            return this;
        }

        /**
         * 设置视觉配置。
         */
        public Builder visualConfig(QuestVisualConfig config) {
            this.visualConfig = config;
            return this;
        }

        public DialogueTree build() {
            return new DialogueTree(dialogueId, defaultNpc, startNodeId,
                    Collections.unmodifiableMap(new LinkedHashMap<>(nodes)),
                    visualConfig);
        }
    }
}