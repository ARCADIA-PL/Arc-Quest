package org.arcadia.arc_quest.mutil.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public final class ArcHudSound {
    private ArcHudSound() {
    }

    public static void click() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.4f);
    }

    public static void pageTurn() {
        play(SoundEvents.BOOK_PAGE_TURN, 1.0f, 1.2f);
    }

    public static void success() {
        play(SoundEvents.PLAYER_LEVELUP, 0.8f, 1.2f);
    }

    public static void fail() {
        play(SoundEvents.ANVIL_LAND, 0.4f, 1.3f);
    }

    public static void play(SoundEvent event, float volume, float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }
}
