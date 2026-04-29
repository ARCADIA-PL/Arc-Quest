package org.arcadia.arc_quest.quest.network;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.capability.IQuestCapability;
import org.arcadia.arc_quest.quest.capability.QuestCapabilityImpl;
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

    public static void syncFlagsVarsAndPush(ServerPlayer player, IQuestCapability cap) {
        ArcQuestNetwork.syncFlagsAndVars(player, cap);
    }

    public static void syncFullDataAndPush(ServerPlayer player, IQuestCapability cap) {
        ArcQuestNetwork.syncFullData(player, cap);
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
     * 统一语义入口：有变更才执行“快照持久化 + 客户端同步 + 清脏”。
     *
     * @return true 如果执行了持久化与同步
     */
    public static void persistAndSyncIfChanged(ServerPlayer player, IQuestCapability cap) {
        if (!(cap instanceof QuestCapabilityImpl impl) || !impl.isDirty()) {
            return;
        }

        persistSnapshot(player, impl);
        syncFullDataAndPush(player, impl);
        impl.clearDirty();

        LOGGER.debug("[QuestPersist] Player {} snapshot persisted and synced (dirty cleared)",
                player.getGameProfile().getName());
    }

    /**
     * 将能力快照写入玩家 PersistentData（运行时快照，不等同于立即磁盘落盘）。
     */
    public static void persistSnapshot(ServerPlayer player, QuestCapabilityImpl impl) {
        player.getPersistentData().put("ArcQuestAutosave", impl.serializeNBT().copy());
    }
}
