package org.com.arc_quest.dialogue.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Forge Capability 提供者，用于将 {@link DialogueNpcPatch} 附加到实体。
 */
public class DialogueNpcPatchProvider implements ICapabilitySerializable<CompoundTag> {

    private final DialogueNpcPatch patch;
    private final LazyOptional<DialogueNpcPatch> lazy;

    public DialogueNpcPatchProvider(Entity entity) {
        this.patch = new DialogueNpcPatch(entity);
        this.lazy = LazyOptional.of(() -> patch);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == DialogueNpcPatch.CAPABILITY) {
            return lazy.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return patch.save();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        patch.load(nbt);
    }

    public void invalidate() {
        lazy.invalidate();
    }
}