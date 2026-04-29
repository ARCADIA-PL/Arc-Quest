package org.arcadia.arc_quest.dialogue.api;

import net.minecraft.sounds.SoundEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 单个条件台词（SayIf）。
 * <p>
 * <b>每个 SayIf 分支必须有唯一的 ID</b>，用于事件监听、调试和网络同步。
 * </p>
 */
public record ConditionalSay(
        @Nonnull String sayId,
        DialogueText text,
        @Nullable SoundEvent soundEvent
) {

    public static ConditionalSay of(String sayId, DialogueText text) {
        if (sayId == null || sayId.isEmpty()) {
            throw new IllegalArgumentException("SayIf ID cannot be null or empty");
        }
        if (text == null) {
            throw new IllegalArgumentException("SayIf text cannot be null");
        }
        return new ConditionalSay(sayId, text, null);
    }

    public static ConditionalSay of(String sayId, DialogueText text, SoundEvent sound) {
        if (sayId == null || sayId.isEmpty()) {
            throw new IllegalArgumentException("SayIf ID cannot be null or empty");
        }
        if (text == null) {
            throw new IllegalArgumentException("SayIf text cannot be null");
        }
        return new ConditionalSay(sayId, text, sound);
    }

    public static ConditionalSay of(String sayId, String text) {
        return of(sayId, DialogueText.literal(text));
    }

    public static ConditionalSay of(String sayId, String text, SoundEvent sound) {
        return of(sayId, DialogueText.literal(text), sound);
    }

    public boolean hasValidId() {
        return sayId != null && !sayId.isEmpty();
    }
}
