package org.arcadia.arc_quest.questplayer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 基于 {@link ArcQuestPlayerSavedData} 的 ArcQuestPlayerRepository 实现。
 */
public final class SavedDataArcQuestPlayerRepository implements ArcQuestPlayerRepository {

    public static final SavedDataArcQuestPlayerRepository INSTANCE = new SavedDataArcQuestPlayerRepository();

    private SavedDataArcQuestPlayerRepository() {
    }

    @Override
    public CompoundTag loadSnapshot(ServerPlayer player, UUID playerUuid) {
        return ArcQuestPlayerSavedData.get(player).getSnapshot(playerUuid);
    }

    @Override
    public void saveSnapshot(ServerPlayer player, UUID playerUuid, CompoundTag snapshot) {
        ArcQuestPlayerSavedData.get(player).putSnapshot(playerUuid, snapshot);
    }

    @Override
    public void deleteSnapshot(ServerPlayer player, UUID playerUuid) {
        ArcQuestPlayerSavedData.get(player).removeSnapshot(playerUuid);
    }
}
