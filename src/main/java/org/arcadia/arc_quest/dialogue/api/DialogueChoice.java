package org.arcadia.arc_quest.dialogue.api;

import net.minecraft.sounds.SoundEvent;
import org.arcadia.arc_quest.questmarker.api.MarkSpec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public record DialogueChoice(
        @Nonnull String choiceId,
        DialogueText text,
        String nextNodeId,
        List<DialogueCondition> conditions,
        List<DialogueAction> actions,
        boolean repeatable,
        long cooldownSeconds,
        CooldownType cooldownType,
        int resetTimeTicks,
        int priority,
        String restoreNodeId,
        @Nullable SoundEvent selectSound,
        List<MarkSpec> relatedMarks
) {

    public DialogueChoice {
        if (choiceId == null || choiceId.isEmpty())
            throw new IllegalArgumentException("DialogueChoice ID cannot be null or empty");
        if (text == null) throw new IllegalArgumentException("DialogueChoice text cannot be null");
        relatedMarks = relatedMarks == null ? List.of() : List.copyOf(relatedMarks);
    }

    public static DialogueChoice of(String choiceId, DialogueText text, String nextNodeId) {
        return new DialogueChoice(choiceId, text, nextNodeId, List.of(), List.of(), true, 0, CooldownType.NONE, 0, 0, null, null, List.of());
    }

    public static DialogueChoice withAction(String choiceId, DialogueText text, String nextNodeId, DialogueAction action) {
        return new DialogueChoice(choiceId, text, nextNodeId, List.of(), List.of(action), true, 0, CooldownType.NONE, 0, 0, null, null, List.of());
    }

    public static DialogueChoice conditional(String choiceId, DialogueText text, String nextNodeId, DialogueCondition condition) {
        return new DialogueChoice(choiceId, text, nextNodeId, List.of(condition), List.of(), true, 0, CooldownType.NONE, 0, 0, null, null, List.of());
    }

    public static DialogueChoice prioritized(String choiceId, DialogueText text, String nextNodeId, int priority) {
        return new DialogueChoice(choiceId, text, nextNodeId, List.of(), List.of(), true, 0, CooldownType.NONE, 0, priority, null, null, List.of());
    }

    public static DialogueChoice prioritizedConditional(String choiceId, DialogueText text, String nextNodeId, DialogueCondition condition, int priority) {
        return new DialogueChoice(choiceId, text, nextNodeId, List.of(condition), List.of(), true, 0, CooldownType.NONE, 0, priority, null, null, List.of());
    }

    public static DialogueChoice gameDayCooldown(String choiceId, DialogueText text, String nextNodeId) {
        return new DialogueChoice(choiceId, text, nextNodeId, List.of(), List.of(), true, 1, CooldownType.GAME_DAY, 0, 0, null, null, List.of());
    }

    public static DialogueChoice cooldownAtTick(String choiceId, DialogueText text, String nextNodeId, int tick) {
        return new DialogueChoice(choiceId, text, nextNodeId, List.of(), List.of(), true, 1, CooldownType.GAME_TICK, tick, 0, null, null, List.of());
    }
}
