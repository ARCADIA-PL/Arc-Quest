package org.com.arc_quest.dialogue.api;

import java.util.List;

/**
 * 一个对话选择项。
 *
 * @param text            显示文本
 * @param nextNodeId      选择后跳转的节点 ID（null = 关闭对话）
 * @param conditions      可见性条件（全部满足才显示）
 * @param actions         选择后执行的动作列表
 * @param repeatable      选项是否可重复选择（默认 true）
 * @param cooldownSeconds 选项冷却时间（秒），0 = 无冷却，仅当 repeatable=true 时有效
 */
public record DialogueChoice(
        String text,
        String nextNodeId,
        List<DialogueCondition> conditions,
        List<DialogueAction> actions,
        boolean repeatable,
        long cooldownSeconds
) {
    /**
     * 向后兼容构造器（默认可重复，无冷却）。
     */
    public DialogueChoice(String text, String nextNodeId, List<DialogueCondition> conditions,
                          List<DialogueAction> actions) {
        this(text, nextNodeId, conditions, actions, true, 0);
    }
    /** 便捷构造：无条件、无动作。 */
    public static DialogueChoice simple(String text, String nextNodeId) {
        return new DialogueChoice(text, nextNodeId, List.of(), List.of());
    }

    /** 便捷构造：带单个动作。 */
    public static DialogueChoice withAction(String text, String nextNodeId, DialogueAction action) {
        return new DialogueChoice(text, nextNodeId, List.of(), List.of(action));
    }

    /** 便捷构造：带条件。 */
    public static DialogueChoice conditional(String text, String nextNodeId,
                                             DialogueCondition condition) {
        return new DialogueChoice(text, nextNodeId, List.of(condition), List.of());
    }
}