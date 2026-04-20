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
 * @param cooldownType    冷却类型（SECONDS=秒级, GAME_DAY=游戏日, GAME_TICK=固定时间刻）
 * @param resetTimeTicks  重置时间刻（Minecraft tick），仅当 cooldownType=GAME_TICK 时有效
 * @param priority        选项优先级（默认 0），高优先级会覆盖低优先级选项
 * @param restoreNodeId   从商店/界面退出后恢复的目标节点 ID（null = 不恢复，保持当前节点）
 */
public record DialogueChoice(
        String text,
        String nextNodeId,
        List<DialogueCondition> conditions,
        List<DialogueAction> actions,
        boolean repeatable,
        long cooldownSeconds,
        CooldownType cooldownType,
        int resetTimeTicks,
        int priority,
        String restoreNodeId
) {
    /**
     * 向后兼容构造器（默认可重复，无冷却，优先级 0，不恢复）。
     */
    public DialogueChoice(String text, String nextNodeId, List<DialogueCondition> conditions,
                          List<DialogueAction> actions) {
        this(text, nextNodeId, conditions, actions, true, 0, CooldownType.NONE, 0, 0, null);
    }

    /**
     * 完整构造器（带优先级、冷却类型和恢复节点）。
     */
    public DialogueChoice(String text, String nextNodeId, List<DialogueCondition> conditions,
                          List<DialogueAction> actions, boolean repeatable, long cooldownSeconds,
                          CooldownType cooldownType, int resetTimeTicks, int priority, String restoreNodeId) {
        this.text = text;
        this.nextNodeId = nextNodeId;
        this.conditions = conditions;
        this.actions = actions;
        this.repeatable = repeatable;
        this.cooldownSeconds = cooldownSeconds;
        this.cooldownType = cooldownType != null ? cooldownType : CooldownType.NONE;
        this.resetTimeTicks = resetTimeTicks;
        this.priority = priority;
        this.restoreNodeId = restoreNodeId;
    }

    /**
     * 便捷构造：无条件、无动作。
     */
    public static DialogueChoice simple(String text, String nextNodeId) {
        return new DialogueChoice(text, nextNodeId, List.of(), List.of());
    }

    /**
     * 便捷构造：带单个动作。
     */
    public static DialogueChoice withAction(String text, String nextNodeId, DialogueAction action) {
        return new DialogueChoice(text, nextNodeId, List.of(), List.of(action));
    }

    /**
     * 便捷构造：带条件。
     */
    public static DialogueChoice conditional(String text, String nextNodeId,
                                             DialogueCondition condition) {
        return new DialogueChoice(text, nextNodeId, List.of(condition), List.of());
    }

    /**
     * 便捷构造：带优先级。
     */
    public static DialogueChoice prioritized(String text, String nextNodeId, int priority) {
        return new DialogueChoice(text, nextNodeId, List.of(), List.of(), true, 0, CooldownType.NONE, 0, priority, null);
    }

    /**
     * 便捷构造：带条件和优先级。
     */
    public static DialogueChoice prioritizedConditional(String text, String nextNodeId,
                                                        DialogueCondition condition, int priority) {
        return new DialogueChoice(text, nextNodeId, List.of(condition), List.of(), true, 0, CooldownType.NONE, 0, priority, null);
    }

    /**
     * 便捷构造：带游戏日冷却。
     */
    public static DialogueChoice gameDayCooldown(String text, String nextNodeId) {
        return new DialogueChoice(text, nextNodeId, List.of(), List.of(), true, 1, CooldownType.GAME_DAY, 0, 0, null);
    }

    /**
     * 便捷构造：带固定时间刻冷却。
     */
    public static DialogueChoice cooldownAtTick(String text, String nextNodeId, int tick) {
        return new DialogueChoice(text, nextNodeId, List.of(), List.of(), true, 1, CooldownType.GAME_TICK, tick, 0, null);
    }
}