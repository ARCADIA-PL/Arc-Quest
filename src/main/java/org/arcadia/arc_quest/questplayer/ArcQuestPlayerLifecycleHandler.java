package org.arcadia.arc_quest.questplayer;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.dialogue.data.DialogueNpcStateManager;
import org.arcadia.arc_quest.dialogue.runtime.DialogueSessionManager;
import org.arcadia.arc_quest.data.reload.ArcQuestReloadCoordinator;
import org.arcadia.arc_quest.npc.runtime.NpcInteractionLeaseManager;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.C2SMarkPhaseStoryReadPacket;
import org.arcadia.arc_quest.quest.network.C2SSetTrackedQuestPacket;
import org.arcadia.arc_quest.quest.network.C2SRequestQuestResyncPacket;
import org.arcadia.arc_quest.quest.network.QuestSyncRevisionManager;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questmarker.runtime.QuestMarkerReconciliationService;
import org.arcadia.arc_quest.questmarker.runtime.QuestMarkerRuntimeManager;
import org.arcadia.arc_quest.questplayer.snapshot.ArcQuestSnapshotReason;
import org.arcadia.arc_quest.questplayer.snapshot.FileArcQuestPlayerSnapshotStore;
import org.arcadia.arc_quest.sync.RequestIdempotencyStore;
import org.arcadia.arc_quest.trade.network.C2SRequestTradePacket;
import org.arcadia.arc_quest.trade.gacha.network.PendingDrawManager;
import org.arcadia.arc_quest.guide.runtime.GuidePlayerStateSyncService;
import org.slf4j.Logger;

import java.util.ArrayList;

@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArcQuestPlayerLifecycleHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ArcQuestPlayerLifecycleHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        PlayerSessionEpochManager.beginSession(sp);
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(sp);
        validateAndFixQuestData(sp, data);
        QuestProgressHandler.rebuildTrackingIndex(sp, data);
        QuestMarkerReconciliationService.reconcileContinuousQuestMarkers(sp, data, true);
        ArcQuestNetwork.syncFullData(sp, data);
        ArcQuestNetwork.sendDatapackReloadEpoch(sp, ArcQuestReloadCoordinator.INSTANCE.getCommittedEpoch());
        GuidePlayerStateSyncService.sync(sp, data);
        PendingDrawManager.compensateAndGrant(sp);
        LOGGER.debug("[ArcQuest] Login sync complete for: {}", sp.getGameProfile().getName());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer to)) return;
        if (!(event.getOriginal() instanceof ServerPlayer from)) return;
        from.reviveCaps();
        try {
            ArcQuestPlayerManager.clone(from, to);
        } finally {
            from.invalidateCaps();
        }
        LOGGER.debug("[ArcQuest] Quest data cloned for player: {}", to.getName().getString());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        DialogueSessionManager.INSTANCE.onPlayerLogout(sp);
        ArcQuestPlayer data = ArcQuestPlayerManager.get(sp);
        if (data == null) return;
        QuestProgressHandler.rebuildTrackingIndex(sp, data);
        QuestMarkerReconciliationService.reconcileContinuousQuestMarkers(sp, data, true);
        ArcQuestNetwork.syncFullData(sp, data);
        GuidePlayerStateSyncService.sync(sp, data);
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        DialogueSessionManager.INSTANCE.onPlayerLogout(sp);
        ArcQuestPlayer data = ArcQuestPlayerManager.get(sp);
        if (data == null) return;
        QuestProgressHandler.rebuildTrackingIndex(sp, data);
        ArcQuestNetwork.syncFullData(sp, data);
        GuidePlayerStateSyncService.sync(sp, data);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        DialogueSessionManager.INSTANCE.onPlayerLogout(sp);
        ArcQuestPlayer data = ArcQuestPlayerManager.get(sp);
        if (data != null) {
            writeRecoverySnapshot(sp, data, ArcQuestSnapshotReason.PLAYER_LOGOUT);
        }
        ArcQuestPlayerManager.persistAndUnload(sp);
        RequestIdempotencyStore.INSTANCE.clearPlayer(sp.getUUID());
        C2SRequestTradePacket.clearPlayer(sp.getUUID());
        C2SRequestQuestResyncPacket.clearPlayer(sp.getUUID());
        C2SMarkPhaseStoryReadPacket.clearPlayer(sp.getUUID());
        C2SSetTrackedQuestPacket.clearPlayer(sp.getUUID());
        QuestSyncRevisionManager.clearPlayer(sp.getUUID());
        PlayerSessionEpochManager.endSession(sp.getUUID());
    }

    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel serverLevel))
            return;
        for (ServerPlayer player : serverLevel.players()) {
            DialogueSessionManager.INSTANCE.onPlayerLogout(player);
            ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
            if (data != null) {
                ArcQuestPlayerManager.persistAndUnload(player);
                RequestIdempotencyStore.INSTANCE.clearPlayer(player.getUUID());
                C2SRequestTradePacket.clearPlayer(player.getUUID());
                C2SRequestQuestResyncPacket.clearPlayer(player.getUUID());
                C2SMarkPhaseStoryReadPacket.clearPlayer(player.getUUID());
                C2SSetTrackedQuestPacket.clearPlayer(player.getUUID());
                QuestSyncRevisionManager.clearPlayer(player.getUUID());
                PlayerSessionEpochManager.endSession(player.getUUID());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DialogueSessionManager.INSTANCE.onPlayerLogout(player);
            QuestMarkerRuntimeManager.clearPlayer(player.getUUID());
            ArcQuestNetwork.clearPlayerMarkerState(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % 20 == 0) {
            for (var expiredLease : NpcInteractionLeaseManager.INSTANCE.tick(event.getServer().getTickCount())) {
                ServerPlayer player = event.getServer().getPlayerList()
                        .getPlayer(expiredLease.owner().playerUuid());
                if (player == null) continue;
                var session = DialogueSessionManager.INSTANCE.getSession(player);
                if (session != null && expiredLease.leaseId().equals(session.getNpcLeaseId())) {
                    DialogueSessionManager.INSTANCE.endDialogue(player);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            ArcQuestPlayerManager.persistAndUnload(player);
        }
        ArcQuestPlayerManager.flushCheckpoints();
        DialogueSessionManager.INSTANCE.shutdown();
        DialogueNpcStateManager.clearAll();
        RequestIdempotencyStore.INSTANCE.clear();
        C2SRequestTradePacket.clearAll();
        C2SRequestQuestResyncPacket.clear();
        C2SMarkPhaseStoryReadPacket.clear();
        C2SSetTrackedQuestPacket.clear();
        QuestSyncRevisionManager.clear();
        PlayerSessionEpochManager.clear();
    }

    @SubscribeEvent
    public static void onWorldSave(LevelEvent.Save event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel serverLevel))
            return;
        for (ServerPlayer player : serverLevel.players()) {
            ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
            if (data != null && data.isDirty()) {
                QuestSyncCoordinator.persistAndSyncIfChanged(player, data);
                writeRecoverySnapshot(player, data, ArcQuestSnapshotReason.WORLD_SAVE);
            }
        }
    }

    private static void writeRecoverySnapshot(ServerPlayer player, ArcQuestPlayer data, ArcQuestSnapshotReason reason) {
        try {
            FileArcQuestPlayerSnapshotStore.INSTANCE.writeSnapshot(player, data, reason);
        } catch (Exception e) {
            LOGGER.warn("[ArcQuest] Failed to write recovery snapshot for player {} ({})",
                    player.getGameProfile().getName(), reason, e);
        }
    }

    public static void validateAndFixQuestData(ServerPlayer player, ArcQuestPlayer data) {
        var activeQuests = data.getAllActiveQuests();
        if (activeQuests.isEmpty()) return;

        boolean needsSync = false;

        for (var entry : activeQuests.entrySet()) {
            String questId = entry.getKey();
            QuestRuntimeData qdata = entry.getValue();
            ResourceLocation rl = ResourceLocation.tryParse(questId);

            if (rl == null) continue;

            QuestDefinition def = QuestRegistry.get(rl);
            if (def == null) {
                LOGGER.warn("[ArcQuest] Quest '{}' no longer exists in registry. Marking as failed for player: {}",
                        questId, player.getName().getString());
                qdata.setState(QuestState.FAILED);
                needsSync = true;
                continue;
            }

            var activeIds = new ArrayList<>(qdata.getActivePhaseIds());
            for (String phaseId : activeIds) {
                if (!def.getPhaseIds().contains(phaseId)) {
                    LOGGER.warn("[ArcQuest] Phase '{}' not found in quest '{}'. Removing for player: {}",
                            phaseId, questId, player.getName().getString());
                    qdata.completePhase(phaseId);
                    needsSync = true;
                }
            }

            if (qdata.getState() == QuestState.ACTIVE && qdata.getActivePhaseIds().isEmpty()) {
                PhaseDefinition init = def.getInitialPhase();
                if (init != null) {
                    qdata.activatePhase(init.getPhaseId(), init.getObjectives().size());
                    needsSync = true;
                }
            }
        }

        if (needsSync) {
            LOGGER.info("[ArcQuest] Fixed quest data inconsistencies for player: {}", player.getName().getString());
        }
    }
}
