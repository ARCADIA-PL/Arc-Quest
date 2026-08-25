package org.arcadia.arc_quest.questplayer;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Arc Quest 玩家数据的独立存储宿主。
 *
 * <p>使用世界级 {@link SavedData} 承载所有玩家的 ArcQuestPlayer 快照，
 * 避免继续依赖 Forge Capability 或 Entity PersistentData 作为正式存档宿主。
 * 默认通过 {@link SavedDataArcQuestPlayerRepository} 暴露给上层仓储接口使用。
 */
public final class ArcQuestPlayerSavedData extends SavedData {
    private static final String DATA_NAME = "arc_quest_player_data";

    private final Map<UUID, CompoundTag> playerSnapshots = new HashMap<>();

    public ArcQuestPlayerSavedData() {
    }

    public static ArcQuestPlayerSavedData load(CompoundTag root) {
        ArcQuestPlayerSavedData data = new ArcQuestPlayerSavedData();
        CompoundTag playersTag = root.getCompound("Players");
        for (String key : playersTag.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                if (playersTag.contains(key, Tag.TAG_COMPOUND)) {
                    data.playerSnapshots.put(uuid, playersTag.getCompound(key).copy());
                }
            } catch (IllegalArgumentException exception) {
                ArcQuestLog.warn(ArcQuestLog.Category.PERSISTENCE, "Ignoring invalid player UUID '{}' in saved data", key, exception);
            }
        }
        return data;
    }

    public static ArcQuestPlayerSavedData get(ServerPlayer player) {
        return get(player.serverLevel());
    }

    public static ArcQuestPlayerSavedData get(ServerLevel level) {
        // ArcQuestPlayer 是全服级玩家数据，不能跟当前维度绑定，
        // 因此统一锚定在 overworld 的 DataStorage 上，避免多维度副本分裂。
        DimensionDataStorage storage = level.getServer().overworld().getDataStorage();
        return storage.computeIfAbsent(ArcQuestPlayerSavedData::load, ArcQuestPlayerSavedData::new, DATA_NAME);
    }

    public CompoundTag getSnapshot(UUID uuid) {
        CompoundTag tag = playerSnapshots.get(uuid);
        return tag == null ? new CompoundTag() : tag.copy();
    }

    public void putSnapshot(UUID uuid, CompoundTag snapshot) {
        playerSnapshots.put(uuid, snapshot.copy());
        setDirty();
    }

    public void removeSnapshot(UUID uuid) {
        if (playerSnapshots.remove(uuid) != null) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        CompoundTag playersTag = new CompoundTag();
        for (var entry : playerSnapshots.entrySet()) {
            playersTag.put(entry.getKey().toString(), entry.getValue().copy());
        }
        root.put("Players", playersTag);
        return root;
    }
}
