package org.arcadia.arc_quest.quest.network;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

/**
 * Quest 同步语义协调器。
 * <p>
 * 统一承载 Quest 侧的“快照持久化 / 网络同步 / push触发”语义入口，
 * 让业务处理器（如 QuestProgressHandler）不直接依赖底层网络细节。
 */
public final class QuestSyncCoordinator {
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
     * 持久化层统一写入 ArcQuestPlayer 独立 SavedData，网络层则按 DirtyKind 选择最小同步包。
     */
    public static void persistAndSyncIfChanged(ServerPlayer player, ArcQuestPlayer data) {
        ArcQuestPlayer.DirtyKind kind = data.getDirtyKind();
        if (kind == ArcQuestPlayer.DirtyKind.NONE) return;

        persistSnapshot(player, data);

        if (kind == ArcQuestPlayer.DirtyKind.FULL) {
            syncFullDataAndPush(player, data);
        } else if (kind == ArcQuestPlayer.DirtyKind.FLAGS_VARS) {
            syncFlagsVarsAndPush(player, data);
        } else if (kind == ArcQuestPlayer.DirtyKind.TRACKED_QUEST) {
            ArcQuestNetwork.syncTrackedQuest(player, data);
        } else if (kind == ArcQuestPlayer.DirtyKind.MARKERS) {
            ArcQuestNetwork.syncMarkers(player, data);
        } else {
            syncQuestStateForDirty(player, data);
        }

        data.clearDirty(kind);

        ArcQuestLog.debug(ArcQuestLog.Category.PERSISTENCE, "Player {} snapshot persisted (kind={})",
                player.getGameProfile().getName(), kind);
    }

    private static void syncQuestStateForDirty(ServerPlayer player, ArcQuestPlayer data) {
        syncFullDataAndPush(player, data);
    }

    /**
     * 仅网络同步，不持久化（用于高频 tick 同步）。
     */
    public static void syncIfChanged(ServerPlayer player, ArcQuestPlayer data) {
        ArcQuestPlayer.DirtyKind kind = data.getDirtyKind();
        if (kind == ArcQuestPlayer.DirtyKind.NONE) return;

        if (kind == ArcQuestPlayer.DirtyKind.FULL) {
            syncFullDataAndPush(player, data);
        } else if (kind == ArcQuestPlayer.DirtyKind.FLAGS_VARS) {
            syncFlagsVarsAndPush(player, data);
        } else if (kind == ArcQuestPlayer.DirtyKind.TRACKED_QUEST) {
            ArcQuestNetwork.syncTrackedQuest(player, data);
        } else if (kind == ArcQuestPlayer.DirtyKind.MARKERS) {
            ArcQuestNetwork.syncMarkers(player, data);
        } else {
            syncQuestStateForDirty(player, data);
        }

        data.clearDirty(kind);
    }

    /**
     * 将 ArcQuestPlayer 快照写入独立 SavedData 宿主。
     */
    public static void persistSnapshot(ServerPlayer player, ArcQuestPlayer data) {
        ArcQuestPlayerManager.persistSnapshot(player, data);
    }
}
