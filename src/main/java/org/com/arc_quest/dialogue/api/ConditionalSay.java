package org.com.arc_quest.dialogue.api;

import net.minecraft.sounds.SoundEvent;

import javax.annotation.Nullable;

/**
 * 单个条件台词（SayIf）。
 */
public record ConditionalSay(String text, @Nullable SoundEvent soundEvent) {
    public static ConditionalSay of(String text) {
        return new ConditionalSay(text, null);
    }

    public static ConditionalSay of(String text, SoundEvent sound) {
        return new ConditionalSay(text, sound);
    }
}
