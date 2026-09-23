package org.arcadia.arc_quest.quest.logic;

import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import org.arcadia.arc_quest.api.event.quest.QuestAbandonedEvent;
import org.arcadia.arc_quest.api.event.quest.QuestCompletedEvent;
import org.arcadia.arc_quest.api.event.quest.QuestFailedEvent;
import org.arcadia.arc_quest.quest.api.IReward;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.event.QuestChangeEvent;
import org.arcadia.arc_quest.quest.event.QuestEventBus;
import org.arcadia.arc_quest.quest.network.QuestRejectCodeDictionary;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.service.TrackedQuestService;
import org.arcadia.arc_quest.quest.tracking.ObjectiveTracker;
import org.arcadia.arc_quest.questmarker.runtime.QuestMarkerRuntimeManager;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import static org.arcadia.arc_quest.quest.logic.QuestProgressEffects.playChapterSound;
import static org.arcadia.arc_quest.quest.network.QuestSyncCoordinator.syncFlagsVarsAndPush;
import static org.arcadia.arc_quest.quest.network.QuestSyncCoordinator.syncFullDataAndPush;
import static org.arcadia.arc_quest.quest.network.QuestSyncCoordinator.syncQuestStateAndPush;

/**
 * 任务完成、失败和放弃的收尾流程；保持奖励、状态、同步及事件的既有顺序。
 */
final class QuestLifecycleService {
    @FunctionalInterface
    interface RewardGrant {
        void grant(ServerPlayer player, List<IReward> rewards, String context);
    }

    private final RewardGrant rewards;

    QuestLifecycleService(RewardGrant rewards) {
        this.rewards = Objects.requireNonNull(rewards);
    }

    void completeQuest(ServerPlayer player,
            ArcQuestPlayer data,
            QuestRuntimeData qdata,
            QuestDefinition def) {
        doCompleteQuest(player, data, qdata, def, "completed");
    }

    void doCompleteQuest(ServerPlayer player,
            ArcQuestPlayer data,
            QuestRuntimeData qdata,
            QuestDefinition def,
            String logPrefix) {
        String questId = qdata.getQuestId();

        rewards.grant(player, def.getCompletionRewards(), "completion");
        def.getFlagsToSetOnComplete().forEach(data::setFlag);

        qdata.setState(QuestState.COMPLETED);
        data.markCompleted(questId);

        ObjectiveTracker.INSTANCE.unregisterQuest(player.getUUID(), questId);
        QuestMarkerService.clearQuestMarkers(data, questId);
        QuestMarkerRuntimeManager.clearQuest(player.getUUID(), questId);

        ArcQuestLog.info(ArcQuestLog.Category.QUEST_PROGRESS, "Player {} {} quest: {}",
                player.getGameProfile().getName(), logPrefix, questId);

        syncQuestStateAndPush(player, qdata);
        TrackedQuestService.onQuestTerminated(player);
        syncFlagsVarsAndPush(player, data);
        QuestEventBus.fire(QuestChangeEvent.questCompleted(ResourceLocation.parse(questId)));
        MinecraftForge.EVENT_BUS.post(new QuestCompletedEvent(player, ResourceLocation.parse(questId)));
        playChapterSound(player, def.getChapterCompleteSound());
    }

    void failQuest(ServerPlayer player, String questId) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        QuestRuntimeData qdata = data.getActiveQuest(questId);

        qdata.setState(QuestState.FAILED);
        data.markFailed(questId);
        ObjectiveTracker.INSTANCE.unregisterQuest(player.getUUID(), questId);
        QuestMarkerService.clearQuestMarkers(data, questId);
        QuestMarkerRuntimeManager.clearQuest(player.getUUID(), questId);

        syncQuestStateAndPush(player, qdata);
        TrackedQuestService.onQuestTerminated(player);
        QuestEventBus.fire(QuestChangeEvent.questFailed(ResourceLocation.parse(questId)));
        MinecraftForge.EVENT_BUS.post(new QuestFailedEvent(player, ResourceLocation.parse(questId)));
        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def != null) playChapterSound(player, def.getChapterFailSound());
    }

    QuestRejectCodeDictionary.Code abandonQuestWithCode(ServerPlayer player, String questId) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null || !data.isQuestActive(questId)) {
            return QuestRejectCodeDictionary.Code.NOT_ACTIVE;
        }
        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) {
            return QuestRejectCodeDictionary.Code.QUEST_NOT_FOUND;
        }
        if (!def.isAbandonAllowed()) {
            return QuestRejectCodeDictionary.Code.ABANDON_NOT_ALLOWED;
        }
        QuestRuntimeData qdata = data.getActiveQuest(questId);
        qdata.setState(QuestState.FAILED);

        data.markFailed(questId);
        ObjectiveTracker.INSTANCE.unregisterQuest(player.getUUID(), questId);
        QuestMarkerService.clearQuestMarkers(data, questId);
        QuestMarkerRuntimeManager.clearQuest(player.getUUID(), questId);

        syncFullDataAndPush(player, data);
        TrackedQuestService.onQuestTerminated(player);
        QuestEventBus.fire(QuestChangeEvent.questFailed(ResourceLocation.parse(questId)));
        MinecraftForge.EVENT_BUS.post(new QuestFailedEvent(player, ResourceLocation.parse(questId)));
        MinecraftForge.EVENT_BUS.post(new QuestAbandonedEvent(player, ResourceLocation.parse(questId)));
        playChapterSound(player, def.getChapterFailSound());
        return QuestRejectCodeDictionary.Code.OK;
    }
}
