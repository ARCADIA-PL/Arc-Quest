package org.arcadia.arc_quest.quest.api.migration;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.logic.CollectionRunAccess;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;
import org.arcadia.arc_quest.questplayer.*;

/** Pause/resume the same run, never accept it again or replay its enter/completion actions. */
public final class QuestLifecycleService {
    public static final QuestLifecycleService INSTANCE = new QuestLifecycleService();
    public enum Status { ACTIVE, PAUSED, COMPLETED, FAILED, ABSENT }
    public enum Result { CHANGED, ALREADY_ACTIVE, TERMINAL, ABSENT, DEFINITION_UNAVAILABLE }
    private QuestLifecycleService() {}
    public Status status(ArcQuestPlayer data, String quest) {
        if (data.isQuestCompleted(quest)) return Status.COMPLETED;
        if (data.isQuestFailed(quest)) return Status.FAILED;
        var active = data.getActiveQuest(quest);
        if (active != null) {
            if (active.getState() == QuestState.COMPLETED) return Status.COMPLETED;
            if (active.getState() == QuestState.FAILED) return Status.FAILED;
            return Status.ACTIVE;
        }
        var archived = pausedRun(data,quest);
        if (archived == null) return Status.ABSENT;
        return switch (archived.getState()) { case ACTIVE -> Status.PAUSED; case COMPLETED -> Status.COMPLETED; case FAILED -> Status.FAILED; default -> Status.ABSENT; };
    }
    public Result pause(ServerPlayer player, String quest, String reason) {
        requireThread(player); var data = ArcQuestPlayerManager.getOrCreate(player);
        var status = status(data, quest);
        if (status == Status.COMPLETED || status == Status.FAILED) return Result.TERMINAL;
        var run = data.getActiveQuest(quest);
        if (run == null) return Result.ABSENT;
        if(run.hasCollectionData())data.getCollectionArchives().capture(run);
        data.getRuntimeManagement().pauseRun(quest, run, reason == null ? "" : reason);
        org.arcadia.arc_quest.quest.logic.QuestMarkerService.clearQuestMarkers(data, quest);
        org.arcadia.arc_quest.questmarker.runtime.QuestMarkerRuntimeManager.clearQuest(player.getUUID(), quest);
        data.removeActiveQuest(quest); finish(player, data); return Result.CHANGED;
    }
    public Result resume(ServerPlayer player, String quest) {
        requireThread(player); var data = ArcQuestPlayerManager.getOrCreate(player);
        var status = status(data, quest);
        if (status == Status.ACTIVE) return Result.ALREADY_ACTIVE;
        if (status == Status.COMPLETED || status == Status.FAILED) return Result.TERMINAL;
        var run = pausedRun(data,quest);
        if (run == null || status != Status.PAUSED) return Result.ABSENT;
        if (run.hasCollectionData()?CollectionRunAccess.resolve(player.server, run) == null:
                org.arcadia.arc_quest.quest.registry.QuestRegistry.getServerDefinition(net.minecraft.resources.ResourceLocation.parse(quest))==null) return Result.DEFINITION_UNAVAILABLE;
        data.addActiveQuest(run.copy()); data.getRuntimeManagement().resumed(quest); finish(player, data); return Result.CHANGED;
    }
    private static org.arcadia.arc_quest.quest.data.QuestRuntimeData pausedRun(ArcQuestPlayer data,String quest) {
        var stored=data.getRuntimeManagement().pausedRun(quest);
        // Collection migrations update the archive; it remains authoritative for these runs.
        return stored!=null&&!stored.hasCollectionData()?stored:data.getCollectionArchives().get(quest);
    }
    private static void requireThread(ServerPlayer player) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Lifecycle commands require server thread");
    }
    private static void finish(ServerPlayer player, ArcQuestPlayer data) {
        QuestProgressHandler.rebuildTrackingIndex(player, data);
        ArcQuestPlayerManager.persistSnapshotSynchronously(player, data);
        QuestSyncCoordinator.syncFullDataAndPush(player, data);
    }
}
