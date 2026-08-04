package org.arcadia.arc_quest.questplayer.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;
import org.arcadia.arc_quest.questplayer.persistence.ArcQuestPlayerPersistenceMetadata;

public final class ArcQuestPlayerCapability implements INBTSerializable<CompoundTag> {

    private CompoundTag snapshot = new CompoundTag();

    public synchronized CompoundTag snapshot() {
        return snapshot.copy();
    }

    public synchronized void replaceSnapshot(CompoundTag snapshot) {
        this.snapshot = snapshot == null ? new CompoundTag() : snapshot.copy();
    }

    public synchronized void clear() {
        snapshot = new CompoundTag();
    }

    public synchronized boolean isEmpty() {
        return ArcQuestPlayerPersistenceMetadata.isPayloadEmpty(snapshot);
    }

    @Override
    public synchronized CompoundTag serializeNBT() {
        return snapshot.copy();
    }

    @Override
    public synchronized void deserializeNBT(CompoundTag tag) {
        replaceSnapshot(tag);
    }
}
