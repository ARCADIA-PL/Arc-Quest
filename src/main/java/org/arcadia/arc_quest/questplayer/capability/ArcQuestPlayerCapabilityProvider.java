package org.arcadia.arc_quest.questplayer.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ArcQuestPlayerCapabilityProvider implements ICapabilitySerializable<CompoundTag> {

    private final ArcQuestPlayerCapability data = new ArcQuestPlayerCapability();
    private final LazyOptional<ArcQuestPlayerCapability> optional = LazyOptional.of(() -> data);

    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        return capability == ArcQuestCapabilities.PLAYER_DATA ? optional.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return data.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        data.deserializeNBT(tag);
    }

    public void invalidate() {
        optional.invalidate();
    }
}
