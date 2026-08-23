package org.arcadia.arc_quest.quest.tracking.application;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import org.arcadia.arc_quest.api.event.quest.TrackedQuestChangedEvent;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;
import org.arcadia.arc_quest.quest.service.QuestTrackingPriority;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingChangeReason;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingFacade;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingResult;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingSnapshot;
import org.arcadia.arc_quest.quest.tracking.domain.QuestTrackingAction;
import org.arcadia.arc_quest.quest.tracking.domain.QuestTrackingStateMachine;
import org.arcadia.arc_quest.quest.tracking.domain.QuestTrackingTransition;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.questmarker.runtime.QuestMarkerReconciliationService;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class QuestTrackingManager implements QuestTrackingFacade {

    public static final QuestTrackingManager INSTANCE = new QuestTrackingManager();

    private final QuestTrackingStateMachine stateMachine = new QuestTrackingStateMachine();

    private QuestTrackingManager() {
    }

    @Override
    public QuestTrackingResult track(ServerPlayer player, String questId) {
        String normalized = normalize(questId);
        if (normalized == null || ResourceLocation.tryParse(normalized) == null) {
            return rejected(player, QuestTrackingChangeReason.USER_TRACK,
                    QuestTrackingResult.Rejection.INVALID_QUEST_ID);
        }
        return apply(player, QuestTrackingAction.userTrack(normalized));
    }

    @Override
    public QuestTrackingResult untrack(ServerPlayer player) {
        return apply(player, QuestTrackingAction.userUntrack());
    }

    @Override
    public QuestTrackingResult onQuestAccepted(ServerPlayer player, String questId) {
        return apply(player, QuestTrackingAction.autoSelect(normalize(questId), true, false,
                QuestTrackingChangeReason.AUTO_ON_ACCEPT));
    }

    @Override
    public QuestTrackingResult onQuestTerminated(ServerPlayer player) {
        return apply(player, QuestTrackingAction.autoSelect(null, false, true,
                QuestTrackingChangeReason.AUTO_ON_TERMINATED));
    }

    @Override
    public QuestTrackingResult onQuestReset(ServerPlayer player) {
        return apply(player, QuestTrackingAction.autoSelect(null, false, true,
                QuestTrackingChangeReason.AUTO_ON_RESET));
    }

    @Override
    public QuestTrackingResult reconcile(ServerPlayer player, QuestTrackingChangeReason reason) {
        return apply(player, QuestTrackingAction.reconcile(reason));
    }

    @Override
    public QuestTrackingSnapshot snapshot(ServerPlayer player) {
        return ArcQuestPlayerManager.getOrCreate(player).getQuestTrackingSnapshot();
    }

    private QuestTrackingResult apply(ServerPlayer player, QuestTrackingAction action) {
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        QuestTrackingSnapshot before = data.getQuestTrackingSnapshot();
        List<String> activeQuestIds = activeQuestIds(data);
        QuestTrackingTransition transition = stateMachine.transition(
                before, action, activeQuestIds, orderedCandidates(data));
        if (!transition.accepted()) {
            return new QuestTrackingResult(false, false, before, before,
                    action.reason(), transition.rejection());
        }

        QuestTrackingSnapshot target = transition.target();
        boolean changed = !Objects.equals(before.questId(), target.questId())
                || before.state() != target.state();
        if (!changed) {
            return new QuestTrackingResult(true, false, before, before,
                    action.reason(), null);
        }

        data.applyQuestTracking(target.questId(), target.state(), action.reason());
        QuestTrackingSnapshot after = data.getQuestTrackingSnapshot();
        if (!Objects.equals(before.questId(), after.questId())) {
            MinecraftForge.EVENT_BUS.post(new TrackedQuestChangedEvent(
                    player.serverLevel(), player, before.questId(), after.questId(),
                    action.reason(), after.revision(), true));
        }
        QuestMarkerReconciliationService.reconcileTrackingPhaseMarkers(player, data, true);
        QuestSyncCoordinator.persistAndSyncIfChanged(player, data);
        return new QuestTrackingResult(true, true, before, after,
                action.reason(), null);
    }

    private QuestTrackingResult rejected(ServerPlayer player,
                                         QuestTrackingChangeReason reason,
                                         QuestTrackingResult.Rejection rejection) {
        QuestTrackingSnapshot snapshot = snapshot(player);
        return new QuestTrackingResult(false, false, snapshot, snapshot,
                reason, rejection);
    }

    private List<String> orderedCandidates(ArcQuestPlayer data) {
        return data.getAllActiveQuests().values().stream()
                .filter(runtime -> runtime.getState() == QuestState.ACTIVE)
                .filter(runtime -> {
                    ResourceLocation questId = ResourceLocation.tryParse(runtime.getQuestId());
                    var definition = questId != null ? QuestRegistry.get(questId) : null;
                    return definition != null && definition.canBeAutoTrack();
                })
                .map(runtime -> QuestTrackingPriority.resolve(
                        runtime.getQuestId(), runtime.getAcceptedAtTick()))
                .sorted()
                .map(QuestTrackingPriority::questId)
                .toList();
    }

    private List<String> activeQuestIds(ArcQuestPlayer data) {
        return data.getAllActiveQuests().values().stream()
                .filter(runtime -> runtime.getState() == QuestState.ACTIVE)
                .map(runtime -> runtime.getQuestId())
                .toList();
    }

    @Nullable
    private String normalize(@Nullable String questId) {
        return questId == null || questId.isBlank() ? null : questId.trim();
    }
}
