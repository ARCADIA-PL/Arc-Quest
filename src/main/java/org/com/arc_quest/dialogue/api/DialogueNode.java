package org.com.arc_quest.dialogue.api;

import java.util.List;
import java.util.Map;

/**
 * 对话树中的单个节点。
 *
 * @param nodeId           节点唯一 ID（在树内唯一）
 * @param speaker          说话者名称（NPC 名 / "Narrator" 等）
 * @param text             对话文本（支持 §格式码 和 %player% 变量）
 * @param conditionalTexts 条件文本映射：条件序列化字符串 → 文本
 * @param choices          玩家可选择的回复列表
 * @param autoNextId       如果没有 choices，自动跳转的节点 ID（null = 结束）
 * @param delayMs          自动跳转前的延迟（毫秒），0 = 立即
 */
public record DialogueNode(
        String nodeId,
        String speaker,
        String text,
        Map<String, String> conditionalTexts,
        List<DialogueChoice> choices,
        String autoNextId,
        int delayMs
) {
    /** 是否为终端节点（无选择、无自动跳转）。 */
    public boolean isTerminal() {
        return (choices == null || choices.isEmpty()) && autoNextId == null;
    }

    /** 是否需要玩家选择。 */
    public boolean hasChoices() {
        return choices != null && !choices.isEmpty();
    }

    /** Builder 便捷方法。 */
    public static Builder builder(String nodeId) {
        return new Builder(nodeId);
    }

    public static class Builder {
        private final String nodeId;
        private String speaker = "";
        private String text = "";
        private Map<String, String> conditionalTexts = Map.of();
        private List<DialogueChoice> choices = List.of();
        private String autoNextId = null;
        private int delayMs = 0;

        Builder(String nodeId) { this.nodeId = nodeId; }

        public Builder speaker(String s) { this.speaker = s; return this; }
        public Builder text(String t) { this.text = t; return this; }
        public Builder conditionalTexts(Map<String, String> ct) { this.conditionalTexts = ct; return this; }
        public Builder choices(DialogueChoice... c) { this.choices = List.of(c); return this; }
        public Builder choices(List<DialogueChoice> c) { this.choices = List.copyOf(c); return this; }
        public Builder autoNext(String id) { this.autoNextId = id; return this; }
        public Builder delay(int ms) { this.delayMs = ms; return this; }

        public DialogueNode build() {
            return new DialogueNode(nodeId, speaker, text, conditionalTexts, choices, autoNextId, delayMs);
        }
    }
}