package org.arcadia.arc_quest.questplayer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * ArcQuestPlayer 持久化仓储接口。
 *
 * <p>Manager 仅依赖此接口，不直接感知底层宿主是 SavedData、PersistentData
 * 还是未来 1.21 的其它玩家数据挂载方案。
 */
public interface ArcQuestPlayerRepository {

    CompoundTag loadSnapshot(ServerPlayer player, UUID playerUuid);

    void saveSnapshot(ServerPlayer player, UUID playerUuid, CompoundTag snapshot);

    void deleteSnapshot(ServerPlayer player, UUID playerUuid);
}
