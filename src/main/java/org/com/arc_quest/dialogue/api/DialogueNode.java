package org.com.arc_quest.dialogue.api;

import net.minecraft.sounds.SoundEvent;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * 对话树中的单个节点。
 *
 * @param nodeId           节点唯一 ID（在树内唯一）
 * @param speaker          说话者名称（NPC 名 / "Narrator" 等）
 * @param text             对话文本（支持 §格式码 和 %player% 变量）
 * @param conditionalTexts 条件文本映射：条件序列化字符串 → {@link ConditionalSay}
 * @param choices          玩家可选择的回复列表
 * @param autoNextId       如果没有 choices，自动跳转的节点 ID（null = 结束）
 * @param delayMs          自动跳转前的延迟（毫秒），0 = 立即
 * @param repeatable       节点是否可重复访问（默认 true）
 * @param cooldownSeconds  节点冷却时间（秒），0 = 无冷却，仅当 repeatable=true 时有效
 * @param cooldownType     冷却类型（SECONDS=秒级, GAME_DAY=游戏日, GAME_TICK=固定时间刻）
 * @param resetTimeTicks   重置时间刻（Minecraft tick），仅当 cooldownType=GAME_TICK 时有效
 *                         <p>例如：6000=早上6点, 12000=中午12点, 18000=晚上6点
 * @param nodeEnterSound   节点进入时播放的音效（可为 null）
 */
public record DialogueNode(
        String nodeId,
        String speaker,
        String text,
        Map<String, ConditionalSay> conditionalTexts,
        List<DialogueChoice> choices,
        String autoNextId,
        int delayMs,
        boolean repeatable,
        long cooldownSeconds,
        CooldownType cooldownType,
        int resetTimeTicks,
        @Nullable SoundEvent nodeEnterSound
) {
    /**
     * 向后兼容构造器（默认可重复，无冷却）。
     */
    public DialogueNode(String nodeId, String speaker, String text,
                        Map<String, ConditionalSay> conditionalTexts,
                        List<DialogueChoice> choices,
                        String autoNextId, int delayMs) {
        this(nodeId, speaker, text, conditionalTexts, choices, autoNextId, delayMs, true, 0, CooldownType.NONE, 0);
    }

    /**
     * 完整构造器（带冷却类型和重置时间刻）。
     */
    public DialogueNode(String nodeId, String speaker, String text,
                        Map<String, ConditionalSay> conditionalTexts,
                        List<DialogueChoice> choices,
                        String autoNextId, int delayMs,
                        boolean repeatable, long cooldownSeconds,
                        CooldownType cooldownType, int resetTimeTicks) {
        this(nodeId, speaker, text, conditionalTexts, choices, autoNextId, delayMs,
                repeatable, cooldownSeconds, cooldownType, resetTimeTicks, null);
    }

    /**
     * 完整构造器（带音效）。
     */
    public DialogueNode(String nodeId, String speaker, String text,
                        Map<String, ConditionalSay> conditionalTexts,
                        List<DialogueChoice> choices,
                        String autoNextId, int delayMs,
                        boolean repeatable, long cooldownSeconds,
                        CooldownType cooldownType, int resetTimeTicks,
                        @Nullable SoundEvent nodeEnterSound) {
        this.nodeId = nodeId;
        this.speaker = speaker;
        this.text = text;
        this.conditionalTexts = conditionalTexts;
        this.choices = choices;
        this.autoNextId = autoNextId;
        this.delayMs = delayMs;
        this.repeatable = repeatable;
        this.cooldownSeconds = cooldownSeconds;
        this.cooldownType = cooldownType != null ? cooldownType : CooldownType.NONE;
        this.resetTimeTicks = resetTimeTicks;
        this.nodeEnterSound = nodeEnterSound;
    }

    /**
     * Builder 便捷方法。
     */
    public static Builder builder(String nodeId) {
        return new Builder(nodeId);
    }

    /**
     * 是否为终端节点（无选择、无自动跳转）。
     */
    public boolean isTerminal() {
        return (choices == null || choices.isEmpty()) && autoNextId == null;
    }

    /**
     * 是否需要玩家选择。
     */
    public boolean hasChoices() {
        return choices != null && !choices.isEmpty();
    }

    public static class Builder {
        private final String nodeId;
        private String speaker = "";
        private String text = "";
        private Map<String, ConditionalSay> conditionalTexts = Map.of();
        private List<DialogueChoice> choices = List.of();
        private String autoNextId = null;
        private int delayMs = 0;
        private boolean repeatable = true;
        private long cooldownSeconds = 0;
        private CooldownType cooldownType = CooldownType.NONE;
        private int resetTimeTicks = 0;  // 重置时间刻
        private SoundEvent nodeEnterSound = null;

        Builder(String nodeId) {
            this.nodeId = nodeId;
        }

        public Builder speaker(String s) {
            this.speaker = s;
            return this;
        }

        public Builder text(String t) {
            this.text = t;
            return this;
        }

        public Builder conditionalTexts(Map<String, ConditionalSay> ct) {
            this.conditionalTexts = ct;
            return this;
        }

        public Builder choices(DialogueChoice... c) {
            this.choices = List.of(c);
            return this;
        }

        public Builder choices(List<DialogueChoice> c) {
            this.choices = List.copyOf(c);
            return this;
        }

        public Builder autoNext(String id) {
            this.autoNextId = id;
            return this;
        }

        public Builder delay(int ms) {
            this.delayMs = ms;
            return this;
        }

        public Builder repeatable(boolean r) {
            this.repeatable = r;
            return this;
        }

        public Builder cooldown(long seconds) {
            this.cooldownSeconds = seconds;
            this.cooldownType = CooldownType.SECONDS;
            return this;
        }

        /**
         * 设置游戏日冷却（每天一次）。
         */
        public Builder cooldownGameDay() {
            this.cooldownSeconds = 1;
            this.cooldownType = CooldownType.GAME_DAY;
            return this;
        }

        /**
         * 设置固定时间刻冷却。
         */
        public Builder cooldownAtTick(int tick) {
            this.cooldownSeconds = 1;
            this.cooldownType = CooldownType.GAME_TICK;
            this.resetTimeTicks = Math.max(0, Math.min(tick, 23999));  // 限制在 0-23999
            return this;
        }

        /**
         * 设置冷却类型。
         */
        public Builder cooldownType(CooldownType type) {
            this.cooldownType = type;
            return this;
        }

        /**
         * 设置重置时间刻（仅 GAME_TICK 有效）。
         */
        public Builder resetTimeTicks(int ticks) {
            this.resetTimeTicks = Math.max(0, Math.min(ticks, 23999));
            return this;
        }

        public Builder enterSound(SoundEvent sound) {
            this.nodeEnterSound = sound;
            return this;
        }

        public DialogueNode build() {
            return new DialogueNode(nodeId, speaker, text, conditionalTexts, choices, autoNextId, delayMs,
                    repeatable, cooldownSeconds, cooldownType, resetTimeTicks, nodeEnterSound);
        }
    }
}