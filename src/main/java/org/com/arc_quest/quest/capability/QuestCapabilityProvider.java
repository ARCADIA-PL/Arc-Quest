package org.com.arc_quest.quest.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Forge Capability Provider，将 IQuestCapability 绑定到 Player Entity。
 */
public class QuestCapabilityProvider implements ICapabilitySerializable<CompoundTag> {


    public static final Capability<IQuestCapability> QUEST_CAP =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    private final QuestCapabilityImpl backend = new QuestCapabilityImpl();
    private final LazyOptional<IQuestCapability> optional = LazyOptional.of(() -> backend);


    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap,
                                                      @Nullable Direction side) {
        if (cap == QUEST_CAP) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    // ── INBTSerializable ──────────────────────────────────

    @Override
    public CompoundTag serializeNBT() {
        return backend.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        backend.deserializeNBT(nbt);
    }


    public void invalidate() {
        optional.invalidate();
    }
}