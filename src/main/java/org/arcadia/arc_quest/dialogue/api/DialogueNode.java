package org.arcadia.arc_quest.dialogue.api;

import net.minecraft.sounds.SoundEvent;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * 对话树中的单个节点。
 */
public record DialogueNode(
        String nodeId,
        DialogueText speaker,
        DialogueText text,
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
    public DialogueNode(String nodeId, DialogueText speaker, DialogueText text,
                        Map<String, ConditionalSay> conditionalTexts,
                        List<DialogueChoice> choices,
                        String autoNextId, int delayMs) {
        this(nodeId, speaker, text, conditionalTexts, choices, autoNextId, delayMs, true, 0, CooldownType.NONE, 0);
    }

    public DialogueNode(String nodeId, DialogueText speaker, DialogueText text,
                        Map<String, ConditionalSay> conditionalTexts,
                        List<DialogueChoice> choices,
                        String autoNextId, int delayMs,
                        boolean repeatable, long cooldownSeconds,
                        CooldownType cooldownType, int resetTimeTicks) {
        this(nodeId, speaker, text, conditionalTexts, choices, autoNextId, delayMs,
                repeatable, cooldownSeconds, cooldownType, resetTimeTicks, null);
    }

    public DialogueNode(String nodeId, DialogueText speaker, DialogueText text,
                        Map<String, ConditionalSay> conditionalTexts,
                        List<DialogueChoice> choices,
                        String autoNextId, int delayMs,
                        boolean repeatable, long cooldownSeconds,
                        CooldownType cooldownType, int resetTimeTicks,
                        @Nullable SoundEvent nodeEnterSound) {
        this.nodeId = nodeId;
        this.speaker = speaker == null ? DialogueText.literal("") : speaker;
        this.text = text == null ? DialogueText.literal("") : text;
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

    public static Builder builder(String nodeId) {
        return new Builder(nodeId);
    }

    public boolean isTerminal() {
        return (choices == null || choices.isEmpty()) && autoNextId == null;
    }

    public boolean hasChoices() {
        return choices != null && !choices.isEmpty();
    }

    public static class Builder {
        private final String nodeId;
        private DialogueText speaker = DialogueText.literal("");
        private DialogueText text = DialogueText.literal("");
        private Map<String, ConditionalSay> conditionalTexts = Map.of();
        private List<DialogueChoice> choices = List.of();
        private String autoNextId = null;
        private int delayMs = 0;
        private boolean repeatable = true;
        private long cooldownSeconds = 0;
        private CooldownType cooldownType = CooldownType.NONE;
        private int resetTimeTicks = 0;
        private SoundEvent nodeEnterSound = null;

        Builder(String nodeId) {
            this.nodeId = nodeId;
        }

        public Builder speaker(DialogueText s) {
            speaker = s;
            return this;
        }

        public Builder speaker(String s) {
            speaker = DialogueText.literal(s);
            return this;
        }

        public Builder text(DialogueText t) {
            text = t;
            return this;
        }

        public Builder text(String t) {
            text = DialogueText.literal(t);
            return this;
        }

        public Builder conditionalTexts(Map<String, ConditionalSay> ct) {
            conditionalTexts = ct;
            return this;
        }

        public Builder choices(DialogueChoice... c) {
            choices = List.of(c);
            return this;
        }

        public Builder choices(List<DialogueChoice> c) {
            choices = List.copyOf(c);
            return this;
        }

        public Builder autoNext(String id) {
            autoNextId = id;
            return this;
        }

        public Builder delay(int ms) {
            delayMs = ms;
            return this;
        }

        public Builder repeatable(boolean r) {
            repeatable = r;
            return this;
        }

        public Builder cooldown(long seconds) {
            cooldownSeconds = seconds;
            cooldownType = CooldownType.SECONDS;
            return this;
        }

        public Builder cooldownGameDay() {
            cooldownSeconds = 1;
            cooldownType = CooldownType.GAME_DAY;
            return this;
        }

        public Builder cooldownAtTick(int tick) {
            cooldownSeconds = 1;
            cooldownType = CooldownType.GAME_TICK;
            resetTimeTicks = Math.max(0, Math.min(tick, 24000));
            return this;
        }

        public Builder cooldownType(CooldownType type) {
            cooldownType = type;
            return this;
        }

        public Builder resetTimeTicks(int ticks) {
            resetTimeTicks = Math.max(0, Math.min(ticks, 24000));
            return this;
        }

        public Builder enterSound(SoundEvent sound) {
            nodeEnterSound = sound;
            return this;
        }

        public DialogueNode build() {
            return new DialogueNode(nodeId, speaker, text, conditionalTexts, choices, autoNextId, delayMs,
                    repeatable, cooldownSeconds, cooldownType, resetTimeTicks, nodeEnterSound);
        }
    }
}
