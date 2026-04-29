package org.arcadia.arc_quest.quest.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
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

    // ── 静态工具方法 ──────────────────────────────────

    /**
     * 从玩家身上安全地获取 Capability，不存在时返回 null。
     * <p>
     * 消除全项目散落的 {@code player.getCapability(QUEST_CAP).orElse(null)} 模板代码。
     */
    public static @NotNull IQuestCapability getOrNull(ServerPlayer player) {
        return player.getCapability(QUEST_CAP).orElse(null);
    }
    
    /**
     * 从任意玩家（包括客户端）身上安全地获取 Capability。
     * <p>
     * 用于客户端代码或不确定玩家类型的场景。
     */
    public static @NotNull IQuestCapability getOrNull(Player player) {
        return player.getCapability(QUEST_CAP).orElse(null);
    }

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
