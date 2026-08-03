package org.arcadia.arc_quest.quest.network;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.slf4j.Logger;

/**
 * Quest 鍚屾璇箟鍗忚皟鍣ㄣ€?
 * <p>
 * 缁熶竴鎵胯浇 Quest 渚х殑鈥滃揩鐓ф寔涔呭寲 / 缃戠粶鍚屾 / push瑙﹀彂鈥濊涔夊叆鍙ｏ紝
 * 璁╀笟鍔″鐞嗗櫒锛堝 QuestProgressHandler锛変笉鐩存帴渚濊禆搴曞眰缃戠粶缁嗚妭銆?
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
     * 缁熶竴璇箟鍏ュ彛锛氭湁鍙樻洿鎵嶆墽琛?蹇収鎸佷箙鍖?+ 瀹㈡埛绔悓姝?+ 娓呰剰"銆?
     * <p>
     * 鎸佷箙鍖栧眰缁熶竴鍐欏叆 ArcQuestPlayer 鐙珛 SavedData锛岀綉缁滃眰鍒欐寜 DirtyKind 閫夋嫨鏈€灏忓悓姝ュ寘銆?
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
        } else {
            syncQuestStateForDirty(player, data);
        }

        data.clearDirty(kind);

        LOGGER.debug("[QuestPersist] Player {} snapshot persisted (kind={})",
                player.getGameProfile().getName(), kind);
    }

    private static void syncQuestStateForDirty(ServerPlayer player, ArcQuestPlayer data) {
        syncFullDataAndPush(player, data);
    }

    /**
     * 浠呯綉缁滃悓姝ワ紝涓嶆寔涔呭寲锛堢敤浜庨珮棰?tick sync锛夈€?
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
     * 灏?ArcQuestPlayer 蹇収鍐欏叆鐙珛 SavedData 瀹夸富銆?
     */
    public static void persistSnapshot(ServerPlayer player, ArcQuestPlayer data) {
        ArcQuestPlayerManager.persistSnapshot(player, data);
    }
}
