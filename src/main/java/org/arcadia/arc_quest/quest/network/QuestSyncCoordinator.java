package org.arcadia.arc_quest.quest.network;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.player.ArcQuestPlayer;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.slf4j.Logger;

/**
 * Quest 同步语义协调器。
 * <p>
 * 统一承载 Quest 侧的“快照持久化 / 网络同步 / push触发”语义入口，
 * 让业务处理器（如 QuestProgressHandler）不直接依赖底层网络细节。
 */
public final class QuestSyncCoordinator {

    private static final Logger LOGGER = LogUtils.getLogger();

    private QuestSyncCoordinator() {
    }

    public static void syncQuestStateAndPush(ServerPlayer player, QuestRuntimeData data) {
        ArcQuestNetwork.syncQuestState(player, data);
    }

    public static void syncFlagsVarsAndPush(ServerPlayer player, ArcQuestPlayer data) {
        ArcQuestNetwork.syncFlagsAndVars(player, data);
    }

    public static void syncFullDataAndPush(ServerPlayer player, ArcQuestPlayer data) {
        ArcQuestNetwork.syncFullData(player, data);
    }

    public static void syncDeltaProgressAndPush(ServerPlayer player,
                                                String questId,
                                                String phaseId,
                                                int objectiveIndex,
                                                int newProgress) {
        ArcQuestNetwork.syncDeltaProgress(player, questId, phaseId, objectiveIndex, newProgress);
    }

    public static void syncDeltaProgressAndPush(ServerPlayer player,
                                                String questId,
                                                int objectiveIndex,
                                                int newProgress) {
        ArcQuestNetwork.syncDeltaProgress(player, questId, objectiveIndex, newProgress);
    }

    /**
     * 统一语义入口：有变更才执行"快照持久化 + 客户端同步 + 清脏"。
     * <p>
     * v2: 按 {@code DirtyKind} 分类调度 —— 不同类别用不同持久化策略和网络包。
     */
    public static void persistAndSyncIfChanged(ServerPlayer player, ArcQuestPlayer data) {
        ArcQuestPlayer.DirtyKind kind = data.getDirtyKind();
        if (kind == ArcQuestPlayer.DirtyKind.NONE) return;

        if (kind == ArcQuestPlayer.DirtyKind.FULL) {
            persistSnapshot(player, data);
            syncFullDataAndPush(player, data);
        } else if (kind == ArcQuestPlayer.DirtyKind.FLAGS_VARS) {
            persistFlagsVars(player, data);
            syncFlagsVarsAndPush(player, data);
        } else {
            persistSnapshot(player, data);
            if (kind == ArcQuestPlayer.DirtyKind.QUEST_STATE) {
                syncQuestStateForDirty(player, data);
            }
        }

        data.clearDirty(kind);

        LOGGER.debug("[QuestPersist] Player {} snapshot persisted (kind={})",
                player.getGameProfile().getName(), kind);
    }

    private static void persistFlagsVars(ServerPlayer player, ArcQuestPlayer data) {
        CompoundTag existing = player.getPersistentData().getCompound("ArcQuestAutosave");
        CompoundTag flagsVars = data.serializeFlagsVars();
        if (existing.contains("Flags")) existing.remove("Flags");
        if (existing.contains("Variables")) existing.remove("Variables");
        existing.put("Flags", flagsVars.get("Flags"));
        existing.put("Variables", flagsVars.get("Variables"));
        player.getPersistentData().put("ArcQuestAutosave", existing);
    }

    private static void syncQuestStateForDirty(ServerPlayer player, ArcQuestPlayer data) {
        syncFullDataAndPush(player, data);
    }

    /**
     * 仅网络同步，不持久化（用于高频 tick sync）。
     */
    public static void syncIfChanged(ServerPlayer player, ArcQuestPlayer data) {
        ArcQuestPlayer.DirtyKind kind = data.getDirtyKind();
        if (kind == ArcQuestPlayer.DirtyKind.NONE) return;

        if (kind == ArcQuestPlayer.DirtyKind.FULL) {
            syncFullDataAndPush(player, data);
        } else if (kind == ArcQuestPlayer.DirtyKind.FLAGS_VARS) {
            syncFlagsVarsAndPush(player, data);
        } else {
            syncQuestStateForDirty(player, data);
        }

        data.clearDirty(kind);
    }

    /**
     * 将能力快照写入玩家 PersistentData（运行时快照，不等同于立即磁盘落盘）。
     */
    public static void persistSnapshot(ServerPlayer player, ArcQuestPlayer data) {
        player.getPersistentData().put("ArcQuestAutosave", data.serializeNBT().copy());
    }
}
