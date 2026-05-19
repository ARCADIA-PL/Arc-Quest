package org.arcadia.arc_quest.questplayer.migration;

import net.minecraft.nbt.CompoundTag;

public final class ArcQuestPlayerMigrationSections {

    private final CompoundTag flagsVars;
    private final CompoundTag questState;
    private final CompoundTag dialogue;
    private final CompoundTag trade;
    private final CompoundTag gacha;
    private final CompoundTag markers;

    public ArcQuestPlayerMigrationSections(CompoundTag flagsVars,
                                           CompoundTag questState,
                                           CompoundTag dialogue,
                                           CompoundTag trade,
                                           CompoundTag gacha,
                                           CompoundTag markers) {
        this.flagsVars = copyOrEmpty(flagsVars);
        this.questState = copyOrEmpty(questState);
        this.dialogue = copyOrEmpty(dialogue);
        this.trade = copyOrEmpty(trade);
        this.gacha = copyOrEmpty(gacha);
        this.markers = copyOrEmpty(markers);
    }

    public CompoundTag getFlagsVars() {
        return flagsVars.copy();
    }

    public CompoundTag getQuestState() {
        return questState.copy();
    }

    public CompoundTag getDialogue() {
        return dialogue.copy();
    }

    public CompoundTag getTrade() {
        return trade.copy();
    }

    public CompoundTag getGacha() {
        return gacha.copy();
    }

    public CompoundTag getMarkers() {
        return markers.copy();
    }

    private static CompoundTag copyOrEmpty(CompoundTag tag) {
        return tag == null ? new CompoundTag() : tag.copy();
    }
}
