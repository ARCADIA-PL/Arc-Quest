package org.arcadia.arc_quest.questplayer.restore;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.service.TrackedQuestService;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingChangeReason;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerLifecycleHandler;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.questplayer.migration.ArcQuestPlayerMigrationBundle;
import org.arcadia.arc_quest.questplayer.migration.ArcQuestPlayerMigrationMode;
import org.arcadia.arc_quest.questplayer.snapshot.ArcQuestPlayerSnapshotRef;
import org.arcadia.arc_quest.questplayer.snapshot.ArcQuestSnapshotReason;
import org.arcadia.arc_quest.questplayer.snapshot.FileArcQuestPlayerSnapshotStore;
import org.arcadia.arc_quest.guide.runtime.GuidePlayerStateSyncService;
import org.arcadia.arc_quest.util.log.ArcQuestLog;
import org.arcadia.arc_quest.trade.gacha.network.PendingDrawManager;
import org.arcadia.arc_quest.trade.gacha.runtime.GachaScreenOpener;
import org.arcadia.arc_quest.dialogue.runtime.DialogueSessionManager;
import org.arcadia.arc_quest.questplayer.interaction.PlayerInteractionGuard;
import org.arcadia.arc_quest.trade.network.C2SRequestTradePacket;
import org.arcadia.arc_quest.sync.RequestIdempotencyStore;
import org.arcadia.arc_quest.quest.network.QuestSyncRevisionManager;

import java.util.ArrayList;
import java.util.Set;

public final class ArcQuestPlayerRestoreService {

    private final ArcQuestPlayerMigrationValidator validator = new ArcQuestPlayerMigrationValidator();
    private final ArcQuestPlayerMigrationImporter importer = new ArcQuestPlayerMigrationImporter();

    public ArcQuestPlayerMigrationApplyResult restore(ServerPlayer player,
                                                      ArcQuestPlayerMigrationBundle bundle,
                                                      Set<String> sections) {
        if (!player.server.isSameThread()) {
            throw new IllegalStateException("Player restore must run on the server thread");
        }
        try (var scope = PlayerInteractionGuard.INSTANCE.restore(player.getUUID())) {
            if (scope == null) {
                return failure(new ArcQuestPlayerMigrationReport(false, java.util.List.of()), null,
                        "player_interaction_busy", new IllegalStateException("Player interaction is still executing"), player);
            }
            return restoreExclusive(player, bundle, sections);
        }
    }

    private ArcQuestPlayerMigrationApplyResult restoreExclusive(ServerPlayer player,
            ArcQuestPlayerMigrationBundle bundle, Set<String> sections) {
        ArcQuestPlayerMigrationReport report = validator.validate(player, bundle, sections);
        if (report.isBlocking()) {
            return new ArcQuestPlayerMigrationApplyResult(false, report, null);
        }

        ArcQuestPlayer targetData = ArcQuestPlayerManager.getOrCreate(player);
        try {
            PendingDrawManager.settleBeforeRestore(player);
            if (PendingDrawManager.hasPendingDraw(player.getUUID())) {
                throw new IllegalStateException("Cannot restore player data while a draw is being processed");
            }
        } catch (RuntimeException failure) {
            return failure(report, null, "pending_draw_unresolved", failure, player);
        }
        ArcQuestPlayer candidate;
        try {
            candidate = prepareCandidate(player, targetData, bundle, sections);
        } catch (RuntimeException failure) {
            return failure(report, null, "invalid_player_data", failure, player);
        }
        ArcQuestPlayerSnapshotRef preRestoreSnapshot;
        try {
            preRestoreSnapshot = FileArcQuestPlayerSnapshotStore.INSTANCE
                    .writeSnapshot(player, targetData, ArcQuestSnapshotReason.PRE_RESTORE);
        } catch (RuntimeException failure) {
            return failure(report, null, "pre_restore_backup_failed", failure, player);
        }

        try {
            DialogueSessionManager.INSTANCE.closeForPlayerRestore(player);
            C2SRequestTradePacket.invalidateScreenState(player.getUUID());
            GachaScreenOpener.clearPlayer(player.getUUID());
            // 结束事件可能更新未被恢复的领域，必须以清理后的真实状态构建候选。
            candidate = prepareCandidate(player, targetData, bundle, sections);
        } catch (RuntimeException failure) {
            return failure(report, preRestoreSnapshot, "interaction_cleanup_failed", failure, player);
        }

        try {
            PlayerRestoreCommit.apply(targetData, candidate, data -> ArcQuestPlayerManager.persistSnapshot(player, data));
        } catch (RuntimeException failure) {
            return failure(report, preRestoreSnapshot, "restore_persistence_failed", failure, player);
        }
        var issues = new ArrayList<>(report.getIssues());
        afterCommit(player, issues, "renew_interaction_session", () -> {
            ArcQuestPlayerManager.advanceSessionEpoch(player);
            C2SRequestTradePacket.clearPlayer(player.getUUID());
            QuestSyncRevisionManager.clearPlayer(player.getUUID());
            RequestIdempotencyStore.INSTANCE.clearPlayer(player.getUUID());
        });
        afterCommit(player, issues, "rebuild_tracking", () -> {
            QuestProgressHandler.rebuildTrackingIndex(player, targetData);
            TrackedQuestService.reconcile(player, QuestTrackingChangeReason.RECONCILE);
        });
        afterCommit(player, issues, "sync_player", () -> ArcQuestNetwork.syncFullData(player, targetData));
        afterCommit(player, issues, "sync_guides", () -> GuidePlayerStateSyncService.sync(player, targetData));

        return new ArcQuestPlayerMigrationApplyResult(true,
                new ArcQuestPlayerMigrationReport(false, issues), preRestoreSnapshot);
    }

    private ArcQuestPlayer prepareCandidate(ServerPlayer player, ArcQuestPlayer target,
            ArcQuestPlayerMigrationBundle bundle, Set<String> sections) {
        ArcQuestPlayer candidate = new ArcQuestPlayer(player.getUUID());
        candidate.copyFrom(target);
        importer.apply(candidate, bundle, ArcQuestPlayerMigrationMode.REPLACE_ALL, sections);
        ArcQuestPlayerLifecycleHandler.validateAndFixQuestData(player, candidate);
        // 对话标记依赖运行期会话，旧快照不能恢复一个已关闭会话的标记。
        for (String marker : java.util.List.copyOf(candidate.getAllMarkers().keySet())) {
            if (marker.startsWith("aq:dlg:")) candidate.removeMarker(marker);
        }
        return candidate;
    }

    private static void afterCommit(ServerPlayer player, java.util.List<ArcQuestPlayerMigrationIssue> issues,
                                    String stage, Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException failure) {
            ArcQuestLog.error(ArcQuestLog.Category.PERSISTENCE,
                    "Player data restored but follow-up failed: player={}, stage={}", player.getUUID(), stage, failure);
            issues.add(new ArcQuestPlayerMigrationIssue(ArcQuestPlayerMigrationSeverity.WARNING,
                    "restore_" + stage + "_failed", "进度已经恢复，但后续处理失败，请检查服务端日志并重新连接",
                    "restore", ""));
        }
    }

    private static ArcQuestPlayerMigrationApplyResult failure(ArcQuestPlayerMigrationReport report,
            ArcQuestPlayerSnapshotRef backup, String code, RuntimeException failure, ServerPlayer player) {
        ArcQuestLog.error(ArcQuestLog.Category.PERSISTENCE,
                "Player restore failed: player={}, stage={}, backup={}", player.getUUID(), code, backup, failure);
        var issues = new ArrayList<>(report.getIssues());
        issues.add(new ArcQuestPlayerMigrationIssue(ArcQuestPlayerMigrationSeverity.ERROR, code,
                failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(), "restore", ""));
        if ("restore_persistence_failed".equals(code) && failure.getSuppressed().length > 0) {
            issues.add(new ArcQuestPlayerMigrationIssue(ArcQuestPlayerMigrationSeverity.ERROR,
                    "restore_rollback_failed", "存储回滚失败，请保留 PRE_RESTORE 快照并检查服务端日志", "restore", ""));
        }
        return new ArcQuestPlayerMigrationApplyResult(false, new ArcQuestPlayerMigrationReport(true, issues), backup);
    }
}
