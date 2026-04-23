package org.com.arc_quest.dialogue.api;

import net.minecraft.sounds.SoundEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 一个对话选择项。
 * <p>
 * <b>每个 Choice 必须有唯一的 ID</b>，用于事件监听、调试和网络同步。
 * </p>
 *
 * @param choiceId        选项唯一标识符（强制）
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
 * @param selectSound     选择该选项时播放的音效（可为 null）
 */
public record DialogueChoice(
        @Nonnull String choiceId,
        String text,
        String nextNodeId,
        List<DialogueCondition> conditions,
        List<DialogueAction> actions,
        boolean repeatable,
        long cooldownSeconds,
        CooldownType cooldownType,
        int resetTimeTicks,
        int priority,
        String restoreNodeId,
        @Nullable SoundEvent selectSound
) {

    /**
     * 验证 choiceId 不能为 null 或空。
     */
    public DialogueChoice {
        if (choiceId == null || choiceId.isEmpty()) {
            throw new IllegalArgumentException("DialogueChoice ID cannot be null or empty");
        }
    }

    /**
     * 便捷构造：无条件、无动作（必须提供 ID）。
     */
    public static DialogueChoice of(String choiceId, String text, String nextNodeId) {
        return new DialogueChoice(choiceId, text, nextNodeId, List.of(), List.of(), true, 0, CooldownType.NONE, 0, 0, null, null);
    }

    /**
     * 便捷构造：带单个动作（必须提供 ID）。
     */
    public static DialogueChoice withAction(String choiceId, String text, String nextNodeId, DialogueAction action) {
        return new DialogueChoice(choiceId, text, nextNodeId, List.of(), List.of(action), true, 0, CooldownType.NONE, 0, 0, null, null);
    }

    /**
     * 便捷构造：带条件（必须提供 ID）。
     */
    public static DialogueChoice conditional(String choiceId, String text, String nextNodeId,
                                             DialogueCondition condition) {
        return new DialogueChoice(choiceId, text, nextNodeId, List.of(condition), List.of(), true, 0, CooldownType.NONE, 0, 0, null, null);
    }

    /**
     * 便捷构造：带优先级（必须提供 ID）。
     */
    public static DialogueChoice prioritized(String choiceId, String text, String nextNodeId, int priority) {
        return new DialogueChoice(choiceId, text, nextNodeId, List.of(), List.of(), true, 0, CooldownType.NONE, 0, priority, null, null);
    }

    /**
     * 便捷构造：带条件和优先级（必须提供 ID）。
     */
    public static DialogueChoice prioritizedConditional(String choiceId, String text, String nextNodeId,
                                                        DialogueCondition condition, int priority) {
        return new DialogueChoice(choiceId, text, nextNodeId, List.of(condition), List.of(), true, 0, CooldownType.NONE, 0, priority, null, null);
    }

    /**
     * 便捷构造：带游戏日冷却（必须提供 ID）。
     */
    public static DialogueChoice gameDayCooldown(String choiceId, String text, String nextNodeId) {
        return new DialogueChoice(choiceId, text, nextNodeId, List.of(), List.of(), true, 1, CooldownType.GAME_DAY, 0, 0, null, null);
    }

    /**
     * 便捷构造：带固定时间刻冷却（必须提供 ID）。
     */
    public static DialogueChoice cooldownAtTick(String choiceId, String text, String nextNodeId, int tick) {
        return new DialogueChoice(choiceId, text, nextNodeId, List.of(), List.of(), true, 1, CooldownType.GAME_TICK, tick, 0, null, null);
    }
}
